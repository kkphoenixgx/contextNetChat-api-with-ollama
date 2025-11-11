package br.cefet.segaudit.model.factories;

import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import br.cefet.segaudit.model.classes.ContextNetConfig;
import br.cefet.segaudit.service.ContextNetClient;

@Component
public class ContextNetClientFactory {

    public ContextNetClient create(ContextNetConfig config, Consumer<String> messageHandler) {
        return new ContextNetClient(config, messageHandler);
    }
}
