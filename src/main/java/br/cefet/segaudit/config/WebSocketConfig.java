package br.cefet.segaudit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

import br.cefet.segaudit.controller.ContextNetWebSocketController;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ContextNetWebSocketController handler;

    public WebSocketConfig(ContextNetWebSocketController handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
                .addHandler(handler, "/ws")
                .setAllowedOrigins("*");
    }
}
