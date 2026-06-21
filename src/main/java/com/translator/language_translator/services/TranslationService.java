package com.translator.language_translator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class TranslationService {

    private static final Logger logger = LoggerFactory.getLogger(TranslationService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiEmail;

    public TranslationService(RestTemplate restTemplate,
                              ObjectMapper objectMapper,
                              @Value("${translator.api.email:sujitverma0.00000@gmail.com}") String apiEmail) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiEmail = apiEmail;
    }

    public String translate(String text, String fromLang, String toLang) {
        try {
            String url = "https://api.mymemory.translated.net/get?q={q}&langpair={langpair}&de={email}";

            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("q", text);
            uriVariables.put("langpair", fromLang + "|" + toLang);
            uriVariables.put("email", apiEmail);

            String rawJson = restTemplate.getForObject(url, String.class, uriVariables);

            JsonNode root = objectMapper.readTree(rawJson);

            if (root != null && root.has("responseData") && root.get("responseData").has("translatedText")) {
                return root.path("responseData").path("translatedText").asText();
            } else {
                logger.warn("Unexpected JSON response from translation API: {}", rawJson);
                return "Translation Failed: Invalid Response";
            }

        } catch (Exception e) {
            logger.error("Error during translation of text: '{}' from {} to {}", text, fromLang, toLang, e);
            return "Translation Error";
        }
    }
}