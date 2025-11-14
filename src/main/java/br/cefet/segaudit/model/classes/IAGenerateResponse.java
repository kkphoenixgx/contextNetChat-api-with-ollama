package br.cefet.segaudit.AIContextManager.Model;

// @JsonIgnoreProperties é crucial porque a resposta tem muitos campos que não nos interessam
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record IAGenerateResponse(String response, long[] context) {
}