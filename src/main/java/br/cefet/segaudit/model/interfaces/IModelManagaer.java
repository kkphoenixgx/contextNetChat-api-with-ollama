package br.cefet.segaudit.model.interfaces;

import java.util.concurrent.CompletableFuture;
import br.cefet.segaudit.model.classes.TranslationResult;

public interface IModelManagaer {
  /** Initializes a user session with the base context and agent-specific plans. */
  int initializeUserSession(String sessionId, String agentPlans);
  /** Translates a user message into a list of KQML commands using the session context. */
  CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage);
  /** Ends the AI model session for the given session ID. */
  void endSession(String sessionId);
}
