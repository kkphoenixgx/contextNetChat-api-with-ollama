package br.cefet.segaudit.model.classes;

public record IAGenerateRequest(String model, String prompt, long[] context, boolean stream) {

    public IAGenerateRequest(String model, String prompt) {
        this(model, prompt, null, false);
    }


    public IAGenerateRequest(String model, String prompt, long[] context) {
        this(model, prompt, context, false);
    }

}