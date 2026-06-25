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

    private static int activeConnections = 0;
    private static String currentGlobalLang = null;

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

        // Changed default from en-IN to en-US
        String sourceLang = "en-US";
        String targetLang = "hi";

        if (query != null) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("sourceLang=")) sourceLang = param.substring(11);
                if (param.startsWith("targetLang=")) targetLang = param.substring(11);
            }
        }

        synchronized (SpeechWebSocketHandler.class) {
            if (activeConnections >= 2) {
                session.sendMessage(new TextMessage("{\"error\": \"Server is at maximum capacity (2/2). Please wait.\"}"));
                session.close(CloseStatus.SERVER_ERROR);
                return;
            }

            if (activeConnections > 0 && currentGlobalLang != null && !currentGlobalLang.equals(sourceLang)) {
                session.sendMessage(new TextMessage("{\"error\": \"Server is currently locked to " + currentGlobalLang + " for another user to protect memory. Please select " + currentGlobalLang + " or wait for them to finish.\"}"));
                session.close(CloseStatus.SERVER_ERROR);
                return;
            }

            currentGlobalLang = sourceLang;
            activeConnections++;
        }

        Model sharedModel = modelManager.getModel(sourceLang);

        if (sharedModel == null) {
            session.sendMessage(new TextMessage("{\"error\": \"Speech model unavailable.\"}"));
            session.close();
            synchronized (SpeechWebSocketHandler.class) {
                activeConnections--;
                if (activeConnections == 0) currentGlobalLang = null;
            }
            return;
        }

        userSourceLangs.put(session.getId(), sourceLang);
        userTargetLangs.put(session.getId(), targetLang);

        Recognizer privateRecognizer = new Recognizer(sharedModel, 16000.0f);
        userRecognizers.put(session.getId(), privateRecognizer);

        session.sendMessage(new TextMessage("{\"status\": \"ready\"}"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        Recognizer myRecognizer = userRecognizers.get(session.getId());
        if (myRecognizer == null) return;

        try {
            ByteBuffer buffer = message.getPayload();
            int bytesCount = buffer.remaining();

            if (bytesCount > 9000) return;

            byte[] audioData = new byte[bytesCount];
            buffer.get(audioData);

            if (myRecognizer.acceptWaveForm(audioData, bytesCount)) {
                String originalText = extractText(myRecognizer.getResult());

                if (!originalText.trim().isEmpty()) {
                    String sourceLang = userSourceLangs.get(session.getId());
                    String targetLang = userTargetLangs.get(session.getId());

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
            // Ignore normal disconnects
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Recognizer oldRecognizer = userRecognizers.remove(session.getId());

        if (oldRecognizer != null) {
            try { oldRecognizer.close(); } catch (Exception ignored) {}
            System.gc();
        }

        userSourceLangs.remove(session.getId());
        userTargetLangs.remove(session.getId());

        synchronized (SpeechWebSocketHandler.class) {
            activeConnections--;
            if (activeConnections <= 0) {
                activeConnections = 0;
                currentGlobalLang = null;
            }
        }
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