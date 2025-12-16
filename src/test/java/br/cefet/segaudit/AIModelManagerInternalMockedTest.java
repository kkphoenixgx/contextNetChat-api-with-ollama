package br.cefet.segaudit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.cefet.segaudit.model.classes.IAGenerateResponse;
import br.cefet.segaudit.model.classes.TranslationResult;
import br.cefet.segaudit.model.interfaces.IModelManagaer;

@SpringBootTest
public class AIModelManagerInternalMockedTest {

    private static final Logger logger = LoggerFactory.getLogger(AIModelManagerInternalMockedTest.class);

    @Mock
    private HttpClient mockHttpClient;

    @Autowired
    private IModelManagaer modelManager;

    @Autowired
    private ObjectMapper objectMapper;

    private final String sessionId = "test-session-123";
    private final String agentPlans = "plans(\"takeOff,land,turnoff,up(N),down(N),left(N),right(N),forward(N),backward(N)\")";

    @BeforeEach
    void setUp() throws JsonProcessingException {
        ReflectionTestUtils.setField(modelManager, "client", mockHttpClient);

        // Mock da resposta para a chamada de inicialização
        IAGenerateResponse initResponse = new IAGenerateResponse("Initialized.", new long[]{1, 2, 3}, 1500);
        String initResponseJson = objectMapper.writeValueAsString(initResponse);
        
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockInitHttpResponse = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(mockInitHttpResponse.statusCode()).thenReturn(200);
        when(mockInitHttpResponse.body()).thenReturn(initResponseJson);

        // Mock da resposta para a chamada de tradução
        String expectedCommands = "takeOff\nup(50)\nright(80)\nforward(40)\nleft(40)\nforward(20)\nleft(80)\nbackward(20)\nleft(40)\nbackward(40)\nright(80)\nland\nturnoff";
        IAGenerateResponse translateResponse = new IAGenerateResponse(expectedCommands, new long[]{4, 5, 6}, 350);
        String translateResponseJson = objectMapper.writeValueAsString(translateResponse);

        @SuppressWarnings("unchecked")
        HttpResponse<String> mockTranslateHttpResponse = (HttpResponse<String>) org.mockito.Mockito.mock(HttpResponse.class);
        when(mockTranslateHttpResponse.statusCode()).thenReturn(200);
        when(mockTranslateHttpResponse.body()).thenReturn(translateResponseJson);

        when(mockHttpClient.sendAsync(any(), any(HttpResponse.BodyHandler.class)))
            .thenReturn(CompletableFuture.completedFuture(mockInitHttpResponse))
            .thenReturn(CompletableFuture.completedFuture(mockTranslateHttpResponse));
    }

    @Test
    @DisplayName("Teste de Métricas de Desempenho e Precisão da Tradução da IA")
    void testPerformanceAndAccuracyMetrics() throws ExecutionException, InterruptedException {
        // --- Fase 1: Inicialização da Sessão ---
        Instant startInit = Instant.now();
        int initTokens = modelManager.initializeUserSession(sessionId, agentPlans);
        Duration initDuration = Duration.between(startInit, Instant.now());

        logger.info("========================= METRICS REPORT =========================");
        logger.info("[MÉTRICA] Custo de Inicialização (Tokens): {}", initTokens);
        logger.info("[MÉTRICA] Tempo de Inicialização (ms): {}", initDuration.toMillis());

        // --- Fase 2: Teste de Tradução e Performance (10x) ---
        String userMessage = "Olá agente, ligue os motores, suba 50 metros, vá para a direita 80 metros, vá para a frente 40, depois vá para a esquerda 40 metros, vá para frente 20 metros, para a esquerda 80 metros, para trás 20 metros, para a esquerda 40 metros, para trás 40 metros, à direita 80 metros, aterrissa e desliga.";
        List<String> expectedCommands = Arrays.asList("takeOff", "up(50)", "right(80)", "forward(40)", "left(40)", "forward(20)", "left(80)", "backward(20)", "left(40)", "backward(40)", "right(80)", "land", "turnoff");
        
        long totalTranslationTime = 0;
        List<String> actualCommands = null;

        logger.info("--- Iniciando 10 execuções de teste de tradução ---");
        for (int i = 0; i < 10; i++) {
            Instant startTranslate = Instant.now();
            CompletableFuture<TranslationResult> future = modelManager.translateMessage(sessionId, userMessage);
            TranslationResult result = future.get();
            Duration translateDuration = Duration.between(startTranslate, Instant.now());
            
            totalTranslationTime += translateDuration.toNanos();
            actualCommands = result.getKqmlMessages(); // Armazena o resultado da última execução

            logger.info("[RUN {}/10] Tempo de Resposta (ms): {}", i + 1, translateDuration.toMillis());
        }
        logger.info("--- Fim das execuções ---");

        assertNotNull(actualCommands, "A lista de comandos retornada não pode ser nula.");

        // --- Fase 3: Análise de Métricas ---
        double averageTranslationTime = (totalTranslationTime / 10.0) / 1_000_000.0; // Nanos para millis

        Set<String> expectedSet = new HashSet<>(expectedCommands);
        Set<String> actualSet = new HashSet<>(actualCommands);

        Set<String> intersection = new HashSet<>(expectedSet);
        intersection.retainAll(actualSet);
        double truePositives = intersection.size();
        double falsePositives = actualSet.size() - truePositives;
        double falseNegatives = expectedSet.size() - truePositives;

        double precision = (truePositives + falsePositives > 0) ? (truePositives / (truePositives + falsePositives)) : 0.0;
        double recall = (truePositives + falseNegatives > 0) ? (truePositives / (truePositives + falseNegatives)) : 0.0;
        double f1Score = (precision + recall > 0) ? (2 * (precision * recall) / (precision + recall)) : 0.0;

        logger.info("-------------------- ANÁLISE FINAL --------------------");
        logger.info("[MÉTRICA] Tempo Médio de Resposta (10 execuções) (ms): {}", String.format("%.2f", averageTranslationTime));
        logger.info("----------------- Métricas de Classificação -----------------");
        logger.info("Comandos Esperados ({}): {}", expectedCommands.size(), expectedCommands);
        logger.info("Comandos Gerados   ({}): {}", actualCommands.size(), actualCommands);
        logger.info("Verdadeiros Positivos (TP): {}", (int)truePositives);
        logger.info("Falsos Positivos (FP)     : {}", (int)falsePositives);
        logger.info("Falsos Negativos (FN)     : {}", (int)falseNegatives);
        logger.info("-------------------------------------------------------------");
        logger.info("[MÉTRICA] Precisão (Precision): {}", String.format("%.4f", precision));
        logger.info("[MÉTRICA] Revocação (Recall):    {}", String.format("%.4f", recall));
        logger.info("[MÉTRICA] Pontuação-F1 (F1-Score): {}", String.format("%.4f", f1Score));
        logger.info("================================================================");

        Assertions.assertEquals(expectedCommands.size(), actualCommands.size(), "O número de comandos gerados difere do esperado.");
        Assertions.assertEquals(
            expectedCommands.stream().sorted().collect(Collectors.toList()),
            actualCommands.stream().sorted().collect(Collectors.toList()),
            "O conteúdo dos comandos gerados não corresponde ao esperado."
        );
    }
}