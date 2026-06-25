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
import java.util.concurrent.ConcurrentHashMap;

public class SpeechWebSocketHandler extends BinaryWebSocketHandler {

    private final VoskModelManager modelManager;
    private final TranslationService translationService;

    // Track active memory and selected languages per user session
    private final ConcurrentHashMap<String, Recognizer> activeRecognizers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionTargetLangs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionSourceLangs = new ConcurrentHashMap<>();

    // Inject BOTH services now
    public SpeechWebSocketHandler(VoskModelManager modelManager, TranslationService translationService) {
        this.modelManager = modelManager;
        this.translationService = translationService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        String sourceLang = "en-IN"; // Default
        String targetLang = "hi";    // Default

        // Extract the exact languages the user picked on the frontend
        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("sourceLang=")) sourceLang = param.substring(11);
                if (param.startsWith("targetLang=")) targetLang = param.substring(11);
            }
        }

        // Save the languages for this specific session
        sessionSourceLangs.put(session.getId(), sourceLang);
        sessionTargetLangs.put(session.getId(), targetLang);

        // Fetch the correct Vosk model dynamically
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
            byte[] audioData = message.getPayload().array();

            if (recognizer.acceptWaveForm(audioData, audioData.length)) {
                // The sentence is finished!
                String originalText = extractText(recognizer.getResult());

                // Only translate if they actually said something
                if (!originalText.trim().isEmpty()) {
                    String sourceLang = sessionSourceLangs.get(session.getId());
                    String targetLang = sessionTargetLangs.get(session.getId());

                    // CALL YOUR GOOGLE TRANSLATE API
                    String translatedText = translationService.translate(originalText, sourceLang, targetLang);

                    // Send BOTH original and translated text back to the frontend
                    session.sendMessage(new TextMessage(
                            "{\"original\": \"" + originalText + "\", \"translated\": \"" + translatedText + "\", \"final\": true}"
                    ));
                }
            } else {
                // Partial sentence (live typing effect). No translation yet to save API calls.
                String partialResult = extractText(recognizer.getPartialResult());
                session.sendMessage(new TextMessage("{\"original\": \"" + partialResult + "\"}"));
            }
        } catch (IOException e) {
            // Ignore normal disconnects
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // Clean up all memory tied to this user
        Recognizer recognizerToClose = activeRecognizers.remove(session.getId());
        if (recognizerToClose != null) {
            recognizerToClose.close();
        }
        sessionSourceLangs.remove(session.getId());
        sessionTargetLangs.remove(session.getId());
    }

    // Helper method to grab just the raw text and strip the JSON quotes
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