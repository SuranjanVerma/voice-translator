package com.translator.language_translator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Service
public class TranslationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${TRANSLATOR_API_EMAIL}")
    private String apiEmail;

    public TranslationService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String translate(String text, String sourceLang, String targetLang) {
        // Attempt 1: Primary API (Google Translate)
        try {
            return callGoogleApi(text, sourceLang, targetLang);
        } catch (Exception e1) {
            System.err.println("⚠️ Google Translate failed: " + e1.getMessage() + ". Initiating LibreTranslate Fallback...");

            // Attempt 2: First Fallback (LibreTranslate)
            try {
                return callLibreTranslateApi(text, sourceLang, targetLang);
            } catch (Exception e2) {
                System.err.println("⚠️ LibreTranslate failed: " + e2.getMessage() + ". Initiating MyMemory Fallback...");

                // Attempt 3: Final Safety Net (MyMemory)
                try {
                    return callMyMemoryApi(text, sourceLang, targetLang);
                } catch (Exception e3) {
                    System.err.println("❌ All translation APIs failed: " + e3.getMessage());
                    return "[Translation Service Unavailable]";
                }
            }
        }
    }

    // --- API 1: GOOGLE TRANSLATE (PRIMARY) ---
    private String callGoogleApi(String text, String sourceLang, String targetLang) throws Exception {
        String source = sourceLang.split("-")[0];
        String target = targetLang.split("-")[0];

        // FIX: Build directly to a URI object to bypass double-encoding
        URI uri = UriComponentsBuilder.fromUriString("https://translate.googleapis.com/translate_a/single")
                .queryParam("client", "gtx")
                .queryParam("sl", source)
                .queryParam("tl", target)
                .queryParam("dt", "t")
                .queryParam("q", text)
                .build()
                .toUri();

        String response = restTemplate.getForObject(uri, String.class);
        JsonNode root = objectMapper.readTree(response);

        return root.get(0).get(0).get(0).asText();
    }

    // --- API 2: LIBRETRANSLATE (FALLBACK 1) ---
    private String callLibreTranslateApi(String text, String sourceLang, String targetLang) throws Exception {
        String source = sourceLang.split("-")[0];
        String target = targetLang.split("-")[0];

        String url = "https://libretranslate.com/translate";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("q", text);
        requestBody.put("source", source);
        requestBody.put("target", target);
        requestBody.put("format", "text");

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(url, request, String.class);
        JsonNode root = objectMapper.readTree(response);

        if (root.has("error")) {
            throw new RuntimeException(root.path("error").asText());
        }

        return root.path("translatedText").asText();
    }

    // --- API 3: MYMEMORY (FALLBACK 2) ---
    private String callMyMemoryApi(String text, String sourceLang, String targetLang) throws Exception {
        String langpair = sourceLang + "|" + targetLang;

        // FIX: Build directly to a URI object to bypass double-encoding
        URI uri = UriComponentsBuilder.fromUriString("https://api.mymemory.translated.net/get")
                .queryParam("q", text)
                .queryParam("langpair", langpair)
                .queryParam("de", apiEmail)
                .build()
                .toUri();

        String response = restTemplate.getForObject(uri, String.class);
        JsonNode root = objectMapper.readTree(response);

        int responseStatus = root.path("responseData").path("status").asInt(200);
        if (responseStatus != 200 && root.path("responseDetails").asText().contains("QUOTA")) {
            throw new RuntimeException("MyMemory Quota Exceeded");
        }

        return root.path("responseData").path("translatedText").asText();
    }
}