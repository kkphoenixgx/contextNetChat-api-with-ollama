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
    @JsonProperty("stream") boolean stream,
    @JsonProperty("think") Boolean think
) {
    public IAGenerateRequest(String model, String prompt, long[] context, OllamaOptions options) {
        this(model, prompt, context, options, false, false);
    }

    public IAGenerateRequest(String model, String prompt, OllamaOptions options) {
        this(model, prompt, null, options, false, false);
    }
}
// @JsonInclude(JsonInclude.Include.NON_NULL)
// public record IAGenerateRequest(
//     String model,
//     String prompt,
//     long[] context,
//     OllamaOptions options,
//     @JsonProperty("stream") boolean stream
// ) {
//     public IAGenerateRequest(String model, String prompt, long[] context, OllamaOptions options) {
//         this(model, prompt, context, options, false);
//     }

//     public IAGenerateRequest(String model, String prompt, OllamaOptions options) {
//         this(model, prompt, null, options, false);
//     }
// }
