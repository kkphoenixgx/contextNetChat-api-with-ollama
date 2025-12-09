package br.cefet.segaudit.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
public class AppConfig {

    private final ExecutorService contextNetExecutor = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Bean
    public ExecutorService contextNetExecutor() {
        return contextNetExecutor;
    }

    @PreDestroy
    public void shutdownExecutors() {
        contextNetExecutor.shutdown();
        scheduler.shutdown();
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return scheduler;
    }
}