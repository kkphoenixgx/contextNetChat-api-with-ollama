package br.cefet.segaudit.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.cefet.segaudit.model.interfaces.IModelManagaer;
import br.cefet.segaudit.model.classes.TranslationResult;

public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);
    public IModelManagaer modelManagaer;

    private final String sessionId;
    private final Executor executor;

    public AIService(IModelManagaer modelManager, String sessionId, Executor executor) {
        this.modelManagaer = modelManager;
        this.sessionId = sessionId;
        this.executor = executor;
    }

    public CompletableFuture<Void> initialize(String agentPlans) {
        return CompletableFuture.runAsync(() -> {
            int initialContextCost = this.modelManagaer.initializeUserSession(sessionId, agentPlans);
            logger.info("[{}] Initial context cost (prompt_eval_count): {} tokens.", sessionId, initialContextCost);
            if (initialContextCost > 2000) {
                logger.warn("[{}] ATTENTION: Initial context size is over 2000 tokens. This may impact performance.", sessionId);
            }
        }, executor);
    }
    public CompletableFuture<List<String>> getKQMLMessages(String sessionId, String message) {
        return this.modelManagaer.translateMessage(sessionId, message)
            .thenApply(result -> {
                logger.info("[{}] Translation prompt cost (prompt_eval_count): {} tokens.", sessionId, result.getPromptEvalCount());
                return result.getKqmlMessages();
            }).exceptionally(e -> {
                logger.error("[{}] Error processing AI translation", sessionId, e);
                return List.of("Error processing AI translation: " + e.getMessage());
            });
    }
}