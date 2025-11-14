package br.cefet.segaudit.controller;

import br.cefet.segaudit.AIContextManager.gemma3.Gemma3Manager;
import br.cefet.segaudit.interfaces.IModelManagaer;
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

    private final Map<String, ContextNetClient> clients = new ConcurrentHashMap<>();
    private final Map<String, AIService> aiServices = new ConcurrentHashMap<>();
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
    }

    /** Handles incoming text messages, routing them to initialize the session or process subsequent commands. */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String sessionId = session.getId();
        
        try {
            if (!clients.containsKey(sessionId)) { // First message
                handleFirstMessage(session, payload);
            } 
            else {
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
        ContextNetConfig config = objectMapper.readValue(payload, ContextNetConfig.class);

        ContextNetClient client = contextNetClientFactory.create(config, (msg) -> {
            sendToSession(session, msg);
        });
        clients.put(sessionId, client);

        //? Handling agent context
        client.fetchAgentPlans()
          .thenAccept(agentPlans -> {
            logger.info("Received plans {}: {}", sessionId, agentPlans);

            AIService aiService = new AIService(this.modelManagaer, sessionId, agentPlans);
            aiServices.put(sessionId, aiService);
                
            sendToSession(session, "Connection stabilized and IA session ready.");
          })
          .exceptionally(ex -> {
            logger.error("getPlans falied{}", sessionId, ex);
            sendToSession(session, "Error: Could not get plans from agent.");
            return null;
          });

    }

    /** Handles all messages after the initial setup, translating user input into KQML commands and sending them to the ContextNet. */
    private void handleSubsequentMessages(WebSocketSession session, String payload) {
        String sessionId = session.getId();
        ContextNetClient contextNetClient = this.clients.get(sessionId);
        AIService aiService = this.aiServices.get(sessionId);

        List<String> kqmlMessages = aiService.getKQMLMessages(sessionId, payload);

        for (String kqmlMessage : kqmlMessages) {
            contextNetClient.sendToContextNet(kqmlMessage);
        }
    }

    /** Invoked when a WebSocket connection is closed, performing cleanup by removing the client and terminating the AI model session. */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        clients.remove(sessionId);
        aiServices.remove(sessionId);
        modelManagaer.endSession(sessionId);
        logger.info("WebSocket and AI sessions closed for id: {}", sessionId);
    }

    //? ----------- Helpers -----------

    /** Sends a string message to a specific WebSocket session if it is open. */
    private void sendToSession(WebSocketSession session, String msg) {
        try {
            if (session.isOpen())  session.sendMessage(new TextMessage(msg));
        } 
        catch (Exception e) {
            logger.error("Failed to send message to session {}", session.getId(), e);
        }
    }
}