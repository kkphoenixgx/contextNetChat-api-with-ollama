package br.cefet.segaudit.AIContextManager.Model;

public record GenerateRequest(String model, String prompt, long[] context, boolean stream) {

    public GenerateRequest(String model, String prompt) {
        this(model, prompt, null, false);
    }


    public GenerateRequest(String model, String prompt, long[] context) {
        this(model, prompt, context, false);
    }

}