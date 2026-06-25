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

    // Every user gets their own private memory and settings
    private final ConcurrentHashMap<String, Recognizer> userRecognizers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userTargetLangs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userSourceLangs = new ConcurrentHashMap<>();

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

        userSourceLangs.put(session.getId(), sourceLang);
        userTargetLangs.put(session.getId(), targetLang);

        // 1. Get the shared dictionary
        Model sharedModel = modelManager.getModel(sourceLang);

        if (sharedModel == null) {
            session.sendMessage(new TextMessage("{\"error\": \"Speech model unavailable.\"}"));
            session.close();
            return;
        }

        // 2. Create a private "Ear" for this specific user
        Recognizer privateRecognizer = new Recognizer(sharedModel, 16000.0f);
        userRecognizers.put(session.getId(), privateRecognizer);

        session.sendMessage(new TextMessage("{\"status\": \"ready\"}"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Recognizer myRecognizer = userRecognizers.get(session.getId());
        if (myRecognizer == null) return;

        try {
            // FIX: Safely extract only the actual bytes to prevent buffer memory leaks
            ByteBuffer buffer = message.getPayload();
            int bytesCount = buffer.remaining();
            byte[] audioData = new byte[bytesCount];
            buffer.get(audioData);

            if (myRecognizer.acceptWaveForm(audioData, bytesCount)) {
                String originalText = extractText(myRecognizer.getResult());

                if (!originalText.trim().isEmpty()) {
                    String sourceLang = userSourceLangs.get(session.getId());
                    String targetLang = userTargetLangs.get(session.getId());

                    // API Translation
                    String translatedText = translationService.translate(originalText, sourceLang, targetLang);

                    session.sendMessage(new TextMessage(
                            "{\"original\": \"" + originalText + "\", \"translated\": \"" + translatedText + "\", \"final\": true}"
                    ));
                }
            } else {
                String partialResult = extractText(myRecognizer.getPartialResult());
                session.sendMessage(new TextMessage("{\"original\": \"" + partialResult + "\"}"));
            }

        } catch (IOException e) {
            // Ignore disconnects
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 3. Destroy THIS user's private ear, but leave the heavy Model in RAM
        Recognizer oldRecognizer = userRecognizers.remove(session.getId());
        if (oldRecognizer != null) {
            try { oldRecognizer.close(); } catch (Exception ignored) {}
        }

        userSourceLangs.remove(session.getId());
        userTargetLangs.remove(session.getId());
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