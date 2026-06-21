package com.translator.language_translator.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
public class TranslationService {

    private final RestTemplate restTemplate = new RestTemplate();

    public String translate(String text, String fromLang, String toLang) {
        try {
            // Place variables inside {} brackets in the URL string
            String url = "https://api.mymemory.translated.net/get?q={q}&langpair={langpair}&de={email}";

            // Pass the data using a Map
            Map<String, String> uriVariables = new HashMap<>();
            uriVariables.put("q", text);
            uriVariables.put("langpair", fromLang + "|" + toLang);
            uriVariables.put("email", "sujitverma0.00000@gmail.com");

            // Give RestTemplate BOTH the URL and the Map
            String rawJson = restTemplate.getForObject(url, String.class, uriVariables);

            JsonNode root = new ObjectMapper().readTree(rawJson);
            return root.path("responseData").path("translatedText").asText();

        } catch (Exception e) {
            e.printStackTrace();
            return "Translation Error";
        }
    }
}