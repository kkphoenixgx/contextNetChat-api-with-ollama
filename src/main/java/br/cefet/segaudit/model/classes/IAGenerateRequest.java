package br.cefet.segaudit.model.classes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the JSON request body sent to the Ollama /api/generate endpoint.
 * It includes the model name, prompt, and optional parameters like context and options.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IAGenerateRequest(
    String model,
    String prompt,
    long[] context,
    OllamaOptions options,
    @JsonProperty("stream") boolean stream
) {
    // Constructor for subsequent messages (with context)
    public IAGenerateRequest(String model, String prompt, long[] context, OllamaOptions options) {
        this(model, prompt, context, options, false);
    }

    // Constructor for the initial message (without context)
    public IAGenerateRequest(String model, String prompt, OllamaOptions options) {
        this(model, prompt, null, options, false);
    }
}