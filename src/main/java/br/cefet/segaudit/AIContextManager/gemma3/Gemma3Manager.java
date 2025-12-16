package br.cefet.segaudit.AIContextManager.gemma3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutionException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;

import br.cefet.segaudit.AIContextManager.IO.FileUtil;
import br.cefet.segaudit.model.classes.IAGenerateRequest;
import br.cefet.segaudit.model.classes.IAGenerateResponse;
import br.cefet.segaudit.model.classes.OllamaOptions;
import br.cefet.segaudit.model.classes.TranslationResult;
import br.cefet.segaudit.model.interfaces.IModelManagaer;

@Component
public class Gemma3Manager implements IModelManagaer {

    private static final Logger logger = LoggerFactory.getLogger(Gemma3Manager.class);

    private final HttpClient client;
    private final ObjectMapper objectMapper;

    @Value("${ollama.api.url}")
    private String ollamaUrl;

    @Value("${ollama.model.name}")
    private String modelName;

    @Value("${ollama.model.context-path}")
    private Resource contextResource;

    private final OllamaOptions ollamaOptions = new OllamaOptions(
        4096
    );

    private final Map<String, long[]> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> pendingRequests = new ConcurrentHashMap<>();

    @Autowired
    public Gemma3Manager(HttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /** Translates a user message into a list of KQML commands using the session context. */
    @Override
    public CompletableFuture<TranslationResult> translateMessage(String sessionId, String userMessage) {
        try {
            final long[] currentContext = activeSessions.get(sessionId);
            if (currentContext == null) {
                throw new IllegalStateException("Erro: Sessão do usuário não foi inicializada corretamente.");
            }

            IAGenerateRequest request = new IAGenerateRequest(modelName, userMessage, currentContext, ollamaOptions);
            String jsonBody = objectMapper.writeValueAsString(request);
            logger.debug("Ollama translate request for session {}: {}", sessionId, jsonBody);

            CompletableFuture<TranslationResult> future = makeRequest(jsonBody).thenApply(response -> {
                activeSessions.put(sessionId, response.context());
                String rawResponse = response.response().trim();
                
                logger.info("Ollama raw response for session {}: {}", sessionId, rawResponse);
               
                // var kqmlMessages = Arrays.asList(rawResponse.split("\\r?\\n"));
                var kqmlMessages = rawResponse.lines()
                                              .map(String::trim)
                                              .filter(line -> !line.isEmpty())
                                              .collect(Collectors.toList());
                return new TranslationResult(kqmlMessages, response.promptEvalCount());
            });

            pendingRequests.put(sessionId, future);
            future.whenComplete((result, ex) -> pendingRequests.remove(sessionId));
            return future;
        } catch (Exception e) {
            logger.error("Failed to translate message for session {}", sessionId, e);
            return CompletableFuture.failedFuture(new RuntimeException("Failed to translate message for session " + sessionId, e));
        }
    }

    /** Ends the AI model session for the given session ID. */
    @Override
    public void endSession(String sessionId) {
        if (activeSessions.remove(sessionId) != null) {
            logger.info("AI model session ended for: {}", sessionId);
        }
        // Cancela qualquer requisição pendente para esta sessão
        CompletableFuture<?> pending = pendingRequests.remove(sessionId);
        if (pending != null && !pending.isDone()) {
            pending.cancel(true);
            logger.warn("Cancelled pending AI request for session {}", sessionId);
        }
    }

    /** Initializes a user session with the base context and agent-specific plans. */
    @Override
    public int initializeUserSession(String sessionId, String agentPlans) {
        
        try {
             String promptTemplate = FileUtil.readResourceAsString(contextResource);
             String plansContent = agentPlans.substring(agentPlans.indexOf('"') + 1, agentPlans.lastIndexOf('"'));
             logger.debug("Formatted plans received from agent being sent to AI: \n{}", plansContent);
             String initialPrompt = promptTemplate.replace("##PLANOS_DO_AGENTE##", plansContent);
 
             IAGenerateRequest request = new IAGenerateRequest(modelName, initialPrompt, ollamaOptions);
             String jsonBody = objectMapper.writeValueAsString(request);
             logger.debug("Ollama init request for session {}: {}", sessionId, jsonBody);
     
             CompletableFuture<IAGenerateResponse> future = makeRequest(jsonBody);
             pendingRequests.put(sessionId, future);
             IAGenerateResponse response = future.get(); // Block only on initialization
     
             activeSessions.put(sessionId, response.context());
             logger.info("Session {} initialized and model context loaded.", sessionId);
             return response.promptEvalCount();
        } catch (InterruptedException | IOException e) {
            logger.error("Failed to initialize user session {} due to an IO or interruption error.", sessionId, e);
            throw new RuntimeException("Falha ao inicializar a sessão do usuário: " + sessionId, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                logger.error("Failed to initialize user session {} due to a network error: {}", sessionId, cause.getMessage());

                throw new RuntimeException("Cannot connect to AI model. Please check network connectivity and firewall settings.", cause);
            }
            throw new RuntimeException("An unexpected error occurred during AI session initialization.", e);
        } finally {
            pendingRequests.remove(sessionId);
        }
    }

    /** Makes a POST request to the Ollama API with the given JSON body. */
    private CompletableFuture<IAGenerateResponse> makeRequest(String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMinutes(10))
                .build();

        logger.info("Sending request to Ollama...");
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(httpResponse -> {
            logger.info("Received response from Ollama.");
            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                try {
                    return objectMapper.readValue(httpResponse.body(), IAGenerateResponse.class);
                } catch (IOException e) {
                    logger.error("Failed to parse Ollama response", e);
                    throw new CompletionException(e);
                }
            } else {
                logger.error("Ollama API returned error. Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
                throw new CompletionException(new IOException("Request to Ollama API failed with status code " + httpResponse.statusCode()));
            }
        });
    }

}