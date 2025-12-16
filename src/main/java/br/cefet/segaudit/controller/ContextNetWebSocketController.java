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
import java.util.concurrent.ExecutorService;

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
    private final ExecutorService contextNetExecutor;

    /** Initializes the controller with required factories and managers for handling WebSocket connections. */
    public ContextNetWebSocketController(ContextNetClientFactory factory, IModelManagaer modelManagaer, ObjectMapper objectMapper, ExecutorService contextNetExecutor) {
        this.contextNetClientFactory = factory;
        this.modelManagaer = modelManagaer;
        this.objectMapper = objectMapper;
        this.contextNetExecutor = contextNetExecutor;
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

            if (!state.isInitialized() && !state.isInitializing()) {
                handleFirstMessage(session, payload);
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

        state.setInitializing(true);

        ContextNetConfig config = objectMapper.readValue(payload, ContextNetConfig.class);

        if (config.gatewayIP == null || config.myUUID == null || config.destinationUUID == null) {
            logger.error("[{}] Invalid configuration received. Closing session.", sessionId);
            sendToSession(session, "Error: Invalid configuration. 'gatewayIP', 'agentUUID', and 'destinationUUID' are required.");
            session.close();
            return;
        }
        logger.debug("[{}] Parsed ContextNetConfig: MyUUID={}, DestinationUUID={}", sessionId, config.myUUID, config.destinationUUID);

        ContextNetClient client = contextNetClientFactory.create(config, (msg) -> {
            sendToSession(session, msg);
        });
        state.setContextNetClient(client);
        logger.info("[{}] ContextNetClient created and stored.", sessionId);
        
        client.getConnectionFuture().thenCompose(v -> {
            logger.info("[{}] Connection to ContextNet established. Fetching agent plans...", sessionId);
            //? Busca os planos do agente de forma assíncrona.
            return client.fetchAgentPlans().orTimeout(120, java.util.concurrent.TimeUnit.SECONDS); // Adiciona um timeout de 2m
        })
          .thenCompose(agentPlans -> {
            logger.info("[{}] Successfully received plans from agent: {}", sessionId, agentPlans);
            logger.info("[{}] Contexto do agente recuperado com sucesso.", sessionId);
            logger.info("[{}] Initializing AI Service...", sessionId);
            AIService aiService = new AIService(this.modelManagaer, sessionId, contextNetExecutor);
            state.setAiService(aiService);
            return aiService.initialize(agentPlans);
        })
          .thenAccept(v -> {
            state.setInitializing(false); // Libera o bloqueio de inicialização.
            state.setInitialized(true); // Marca a sessão como totalmente inicializada.
            logger.info("[{}] AI Service initialized and stored.", sessionId);
            sendToSession(session, "Connection stabilized and IA session ready.");
        }).exceptionally(ex -> {
            state.setInitializing(false); // Libera o bloqueio em caso de erro.
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

            if (cause instanceof java.util.concurrent.TimeoutException) {
                logger.error("[{}] Timed out waiting for agent to respond with plans.", sessionId, cause);
                sendToSession(session, "Error: Could not get plans from agent. The agent did not respond in time.");
            } else if (cause instanceof java.io.IOException || (cause instanceof RuntimeException && cause.getMessage().contains("Cannot connect to AI model"))) {
                logger.error("[{}] Failed to connect to a required service (ContextNet or AI): {}", sessionId, cause.getMessage());
                sendToSession(session, "Error: " + cause.getMessage());
            } 
            else {
                logger.error("[{}] Failed to get plans from agent. Reason: {}", sessionId, cause.getMessage(), cause);
                sendToSession(session, "Error: Could not get plans from agent. " + cause.getMessage());
            }

            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR.withReason(cause.getMessage()));
                }
            } catch (Exception e) {
                logger.error("[{}] Error closing session after failure.", sessionId, e);
            }

            return null;
        });
    }

    /** Handles all messages after the initial setup, translating user input into KQML commands and sending them to the ContextNet. */
    private void handleSubsequentMessages(WebSocketSession session, String payload) {
        String sessionId = session.getId();
        WebSocketSessionState state = sessions.get(sessionId);

        if (state == null || !state.isInitialized()) {
            logger.warn("[{}] Received message, but session is not fully initialized. Ignoring.", sessionId);
            sendToSession(session, "Warning: Session not ready. Please wait for 'Connection stabilized' message.");
            return;
        }

        if (!state.tryStartProcessing()) {
            logger.warn("[{}] Received message while another is still being processed. Ignoring.", sessionId);
            sendToSession(session, "Warning: Previous message is still being processed. Please wait.");
            return;
        }
        AIService aiService = state.getAiService();

        logger.info("[{}] Handling subsequent message: '{}'", sessionId, payload);

        aiService.getKQMLMessages(sessionId, payload)
            .thenAccept(kqmlMessages -> {
                logger.info("[{}] AI translated message to {} KQML command(s): {}", sessionId, kqmlMessages.size(), kqmlMessages);
                ContextNetClient client = state.getContextNetClient();

                // Se houver múltiplos comandos, envie-os com um pequeno atraso entre eles.
                // Este loop ainda é bloqueante, mas apenas por curtos períodos.
                // Para uma solução totalmente não bloqueante, seria necessário um agendador.
                for (int i = 0; i < kqmlMessages.size(); i++) {
                    String kqmlMessage = kqmlMessages.get(i);
                    String fullKqmlMessage = String.format("achieve,%s,%s", client.getDestinationUUID(), kqmlMessage);
                    client.sendToContextNet(fullKqmlMessage);
                    logger.debug("[{}] Sent to ContextNet: {}", sessionId, fullKqmlMessage);

                    if (i < kqmlMessages.size() - 1) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.error("Thread interrupted while waiting to send next command", e);
                            break; // Sai do loop se a thread for interrompida
                        }
                    }
                }
            })
            .exceptionally(ex -> {
                logger.error("[{}] Error processing AI translation for subsequent message.", sessionId, ex);
                sendToSession(session, "Error during AI translation: " + ex.getMessage());
                return null;
            })
            .whenComplete((res, ex) -> {
                state.finishProcessing(); // Libera o semáforo, permitindo a próxima mensagem
            });
    }

    /** Invoked when a WebSocket connection is closed, performing cleanup by removing the client and terminating the AI model session. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        WebSocketSessionState state = sessions.get(sessionId);
        if (state != null && state.getContextNetClient() != null) {
            // Cancela qualquer requisição pendente para evitar que fique "presa"
            state.getContextNetClient().cancelPendingRequests();
            state.getContextNetClient().cancelConnectionTimeout();
        }

        sessions.remove(sessionId);
        modelManagaer.endSession(sessionId);
        logger.info("WebSocket and AI sessions closed for id: {}", sessionId);
    }

    //? ----------- Helpers -----------

    /** Sends a string message to a specific WebSocket session if it is open. */
    private void sendToSession(WebSocketSession session, String msg) {
        try { 
            synchronized (session) {
                if (session.isOpen())  session.sendMessage(new TextMessage(msg));
            }
        } 
        catch (Exception e) {
            logger.error("Failed to send message to session {}", session.getId(), e);
        }
    }
}