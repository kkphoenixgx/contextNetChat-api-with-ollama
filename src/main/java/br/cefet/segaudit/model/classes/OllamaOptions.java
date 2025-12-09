package br.cefet.segaudit.model.classes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the options that can be sent to the Ollama API.
 * This allows for fine-tuning model parameters like context window size.
 */
public record OllamaOptions(
    @JsonProperty("num_ctx") int numCtx
) {}