package com.translator.language_translator.config;

import com.translator.language_translator.services.SpeechService;
import com.translator.language_translator.services.TranslationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SpeechService speechService;
    private final TranslationService translationService;

    public WebSocketConfig(SpeechService speechService, TranslationService translationService) {
        this.speechService = speechService;
        this.translationService = translationService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SpeechWebSocketHandler(speechService, translationService), "/ws/speech")
                .setAllowedOrigins("*");   // tighten this in production if needed
    }
}