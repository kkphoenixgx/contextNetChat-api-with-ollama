package br.cefet.segaudit.service;

import java.util.ArrayList;
import java.util.List;

import br.cefet.segaudit.interfaces.IModelManagaer;

public class AIService {

    public IModelManagaer modelManagaer;

    public AIService(IModelManagaer modelManager, String sessionId, String agentPlans ){
        this.modelManagaer = modelManager;
        this.modelManagaer.initializeUserSession(sessionId, agentPlans);
    }

    public List<String> getKQMLMessages(String sessionId, String message){
        List<String> responses = new ArrayList<String>();

        try {
            this.modelManagaer.translateMessage(sessionId, message);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return responses;
    }


}