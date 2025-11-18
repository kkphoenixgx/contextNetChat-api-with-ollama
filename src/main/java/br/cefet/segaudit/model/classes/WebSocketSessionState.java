package br.cefet.segaudit.model.classes;

import br.cefet.segaudit.service.AIService;
import br.cefet.segaudit.service.ContextNetClient;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Encapsulates all state related to a single WebSocket session.
 * This includes the ContextNet client, the AI service, and the initialization status.
 */
public class WebSocketSessionState {

    private ContextNetClient contextNetClient;
    private AIService aiService;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public ContextNetClient getContextNetClient() {
        return contextNetClient;
    }

    public void setContextNetClient(ContextNetClient contextNetClient) {
        this.contextNetClient = contextNetClient;
    }

    public AIService getAiService() {
        return aiService;
    }

    public void setAiService(AIService aiService) {
        this.aiService = aiService;
    }

    public boolean isInitialized() {
        return isInitialized.get();
    }

    public void setInitialized(boolean initialized) {
        isInitialized.set(initialized);
    }
}