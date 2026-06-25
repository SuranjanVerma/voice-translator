package com.translator.language_translator.config;

import com.translator.language_translator.services.TranslationService;
import com.translator.language_translator.services.VoskModelManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoskModelManager modelManager;
    private final TranslationService translationService;

    // Inject BOTH services
    public WebSocketConfig(VoskModelManager modelManager, TranslationService translationService) {
        this.modelManager = modelManager;
        this.translationService = translationService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Pass BOTH services into your handler
        registry.addHandler(new SpeechWebSocketHandler(modelManager, translationService), "/ws/speech")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(1024 * 1024);
        container.setMaxTextMessageBufferSize(1024 * 1024);
        return container;
    }
}