package br.cefet.segaudit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.junit.jupiter.api.TestInstance;

import br.cefet.segaudit.model.classes.TranslationResult;
import br.cefet.segaudit.model.interfaces.IModelManagaer;

@SpringBootTest
@Tag("LiveTest")
public class AIModelManagerLiveTest {

    private static final Logger logger = LoggerFactory.getLogger(AIModelManagerLiveTest.class);

    @Autowired
    private IModelManagaer modelManager;

    private final String sessionId = "live-test-session-456";
    private final String agentPlans = "plans(\"/**@Description Just lifts the embedded system off the ground making it available for next commands*/ takeOff /**@Description If the drone is not flying, executes an internal takeOff and then raises the drone by X units and if it is already flying, raises the drone by X units*/ up(X) /**@Description lowers the drone by X units*/ down(X) /**@Description lands the drone*/ land /**@Description Commands the drone to the right by X units*/ right(X) /**@Description Controls the drone forward by X units*/ forward(X) /**@Description Commands the drone to the left by X units*/ left(X) /**@Description controls the drone backward by X units*/ backward(X) /**@Description turns off the drone*/ turnOff \")";
    
    // private final String agentPlans = "plans(\"/**@Description Apenas tira o embarcado do chão tornando-o disponível para próximos comandos*/ takeOff /**@Description Se o drone não estiver voando, executa um takeOff interno e depois sobe o drone em X unidades e se já estiver voando, sobe o drone em X unidades*/ up(X) /**@Description desce o drone em X unidades*/ down(X) /**@Description pousa o drone*/ land /**@Description Comanda o drone para a direita em X unidades*/ right(X) /**@Description Controla o drone para frente em X unidades*/ forward(X) /**@Description Comanda o drone para a esquerda em X unidades*/ left(X) /**@Description controla o drone em X unidades*/ backward(X) /**@Description desliga o drone*/ turnOff\")" 

    

    @BeforeEach
    void setUp() {

        System.out.println("================================================================");
        System.out.println("=== INICIALIZANDO SESSÃO DE TESTE COM OLLAMA (LIVE)        ===");
        long startInit = System.currentTimeMillis();
        int initTokens = modelManager.initializeUserSession(sessionId, agentPlans);
        long initDuration = System.currentTimeMillis() - startInit;
        Assertions.assertTrue(initTokens > 0, "A inicialização deveria consumir tokens.");
        System.out.printf("=== SESSÃO INICIALIZADA COM SUCESSO. Custo: %d tokens. Tempo: %d ms ===%n", initTokens, initDuration);
        System.out.println("================================================================");
    }

    @Test
    @DisplayName("Teste REAL de Precisão da Tradução da IA")
    void testAccuracyOfTranslation() throws ExecutionException, InterruptedException {
        // String userMessage = "Olá agente, ligue os motores, suba 50 metros, vá para a direita 80 metros, vá para a frente 40, depois vá para a esquerda 40 metros, vá para frente 20 metros, para a esquerda 80 metros, para trás 20 metros, para a esquerda 40 metros, para trás 40 metros, à direita 80 metros, aterrissa e desliga.";
        String userMessage = "Hello agent, start the engines, climb 50 meters, go right 80 meters, move forward 40, then go left 40 meters, forward 20, left 80, back 20 meters, left 40, back 40, right 80, land, and shut down.";

        List<String> expectedCommands = Arrays.asList("takeOff", "up(50)", "right(80)", "forward(40)", "left(40)", "forward(20)", "left(80)", "backward(20)", "left(40)", "backward(40)", "right(80)", "land", "turnOff");
        
        Set<String> expectedSet = new HashSet<>(expectedCommands);
        
        System.out.println("-------------------- TESTE DE PRECISÃO (10 EXECUÇÕES) --------------------");

        for (int i = 1; i <= 10; i++) {
            // Reinicializa a sessão para garantir isolamento de contexto entre iterações
            modelManager.initializeUserSession(sessionId, agentPlans);
            long startTranslate = System.currentTimeMillis();
            List<String> actualCommands = null;
            try {
                TranslationResult result = modelManager.translateMessage(sessionId, userMessage).get();
                actualCommands = result.getKqmlMessages();
            } catch (Exception e) {
                System.out.printf("[Iteração %d] ERRO: %s%n", i, e.getMessage());
                continue;
            }
            long translateDuration = System.currentTimeMillis() - startTranslate;

            if (actualCommands == null) actualCommands = List.of();

            // Cálculo de métricas com base em frequência (Multiset) para suportar duplicatas
            Map<String, Long> expectedFreq = expectedCommands.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            Map<String, Long> actualFreq = actualCommands.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(expectedFreq.keySet());
            allKeys.addAll(actualFreq.keySet());

            long truePositives = 0;
            long falsePositives = 0;
            long falseNegatives = 0;

            for (String key : allKeys) {
                long exp = expectedFreq.getOrDefault(key, 0L);
                long act = actualFreq.getOrDefault(key, 0L);

                truePositives += Math.min(exp, act);
                falsePositives += Math.max(0, act - exp);
                falseNegatives += Math.max(0, exp - act);
            }

            double precision = (truePositives + falsePositives > 0) ? ((double) truePositives / (truePositives + falsePositives)) : 0.0;
            double recall = (truePositives + falseNegatives > 0) ? ((double) truePositives / (truePositives + falseNegatives)) : 0.0;
            double f1Score = (precision + recall > 0) ? (2 * (precision * recall) / (precision + recall)) : 0.0;

            System.out.printf("[Iteração %d] Tempo: %d ms | F1-Score: %.4f%n", i, translateDuration, f1Score);
            System.out.printf("Comandos Gerados   (%d): %s%n", actualCommands.size(), actualCommands);
            System.out.printf("Comandos Esperados (%d): %s%n", expectedCommands.size(), expectedCommands);
            System.out.printf("Verdadeiros Positivos (TP): %d%n", (int)truePositives);
            System.out.printf("Falsos Positivos (FP)     : %d%n", (int)falsePositives);
            System.out.printf("Falsos Negativos (FN)     : %d%n", (int)falseNegatives);
            System.out.println("-------------------------------------------------------------");
        }
        System.out.println("--------------------------------------------------------------------------");
    }

    @Test
    @DisplayName("Teste de Latência (Comando Simples)")
    void testSimpleCommandLatency() throws ExecutionException, InterruptedException {
        String userMessage = "Suba 10 metros";
        List<String> expectedOption1 = Arrays.asList("takeOff", "up(10)");
        List<String> expectedOption2 = Arrays.asList("up(10)");

        long totalDuration = 0;
        System.out.println("-------------------- TESTE DE LATÊNCIA (COMANDO CURTO) --------------------");

        for (int i = 1; i <= 10; i++) {
            modelManager.initializeUserSession(sessionId, agentPlans);
            long start = System.currentTimeMillis();
            TranslationResult result = modelManager.translateMessage(sessionId, userMessage).get();
            long duration = System.currentTimeMillis() - start;
            totalDuration += duration;
            
            List<String> actual = result.getKqmlMessages();
            if (!expectedOption1.equals(actual) && !expectedOption2.equals(actual)) {
                System.out.println("[ALERTA] Comando incorreto na iteração " + i + ": " + actual);
            }
            System.out.printf("[Iteração %d] Tempo: %d ms%n", i, duration);
        }

        double average = totalDuration / 10.0;
        System.out.println("---------------------------------------------------------------------------");
        System.out.printf("[RESUMO] Latência Média (Comando Simples): %.2f ms%n", average);
        System.out.println("---------------------------------------------------------------------------");
        
    }
}