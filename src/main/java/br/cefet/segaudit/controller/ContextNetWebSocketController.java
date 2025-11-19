package br.cefet.segaudit.controller;

import br.cefet.segaudit.model.interfaces.IModelManagaer;
import br.cefet.segaudit.model.classes.WebSocketSessionState;
import br.cefet.segaudit.model.classes.ContextNetConfig;
import br.cefet.segaudit.model.factories.ContextNetClientFactory;
import br.cefet.segaudit.service.AIService;
import br.cefet.segaudit.service.ContextNetClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ContextNetWebSocketController extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(ContextNetWebSocketController.class);

    private final ContextNetClientFactory contextNetClientFactory;

    private final Map<String, WebSocketSessionState> sessions = new ConcurrentHashMap<>();
    private final IModelManagaer modelManagaer;
    private final ObjectMapper objectMapper;

    /** Initializes the controller with required factories and managers for handling WebSocket connections. */
    public ContextNetWebSocketController(ContextNetClientFactory factory, IModelManagaer modelManagaer, ObjectMapper objectMapper) {
        this.contextNetClientFactory = factory;
        this.modelManagaer = modelManagaer;
        this.objectMapper = objectMapper;
    }

    //? ----------- Methods -----------
    /** Invoked after a new WebSocket connection is established, preparing it to receive messages. */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket session opened: {}", session.getId());
        sessions.put(session.getId(), new WebSocketSessionState());
    }

    /** Handles incoming text messages, routing them to initialize the session or process subsequent commands. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String sessionId = session.getId();
        WebSocketSessionState state = sessions.get(sessionId);
        
        try {
            if (state == null) {
                throw new IllegalStateException("Session state not found.");
            }

            // Se a sessão não foi inicializada e não está em processo de inicialização, trata como a primeira mensagem.
            if (!state.isInitialized() && !state.isInitializing()) {
                handleFirstMessage(session, payload);
            // Se já foi inicializada, trata como mensagem subsequente.
            } else {
                handleSubsequentMessages(session, payload);
            }
        } catch (Exception e) {
            logger.error("Error handling message for session {}", sessionId, e);
            sendToSession(session, "Server error: " + e.getMessage());
        }
    }

    /** Handles the first message from a client, which contains configuration to initialize the ContextNet client and AI session.
    //?First Message is a JSON with connection configs*/
    private void handleFirstMessage(WebSocketSession session, String payload) throws Exception {
        String sessionId = session.getId();
        logger.info("[{}] Handling first message. Payload: {}", sessionId, payload);
        WebSocketSessionState state = sessions.get(sessionId);

        // Marca que a inicialização começou para bloquear outras mensagens.
        state.setInitializing(true);

        ContextNetConfig config = objectMapper.readValue(payload, ContextNetConfig.class);
        logger.debug("[{}] Parsed ContextNetConfig: MyUUID={}, DestinationUUID={}", sessionId, config.myUUID, config.destinationUUID);

        ContextNetClient client = contextNetClientFactory.create(config, (msg) -> {
            sendToSession(session, msg);
        });
        state.setContextNetClient(client);
        logger.info("[{}] ContextNetClient created and stored.", sessionId);
        
        //? Busca os planos do agente de forma assíncrona.
        logger.info("[{}] Fetching agent plans from ContextNet...", sessionId);
        client.fetchAgentPlans()
            .thenAccept(agentPlans -> {

                logger.info("[{}] Successfully received plans from agent: {}", sessionId, agentPlans);
                logger.info("[{}] Contexto do agente recuperado com sucesso.", sessionId);
    
                try {
                    logger.info("[{}] Initializing AI Service...", sessionId);
                    AIService aiService = new AIService(this.modelManagaer, sessionId, agentPlans);
                    state.setAiService(aiService);
                    state.setInitializing(false); // Libera o bloqueio de inicialização.
                    state.setInitialized(true); // Marca a sessão como totalmente inicializada.
                    logger.info("[{}] AI Service initialized and stored.", sessionId);
        
                    sendToSession(session, "Connection stabilized and IA session ready.");
                } catch (Exception e) {
                    state.setInitializing(false); // Garante a liberação do bloqueio em caso de erro.

                    logger.error("[{}] Failed to initialize AI service after getting plans.", sessionId, e);
                    sendToSession(session, "Error: AI model initialization failed. " + e.getMessage());
                
                }
            })
            .exceptionally(ex -> {   
                state.setInitializing(false); // Libera o bloqueio em caso de erro.
                logger.error("[{}] Failed to get plans from agent. Reason: {}", sessionId, ex.getMessage(), ex);
                sendToSession(session, "Error: Could not get plans from agent. The agent did not respond.");
                
                return null;
            });
    }

    /** Handles all messages after the initial setup, translating user input into KQML commands and sending them to the ContextNet. */
    private void handleSubsequentMessages(WebSocketSession session, String payload) {
        String sessionId = session.getId();
        WebSocketSessionState state = sessions.get(sessionId);

        if (state == null || !state.isInitialized() || state.isInitializing()) {
             throw new IllegalStateException("Session is not ready or has been closed. Please wait for 'Connection stabilized' message.");
        }
        AIService aiService = state.getAiService();

        logger.info("[{}] Handling subsequent message: {}", sessionId, payload);

        List<String> kqmlMessages = aiService.getKQMLMessages(sessionId, payload);
        logger.info("[{}] AI translated message to {} KQML command(s): {}", sessionId, kqmlMessages.size(), kqmlMessages);

        ContextNetClient client = state.getContextNetClient();

        // Envia os comandos com um atraso para evitar condição de corrida no agente.
        for (int i = 0; i < kqmlMessages.size(); i++) {
            String kqmlMessage = kqmlMessages.get(i);
            String fullKqmlMessage = String.format("achieve,%s,%s", client.getDestinationUUID(), kqmlMessage);
            client.sendToContextNet(fullKqmlMessage);
            logger.debug("[{}] Sent to ContextNet: {}", sessionId, fullKqmlMessage);

            // Adiciona um atraso de 1 segundo, exceto após o último comando.
            if (i < kqmlMessages.size() - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.error("Thread interrupted while waiting to send next command", e);
                }
            }
        }
    }

    /** Invoked when a WebSocket connection is closed, performing cleanup by removing the client and terminating the AI model session. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        sessions.remove(sessionId);
        modelManagaer.endSession(sessionId);
        logger.info("WebSocket and AI sessions closed for id: {}", sessionId);
    }

    //? ----------- Helpers -----------

    /** Sends a string message to a specific WebSocket session if it is open. */
    private void sendToSession(WebSocketSession session, String msg) {
        try { 
            // Sincroniza o acesso à sessão para evitar que múltiplas threads escrevam ao mesmo tempo.
            synchronized (session) {
                if (session.isOpen())  session.sendMessage(new TextMessage(msg));
            }
        } 
        catch (Exception e) {
            logger.error("Failed to send message to session {}", session.getId(), e);
        }
    }
}