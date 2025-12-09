package br.cefet.segaudit.model.classes;

import java.util.List;

public class TranslationResult {
    private final List<String> kqmlMessages;
    private final int promptEvalCount;

    public TranslationResult(List<String> kqmlMessages, int promptEvalCount) {
        this.kqmlMessages = kqmlMessages;
        this.promptEvalCount = promptEvalCount;
    }

    public List<String> getKqmlMessages() {
        return kqmlMessages;
    }

    public int getPromptEvalCount() {
        return promptEvalCount;
    }
}