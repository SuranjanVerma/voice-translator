package com.translator.language_translator.config;

import com.translator.language_translator.services.TranslationService;
import com.translator.language_translator.services.VoskModelManager;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public class SpeechWebSocketHandler extends BinaryWebSocketHandler {

    private final VoskModelManager modelManager;
    private final TranslationService translationService;

    private final ConcurrentHashMap<String, Recognizer> activeRecognizers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionTargetLangs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionSourceLangs = new ConcurrentHashMap<>();

    public SpeechWebSocketHandler(VoskModelManager modelManager, TranslationService translationService) {
        this.modelManager = modelManager;
        this.translationService = translationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        String sourceLang = "en-IN";
        String targetLang = "hi";

        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("sourceLang=")) sourceLang = param.substring(11);
                if (param.startsWith("targetLang=")) targetLang = param.substring(11);
            }
        }

        sessionSourceLangs.put(session.getId(), sourceLang);
        sessionTargetLangs.put(session.getId(), targetLang);

        Model sharedModel = modelManager.getModel(sourceLang);

        if (sharedModel == null) {
            session.sendMessage(new TextMessage("{\"error\": \"Speech model is currently unavailable.\"}"));
            session.close();
            return;
        }

        Recognizer sessionRecognizer = new Recognizer(sharedModel, 16000.0f);
        activeRecognizers.put(session.getId(), sessionRecognizer);

        session.sendMessage(new TextMessage("{\"status\": \"ready\"}"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Recognizer recognizer = activeRecognizers.get(session.getId());
        if (recognizer == null) return;

        try {
            // FIX: Safely extract only the actual bytes sent in this frame
            ByteBuffer buffer = message.getPayload();
            int bytesCount = buffer.remaining();
            byte[] audioData = new byte[bytesCount];
            buffer.get(audioData);

            // Pass the precise length of the valid audio array
            if (recognizer.acceptWaveForm(audioData, bytesCount)) {
                String originalText = extractText(recognizer.getResult());

                if (!originalText.trim().isEmpty()) {
                    String sourceLang = sessionSourceLangs.get(session.getId());
                    String targetLang = sessionTargetLangs.get(session.getId());

                    String translatedText = translationService.translate(originalText, sourceLang, targetLang);

                    session.sendMessage(new TextMessage(
                            "{\"original\": \"" + originalText + "\", \"translated\": \"" + translatedText + "\", \"final\": true}"
                    ));
                }
            } else {
                String partialResult = extractText(recognizer.getPartialResult());
                session.sendMessage(new TextMessage("{\"original\": \"" + partialResult + "\"}"));
            }
        } catch (IOException e) {
            // Ignore normal disconnects
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Recognizer recognizerToClose = activeRecognizers.remove(session.getId());
        if (recognizerToClose != null) {
            try {
                recognizerToClose.close(); // Frees underlying C++ structure
            } catch (Exception e) {
                System.err.println("Error explicitly closing recognizer: " + e.getMessage());
            }
        }
        sessionSourceLangs.remove(session.getId());
        sessionTargetLangs.remove(session.getId());

        // Explicitly hint to JVM to clear dead references immediately after session drop
        System.gc();
    }

    private String extractText(String voskJson) {
        int textIndex = voskJson.indexOf("\"text\" : \"");
        int partialIndex = voskJson.indexOf("\"partial\" : \"");

        if (textIndex != -1) {
            int endIndex = voskJson.indexOf("\"", textIndex + 10);
            return voskJson.substring(textIndex + 10, endIndex);
        } else if (partialIndex != -1) {
            int endIndex = voskJson.indexOf("\"", partialIndex + 13);
            return voskJson.substring(partialIndex + 13, endIndex);
        }
        return "";
    }
}