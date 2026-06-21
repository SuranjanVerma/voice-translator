package com.translator.language_translator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class LanguageTranslatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LanguageTranslatorApplication.class, args);
    }

    // This creates the RestTemplate bean required by TranslationService
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // This creates the ObjectMapper bean required by SpeechService
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}