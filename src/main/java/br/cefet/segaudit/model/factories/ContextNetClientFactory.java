package br.cefet.segaudit.model.factories;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import br.cefet.segaudit.model.classes.ContextNetConfig;
import br.cefet.segaudit.service.ContextNetClient;

@Component
public class ContextNetClientFactory {

    private final ExecutorService contextNetExecutor;
    private final ScheduledExecutorService scheduler;

    public ContextNetClientFactory(ExecutorService contextNetExecutor, ScheduledExecutorService scheduler) {
        this.contextNetExecutor = contextNetExecutor;
        this.scheduler = scheduler;
    }

    public ContextNetClient create(ContextNetConfig config, Consumer<String> messageHandler) {
        return new ContextNetClient(config, messageHandler, contextNetExecutor, scheduler);
    }
}
