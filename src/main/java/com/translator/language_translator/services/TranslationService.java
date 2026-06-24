package com.translator.language_translator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    // Standard browser User-Agent to prevent instant bot-bans from free APIs
    private static final String BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36";

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<String> fallbackApis;

    @Value("${translation.libretranslate.url:https://libretranslate.com/translate}")
    private String libreTranslateUrl;

    @Value("${translation.libretranslate.api-key:}")
    private String libreTranslateApiKey;

    @Value("${translation.mymemory.email:}")
    private String myMemoryEmail;

    public TranslationService(CloseableHttpClient httpClient,
                              ObjectMapper objectMapper,
                              @Value("${translation.fallback-order:google,libretranslate,mymemory}")
                              String fallbackOrder) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.fallbackApis = Arrays.asList(fallbackOrder.split(","));
    }

    @Cacheable(value = "translationCache", key = "{#text, #sourceLang, #targetLang}")
    public String translate(String text, String sourceLang, String targetLang) {
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) {
            return "";
        }

        String src = sourceLang.contains("-") ? sourceLang.split("-")[0] : sourceLang;
        String tgt = targetLang.contains("-") ? targetLang.split("-")[0] : targetLang;

        for (String api : fallbackApis) {
            try {
                String result;
                switch (api.toLowerCase().trim()) {
                    case "google":
                        result = callGoogleTranslate(trimmedText, src, tgt);
                        break;
                    case "libretranslate":
                        result = callLibreTranslate(trimmedText, src, tgt);
                        break;
                    case "mymemory":
                        result = callMyMemory(trimmedText, src, tgt);
                        break;
                    default:
                        continue;
                }
                log.info("Translation succeeded via {}: {} -> {}", api, trimmedText, result);
                return result;
            } catch (Exception e) {
                log.warn("{} translation failed: {}", api, e.getMessage());
            }
        }

        log.error("All translation APIs failed for text: {}", trimmedText);
        return "[Translation Unavailable]";
    }

    private byte[] readEntityBytes(org.apache.hc.core5.http.HttpEntity entity) throws IOException {
        if (entity == null) {
            return new byte[0];
        }
        try (InputStream is = entity.getContent()) {
            return is.readAllBytes();
        }
    }

    // --- Google Translate (free, unofficial endpoint) ---
    private String callGoogleTranslate(String text, String src, String tgt) throws Exception {
        // CLOUD FIX: Stripped the template string to allow URIBuilder to safely encode parameters
        URIBuilder uriBuilder = new URIBuilder("https://translate.googleapis.com/translate_a/single");
        uriBuilder.addParameter("client", "gtx");
        uriBuilder.addParameter("sl", src);
        uriBuilder.addParameter("tl", tgt);
        uriBuilder.addParameter("dt", "t");
        uriBuilder.addParameter("q", text);

        HttpGet get = new HttpGet(uriBuilder.build());
        // CLOUD FIX: Stealth header to prevent IP bans
        get.setHeader("User-Agent", BROWSER_USER_AGENT);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            byte[] bytes = readEntityBytes(response.getEntity());
            JsonNode root = objectMapper.readTree(bytes);
            return root.get(0).get(0).get(0).asText();
        }
    }

    // --- LibreTranslate (self‑hosted or public instance) ---
    private String callLibreTranslate(String text, String src, String tgt) throws Exception {
        HttpPost post = new HttpPost(libreTranslateUrl);
        post.setHeader("Content-Type", "application/json");
        post.setHeader("User-Agent", BROWSER_USER_AGENT);

        Map<String, String> body = new java.util.HashMap<>();
        body.put("q", text);
        body.put("source", src);
        body.put("target", tgt);
        body.put("format", "text");
        if (!libreTranslateApiKey.isEmpty()) {
            body.put("api_key", libreTranslateApiKey);
        }

        StringEntity entity = new StringEntity(objectMapper.writeValueAsString(body),
                ContentType.APPLICATION_JSON);
        post.setEntity(entity);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            byte[] bytes = readEntityBytes(response.getEntity());
            JsonNode root = objectMapper.readTree(bytes);
            if (root.has("error")) {
                throw new RuntimeException(root.path("error").asText());
            }
            return root.path("translatedText").asText();
        }
    }

    // --- MyMemory (free, no API key) ---
    private String callMyMemory(String text, String src, String tgt) throws Exception {
        String langpair = src + "|" + tgt;
        URIBuilder uriBuilder = new URIBuilder("https://api.mymemory.translated.net/get");
        uriBuilder.addParameter("q", text);
        uriBuilder.addParameter("langpair", langpair);
        if (!myMemoryEmail.isEmpty()) {
            uriBuilder.addParameter("de", myMemoryEmail);
        }

        HttpGet get = new HttpGet(uriBuilder.build());
        get.setHeader("User-Agent", BROWSER_USER_AGENT);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            byte[] bytes = readEntityBytes(response.getEntity());
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode responseData = root.path("responseData");
            int status = responseData.path("status").asInt(200);
            if (status != 200 && responseData.path("responseDetails").asText().contains("QUOTA")) {
                throw new RuntimeException("MyMemory quota exceeded");
            }
            return responseData.path("translatedText").asText();
        }
    }
}