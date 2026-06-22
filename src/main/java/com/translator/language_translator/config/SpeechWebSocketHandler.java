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

import java.util.Map;

public class SpeechWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SpeechWebSocketHandler.class);
    private final SpeechService speechService;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpeechWebSocketHandler(SpeechService speechService, TranslationService translationService) {
        this.speechService = speechService;
        this.translationService = translationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Expect parameters: sourceLang and targetLang from the WebSocket URL query
        String query = session.getUri().getQuery();
        Map<String, String> params = parseQueryParams(query);
        String sourceLang = params.getOrDefault("sourceLang", "en-US");
        String targetLang = params.getOrDefault("targetLang", "hi-IN");

        // Start a speech recognition session
        String speechSessionId = speechService.startSession(sourceLang);
        session.getAttributes().put("speechSessionId", speechSessionId);
        session.getAttributes().put("sourceLang", sourceLang);
        session.getAttributes().put("targetLang", targetLang);

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

            // Process the audio chunk and get partial transcription
            String partialJson = speechService.processChunk(speechSessionId, audioChunk, numBytes);
            Map<String, Object> partialResult = objectMapper.readValue(partialJson, Map.class);
            String partialText = (String) partialResult.getOrDefault("text", "");

            // Translate the partial text (even if incomplete) – for a real‑time feel
            String translated = translationService.translate(partialText, sourceLang, targetLang);

            // Send both original and translation back to client
            String responseJson = objectMapper.writeValueAsString(Map.of(
                    "original", partialText,
                    "translated", translated,
                    "final", partialResult.getOrDefault("final", false)
            ));
            session.sendMessage(new TextMessage(responseJson));

        } catch (Exception e) {
            logger.error("Error processing audio chunk for session {}: {}", session.getId(), e.getMessage());
            try {
                session.sendMessage(new TextMessage("{\"error\":\"Processing error\"}"));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String speechSessionId = (String) session.getAttributes().get("speechSessionId");
        if (speechSessionId != null) {
            try {
                String finalText = speechService.stopSession(speechSessionId);
                // Optionally send final result back before closing
                String sourceLang = (String) session.getAttributes().get("sourceLang");
                String targetLang = (String) session.getAttributes().get("targetLang");
                String translated = translationService.translate(finalText, sourceLang, targetLang);
                String finalJson = objectMapper.writeValueAsString(Map.of(
                        "original", finalText,
                        "translated", translated,
                        "final", true
                ));
                session.sendMessage(new TextMessage(finalJson));
            } catch (Exception e) {
                logger.error("Error finalizing speech session: {}", e.getMessage());
            }
        }
        logger.info("WebSocket closed: session={}", session.getId());
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new java.util.HashMap<>();
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