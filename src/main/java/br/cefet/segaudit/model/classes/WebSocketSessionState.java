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
    private final AtomicBoolean isInitializing = new AtomicBoolean(false);
    private final AtomicBoolean isProcessingMessage = new AtomicBoolean(false);

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

    public boolean isInitializing() {
        return isInitializing.get();
    }

    public void setInitializing(boolean initializing) {
        isInitializing.set(initializing);
    }

    /**
     * Tenta marcar a sessão como "processando uma mensagem".
     * @return {@code true} se o bloqueio foi adquirido com sucesso, {@code false} caso contrário.
     */
    public boolean tryStartProcessing() {
        return isProcessingMessage.compareAndSet(false, true);
    }

    /**
     * Marca a sessão como "não mais processando uma mensagem".
     */
    public void finishProcessing() {
        isProcessingMessage.set(false);
    }
}