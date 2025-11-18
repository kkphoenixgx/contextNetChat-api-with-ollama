package br.cefet.segaudit.AIContextManager.gemma3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.cefet.segaudit.AIContextManager.IO.FileUtil;
import br.cefet.segaudit.model.classes.IAGenerateRequest;
import br.cefet.segaudit.model.classes.IAGenerateResponse;
import br.cefet.segaudit.model.interfaces.IModelManagaer;

@Component
public class Gemma3Manager implements IModelManagaer {

    private static final Logger logger = LoggerFactory.getLogger(Gemma3Manager.class);

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ollama.api.url}")
    private String ollamaUrl;

    @Value("${ollama.model.name}")
    private String modelName;

    @Value("${ollama.model.context-path}")
    private Resource contextResource;

    private final Map<String, long[]> activeSessions = new ConcurrentHashMap<>();

    /** Translates a user message into a list of KQML commands using the session context. */
    @Override
    public List<String> translateMessage(String sessionId, String userMessage) {
    
        try {
            final long[] currentContext = activeSessions.get(sessionId);
            
            if (currentContext == null) {
            throw new IllegalStateException("Erro: Sessão do usuário não foi inicializada corretamente.");
            }

            IAGenerateRequest request = new IAGenerateRequest(modelName, userMessage, currentContext);
            String jsonBody = objectMapper.writeValueAsString(request);
            logger.debug("Ollama translate request for session {}: {}", sessionId, jsonBody);

            IAGenerateResponse response = makeRequest(jsonBody);

            activeSessions.put(sessionId, response.context());

            String rawResponse = response.response().trim();
            logger.info("Ollama raw response for session {}: {}", sessionId, rawResponse);

            return Arrays.asList(rawResponse.split("\\r?\\n"));
        } 
        catch (Exception e) {
            logger.error("Failed to translate message for session {}", sessionId, e);
            // Retorna uma lista com uma única mensagem de erro para o cliente.
            return List.of("Erro na tradução para KQML: " + e.getMessage());
        }
    }
    
    /** Ends the AI model session for the given session ID. */
    @Override
    public void endSession(String sessionId) {
        activeSessions.remove(sessionId);
        logger.info("AI model session ended for: {}", sessionId);
    }

    /** Initializes a user session with the base context and agent-specific plans. */
    @Override
    public void initializeUserSession(String sessionId, String agentPlans) {
        
        try {
            // 1. Lê o template genérico do arquivo.
            String promptTemplate = FileUtil.readResourceAsString(contextResource);

            // 2. Substitui o placeholder pela lista de planos real do agente.
            // O formato de agentPlans é 'plans("plano1 plano2 ...")'.
            // Extraímos o conteúdo de dentro das aspas e formatamos como uma lista.
            String plansContent = agentPlans.substring(agentPlans.indexOf('"') + 1, agentPlans.lastIndexOf('"'));

            logger.debug("Formatted plans received from agent being sent to AI: \n{}", plansContent);
            String initialPrompt = promptTemplate.replace("##PLANOS_DO_AGENTE##", plansContent);

            IAGenerateRequest request = new IAGenerateRequest(modelName, initialPrompt);
            String jsonBody = objectMapper.writeValueAsString(request);
            logger.debug("Ollama init request for session {}: {}", sessionId, jsonBody);
    
            IAGenerateResponse response = makeRequest(jsonBody);
    
            activeSessions.put(sessionId, response.context());
            logger.info("Session {} initialized and model context loaded.", sessionId);
        } 
        catch (Exception e) {
            logger.error("Failed to initialize user session {}", sessionId, e);
            throw new RuntimeException("Falha ao inicializar a sessão do usuário: " + sessionId, e);
        }
    }

    /** Makes a POST request to the Ollama API with the given JSON body. */
    private IAGenerateResponse makeRequest(String jsonBody) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .timeout(Duration.ofMinutes(10)) // Timeout para a resposta completa (aumentado para 10 minutos)
            .build();

        // Executa a requisição de forma assíncrona para não bloquear o spinner
        CompletableFuture<HttpResponse<String>> future = CompletableFuture.supplyAsync(() -> {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new CompletionException(e);
            }
        });

        // Exibe um spinner de carregamento no console enquanto espera a resposta
        System.out.print("Aguardando resposta do modelo Ollama...  ");
        char[] spinner = {'|', '/', '-', '\\'};
        int i = 0;
        while (!future.isDone()) {
            System.out.print("\b" + spinner[i % spinner.length]); // \b é um backspace para animar no mesmo lugar
            Thread.sleep(250); // Controla a velocidade do spinner
            i++;
        }
        System.out.println("\b. Resposta recebida!");

        try {
            HttpResponse<String> httpResponse = future.get(); // Obtém o resultado (ou a exceção)

            if (httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300) {
                return objectMapper.readValue(httpResponse.body(), IAGenerateResponse.class);
            } else {
                logger.error("Ollama API returned error. Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
                throw new IOException("Request to Ollama API failed with status code " + httpResponse.statusCode());
            }
        } catch (ExecutionException e) {
            // Desembrulha a exceção original que ocorreu dentro do CompletableFuture
            Throwable cause = e.getCause();
            logger.error("Error executing async Ollama request", cause);
            throw new IOException("Falha na execução da requisição para o Ollama.", cause);
        }

    }

}