package br.cefet.segaudit.model.classes;

import com.fasterxml.jackson.annotation.JsonProperty;

// @JsonIgnoreProperties é crucial porque a resposta tem muitos campos que não nos interessam
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record IAGenerateResponse(String response, long[] context, @JsonProperty("prompt_eval_count") int promptEvalCount) {
}