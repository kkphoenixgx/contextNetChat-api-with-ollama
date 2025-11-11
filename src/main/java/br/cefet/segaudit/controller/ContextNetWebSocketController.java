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
    private IModelManagaer gemma3ModelManagaer;

    public ContextNetWebSocketController(ContextNetClientFactory factory) {
        this.contextNetClientFactory = factory;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        
        try {
            this.gemma3ModelManagaer = new Gemma3Manager();
            this.aiService = new AIService(this.gemma3ModelManagaer);
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        clients.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String clientMessage = message.getPayload();

        List<String> kqmlMessages = this.aiService.getKQMLMessages(clientMessage);


        for ( String currentPayload :  kqmlMessages) {
         
            try {

                if (!clients.containsKey(session.getId())) {
                    ContextNetConfig config = new ObjectMapper().readValue(currentPayload, ContextNetConfig.class);

                    ContextNetClient client = contextNetClientFactory.create(config, (msg) -> {
                        sendToSession(session, msg);
                    });

                    clients.put(session.getId(), client);
                } else {
                    ContextNetClient client = clients.get(session.getId());
                    client.sendToContextNet(currentPayload);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

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