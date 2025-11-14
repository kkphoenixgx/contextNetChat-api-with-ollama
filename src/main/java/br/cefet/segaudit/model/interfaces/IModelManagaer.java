package br.cefet.segaudit.model.interfaces;

import java.util.List;

public interface IModelManagaer {
  /** Initializes a user session with the base context and agent-specific plans. */
  void initializeUserSession(String sessionId, String agentPlans);
  /** Translates a user message into a list of KQML commands using the session context. */
  List<String> translateMessage(String sessionId, String userMessage);
  /** Ends the AI model session for the given session ID. */
  void endSession(String sessionId);
}
