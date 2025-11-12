package br.cefet.segaudit.interfaces;

public interface IModelManagaer {
  void initializeUserSession(String sessionId, String userPlans);
  String translateMessage(String sessionId, String userMessage);
  void endSession(String sessionId);
}
