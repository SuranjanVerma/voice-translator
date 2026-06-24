package com.translator.language_translator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.translator.language_translator.services.SpeechService;
import com.translator.language_translator.services.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

public class SpeechWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SpeechWebSocketHandler.class);

    private final SpeechService speechService;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cleaned up constructor: Database repository is no longer needed here!
    public SpeechWebSocketHandler(SpeechService speechService, TranslationService translationService) {
        this.speechService = speechService;
        this.translationService = translationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        Map<String, String> params = parseQueryParams(query);
        String sourceLang = params.getOrDefault("sourceLang", "en-US");
        String targetLang = params.getOrDefault("targetLang", "hi-IN");

        String speechSessionId = speechService.startSession(sourceLang);
        session.getAttributes().put("speechSessionId", speechSessionId);
        session.getAttributes().put("sourceLang", sourceLang);
        session.getAttributes().put("targetLang", targetLang);

        // Tracking variables for API throttling
        session.getAttributes().put("lastTranslatedOriginal", "");
        session.getAttributes().put("lastTranslatedResult", "");

        logger.info("WebSocket opened: session={}, speechSessionId={}", session.getId(), speechSessionId);
        session.sendMessage(new TextMessage("{\"status\":\"ready\"}"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            String speechSessionId = (String) session.getAttributes().get("speechSessionId");
            String sourceLang = (String) session.getAttributes().get("sourceLang");
            String targetLang = (String) session.getAttributes().get("targetLang");

            if (speechSessionId == null) {
                session.sendMessage(new TextMessage("{\"error\":\"No active speech session\"}"));
                return;
            }

            byte[] audioChunk = message.getPayload().array();
            int numBytes = message.getPayloadLength();

            String partialJson = speechService.processChunk(speechSessionId, audioChunk, numBytes);
            Map<String, Object> partialResult = objectMapper.readValue(partialJson, Map.class);
            String partialText = (String) partialResult.getOrDefault("text", "");
            boolean isFinal = (boolean) partialResult.getOrDefault("final", false);

            if (partialText.trim().isEmpty()) {
                return; // Skip processing if no text was recognized yet
            }

            String lastTranslatedOriginal = (String) session.getAttributes().get("lastTranslatedOriginal");
            String translatedResult = (String) session.getAttributes().get("lastTranslatedResult");

            int currentWords = partialText.trim().isEmpty() ? 0 : partialText.trim().split("\\s+").length;
            int lastWords = lastTranslatedOriginal.trim().isEmpty() ? 0 : lastTranslatedOriginal.trim().split("\\s+").length;

            // Instantly translate even single words
            if (isFinal || (currentWords - lastWords >= 1)) {
                translatedResult = translationService.translate(partialText, sourceLang, targetLang);
                session.getAttributes().put("lastTranslatedOriginal", partialText);
                session.getAttributes().put("lastTranslatedResult", translatedResult);
            }

            // Send response back to frontend
            String responseJson = objectMapper.writeValueAsString(Map.of(
                    "original", partialText,
                    "translated", translatedResult,
                    "final", isFinal
            ));

            if (session.isOpen()) {
                session.sendMessage(new TextMessage(responseJson));
            }

        } catch (Exception e) {
            logger.error("Error processing audio chunk for session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String speechSessionId = (String) session.getAttributes().get("speechSessionId");
        if (speechSessionId != null) {
            try {
                // Free up the RAM immediately
                speechService.stopSession(speechSessionId);
            } catch (Exception e) {
                logger.error("Error finalizing speech session: {}", e.getMessage());
            }
        }

        // Database logic removed! The REST Controller handles this securely now.
        logger.info("WebSocket closed cleanly: session={}", session.getId());
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    map.put(pair[0], pair[1]);
                }
            }
        }
        return map;
    }
}