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
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ContextNetWebSocketController extends TextWebSocketHandler {
    private final ContextNetClientFactory contextNetClientFactory;

    private final Map<String, ContextNetClient> clients = new ConcurrentHashMap<>();
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private AIService aiService;
    private final IModelManagaer gemma3ModelManagaer;

    public ContextNetWebSocketController(ContextNetClientFactory factory, IModelManagaer gemma3ModelManagaer) {
        this.contextNetClientFactory = factory;
        this.gemma3ModelManagaer = gemma3ModelManagaer;
        // this.aiService = new AIService(this.gemma3ModelManagaer);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        clients.remove(session.getId());
        // Encerra a sessão no gerenciador do modelo de IA
        gemma3ModelManagaer.endSession(session.getId());
        System.out.println("Sessão WebSocket fechada: " + session.getId() + ". Cliente e sessão de IA removidos.");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        String sessionId = session.getId();

        try {
            if (!clients.containsKey(sessionId)) {
                handleFirstMessage(session, payload);
            } else {
                handleSubsequentMessages(session, payload);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendToSession(session, "Erro no servidor: " + e.getMessage());
        }
    }

    private void handleFirstMessage(WebSocketSession session, String payload) throws Exception {
        String sessionId = session.getId();
        ContextNetConfig config = new ObjectMapper().readValue(payload, ContextNetConfig.class);

        // Cria o cliente e o armazena.
        ContextNetClient client = contextNetClientFactory.create(config, (msg) -> {
            sendToSession(session, msg);
        });
        clients.put(sessionId, client);
        
        sendToSession(session, "Conexão estabelecida e sessão de IA iniciada.");
    }

    private void handleSubsequentMessages(WebSocketSession session, String payload) {
        String sessionId = session.getId();
        ContextNetClient client = clients.get(sessionId);

        // Traduz a mensagem do usuário para um ou mais comandos KQML.
        List<String> kqmlMessages = this.aiService.getKQMLMessages(sessionId, payload);

        // Envia cada comando KQML para a ContextNet.
        for (String kqmlMessage : kqmlMessages) {
            client.sendToContextNet(kqmlMessage);
        }
    }

    private void sendToSession(WebSocketSession session, String msg) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(msg));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}