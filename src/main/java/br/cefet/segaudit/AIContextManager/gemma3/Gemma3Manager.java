package br.cefet.segaudit.AIContextManager.gemma3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.cefet.segaudit.AIContextManager.IO.FileUtil;
import br.cefet.segaudit.AIContextManager.Model.GenerateRequest;
import br.cefet.segaudit.AIContextManager.Model.GenerateResponse;
import br.cefet.segaudit.interfaces.IModelManagaer;

@Component
public class Gemma3Manager implements IModelManagaer {

  private final HttpClient client = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${ollama.api.url}")
  private String ollamaUrl;

  @Value("${ollama.model.name}")
  private String modelName;

  @Value("${ollama.model.contextPath}")
  private String contextPath;


  private final Map<String, long[]> activeSessions = new ConcurrentHashMap<>();

    @Override
    public String translateMessage(String sessionId, String userMessage) {

        if (!activeSessions.containsKey(sessionId)) {
            // initializeUserSession(sessionId);
        }

        try {
            long[] currentContext = activeSessions.get(sessionId);
            
            if (currentContext == null) {
            throw new IllegalStateException("Erro: Sessão do usuário não foi inicializada corretamente.");
            }

            GenerateRequest request = new GenerateRequest(modelName, userMessage, currentContext);
            String jsonBody = objectMapper.writeValueAsString(request);

            GenerateResponse response = makeRequest(jsonBody);

            activeSessions.put(sessionId, response.context());

            return response.response().trim();
        } 
        catch (Exception e) {
            e.printStackTrace();
            return "Erro na tradução para KQML: " + e.getMessage();
        }
        }
    
        @Override
        public void endSession(String sessionId) {
        activeSessions.remove(sessionId);
        System.out.println("Sessão do modelo de IA encerrada para: " + sessionId);
    }


    @Override
    public void initializeUserSession(String sessionId, String agentPlans) {
        
        try {
            String context = FileUtil.readFileAsString(contextPath);

            String initialPrompt = context + "\n" + agentPlans;

            GenerateRequest request = new GenerateRequest(modelName, initialPrompt);
            String jsonBody = objectMapper.writeValueAsString(request);
    
            GenerateResponse response = makeRequest(jsonBody);
    
            activeSessions.put(sessionId, response.context());
            System.out.println("Sessão " + sessionId + " iniciada e contexto do modelo carregado.");
        } 
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Falha ao inicializar a sessão do usuário: " + sessionId, e);
        }
    }

    private GenerateResponse makeRequest(String jsonBody) throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());

        return objectMapper.readValue(httpResponse.body(), GenerateResponse.class);
    
    }

}