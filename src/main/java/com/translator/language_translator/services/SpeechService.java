package com.translator.language_translator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpeechService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechService.class);
    private static final float SAMPLE_RATE = 16000.0f;

    private final File modelsDir;
    private final ConcurrentHashMap<String, Model> modelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Recognizer> activeSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SpeechService(@Value("${vosk.model.dir:/opt/vosk-models}") String modelDirPath,
                         ObjectMapper objectMapper) {
        this.modelsDir = new File(modelDirPath);
        this.objectMapper = objectMapper;
    }

    private String mapLanguageToModelFolder(String langCode) {
        return switch (langCode) {
            case "hi-IN", "hi" -> "model-hi";
            case "te-IN", "te" -> "model-te";
            case "en-US", "en" -> "model-en";
            default          -> "model-in";
        };
    }

    private Model getModelForLanguage(String sourceLang) {
        String folderName = mapLanguageToModelFolder(sourceLang);
        return modelCache.computeIfAbsent(folderName, key -> {
            Model loaded = loadModel(key, true);
            if (loaded == null) {
                throw new RuntimeException(
                        "Vosk model not found for language " + sourceLang +
                                " (expected folder: " + key + "). " +
                                "Please check that the model is present in " + modelsDir.getAbsolutePath()
                );
            }
            return loaded;
        });
    }

    private Model loadModel(String folderName, boolean log) {
        File modelPath = new File(modelsDir, folderName);
        if (!modelPath.exists() || !modelPath.isDirectory() || modelPath.list() == null || modelPath.list().length == 0) {
            if (log) logger.warn("Vosk model folder missing or empty: {}", modelPath.getAbsolutePath());
            return null;
        }
        try {
            if (log) logger.info("Loading Vosk model: {}", modelPath.getAbsolutePath());
            return new Model(modelPath.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to load Vosk model from {}: {}", modelPath.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    public String transcribe(InputStream audio, String sourceLang) throws Exception {
        Model model = getModelForLanguage(sourceLang);
        if (model == null) {
            throw new RuntimeException("No Vosk model available for transcription.");
        }
        try (Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audio.read(buffer)) >= 0) {
                if (bytesRead > 0) {
                    recognizer.acceptWaveForm(buffer, bytesRead);
                }
            }
            String jsonResult = recognizer.getFinalResult();
            JsonNode rootNode = objectMapper.readTree(jsonResult);
            return rootNode.has("text") ? rootNode.get("text").asText() : "";
        }
    }

    public String startSession(String sourceLang) throws Exception {
        Model model = getModelForLanguage(sourceLang);
        if (model == null) {
            throw new RuntimeException("No Vosk model available for language: " + sourceLang);
        }
        String sessionId = java.util.UUID.randomUUID().toString();
        Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
        activeSessions.put(sessionId, recognizer);
        logger.info("Speech session started: {} for language {}", sessionId, sourceLang);
        return sessionId;
    }

    public String processChunk(String sessionId, byte[] audioBytes, int numBytes) throws Exception {
        Recognizer recognizer = activeSessions.get(sessionId);
        if (recognizer == null) {
            throw new RuntimeException("No active speech session for ID: " + sessionId);
        }
        if (numBytes > 0) {
            recognizer.acceptWaveForm(audioBytes, numBytes);
        }
        String partial = recognizer.getPartialResult();
        JsonNode partialNode = objectMapper.readTree(partial);
        String partialText = partialNode.path("partial").asText("");
        return objectMapper.writeValueAsString(Map.of("text", partialText, "final", false));
    }

    public String stopSession(String sessionId) throws Exception {
        Recognizer recognizer = activeSessions.remove(sessionId);
        if (recognizer == null) {
            throw new RuntimeException("No active speech session for ID: " + sessionId);
        }
        String finalJson = recognizer.getFinalResult();
        recognizer.close();
        JsonNode finalNode = objectMapper.readTree(finalJson);
        String finalText = finalNode.has("text") ? finalNode.get("text").asText() : "";
        logger.info("Speech session {} ended. Final text: {}", sessionId, finalText);
        return finalText;
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down SpeechService. Closing all active sessions and models...");
        for (Recognizer rec : activeSessions.values()) {
            try { rec.close(); } catch (Exception ignored) {}
        }
        activeSessions.clear();
        modelCache.values().forEach(Model::close);
    }
}