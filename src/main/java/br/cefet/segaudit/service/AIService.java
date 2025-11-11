package br.cefet.segaudit.service;

import java.util.ArrayList;
import java.util.List;

import br.cefet.segaudit.interfaces.IModelManagaer;

public class AIService {

    public IModelManagaer modelManagaer;

    public AIService(IModelManagaer modelManager){
        this.modelManagaer = modelManager;
        this.modelManagaer.initSession();
    }

    public List<String> getKQMLMessages(String message){
        List<String> responses = new ArrayList<String>();

        try {
            this.modelManagaer.translateMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return responses;
    }


}