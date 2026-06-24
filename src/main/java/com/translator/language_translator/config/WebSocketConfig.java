package com.translator.language_translator.config;

import com.translator.language_translator.services.SpeechService;
import com.translator.language_translator.services.TranslationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SpeechService speechService;
    private final TranslationService translationService;

    // Cleaned up constructor: no longer asks for TranslationRepository
    public WebSocketConfig(SpeechService speechService, TranslationService translationService) {
        this.speechService = speechService;
        this.translationService = translationService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Cleaned up handler injection: only passes the two required services
        registry.addHandler(new SpeechWebSocketHandler(speechService, translationService), "/ws/speech")
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