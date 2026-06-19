package com.translator.language_translator.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TranslationService {
    public String translate(String text, String fromLang, String toLang) {
        String url = String.format("https://api.mymemory.translated.net/get?q=%s&langpair=%s|%s&de=sujitverma0.00000@gmail.com", text, fromLang, toLang);
        try {
            String rawJson = new RestTemplate().getForObject(url, String.class);
            JsonNode root = new ObjectMapper().readTree(rawJson);
            return root.path("responseData").path("translatedText").asText();
        } catch (Exception e) {
            return "Translation Error";
        }
    }
}
