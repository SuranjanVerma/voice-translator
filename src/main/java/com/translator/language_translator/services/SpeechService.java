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
    private Model fallbackModel;

    public SpeechService(@Value("${vosk.model.dir:/opt/vosk-models}") String modelDirPath,
                         ObjectMapper objectMapper) {
        this.modelsDir = new File(modelDirPath);
        this.objectMapper = objectMapper;

        // Pre-load a fallback model (Hindi, as "model-in")
        this.fallbackModel = loadModel("model-in", false);
        if (fallbackModel == null) {
            logger.error("CRITICAL: Fallback model (model-in) could not be loaded from {}. " +
                    "Speech recognition will fail for unsupported languages.", modelsDir.getAbsolutePath());
        }
    }

    /**
     * Map language codes to Vosk model folder names (must match directory names inside vosk.model.dir).
     */
    private String mapLanguageToModelFolder(String langCode) {
        return switch (langCode) {
            case "hi-IN", "hi" -> "model-hi";
            case "te-IN", "te" -> "model-te";
            case "en-US", "en" -> "model-en";
            default          -> "model-in";
        };
    }

    /**
     * Retrieve or load a Vosk model for the given language.
     */
    private Model getModelForLanguage(String sourceLang) {
        String folderName = mapLanguageToModelFolder(sourceLang);
        return modelCache.computeIfAbsent(folderName, key -> {
            Model loaded = loadModel(key, true);
            return loaded != null ? loaded : fallbackModel;
        });
    }

    /**
     * Load a Vosk model from a subdirectory of modelsDir.
     */
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

    // ========================
    // BATCH TRANSCRIPTION (existing functionality)
    // ========================
    /**
     * Transcribe an entire audio stream (batch mode).
     * Suitable for REST file uploads.
     */
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

    // ========================
    // REAL-TIME INCREMENTAL RECOGNITION (WebSocket)
    // ========================

    /**
     * Start a new speech recognition session.
     * @return session ID
     */
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

    /**
     * Process an audio chunk from an active session.
     * Returns a JSON string with keys:
     *   "text"   - current partial/final text
     *   "final"  - true if this is the final result (user stopped speaking)
     */
    public String processChunk(String sessionId, byte[] audioBytes, int numBytes) throws Exception {
        Recognizer recognizer = activeSessions.get(sessionId);
        if (recognizer == null) {
            throw new RuntimeException("No active speech session for ID: " + sessionId);
        }
        if (numBytes > 0) {
            recognizer.acceptWaveForm(audioBytes, numBytes);
        }
        // Get partial result (non‑final)
        String partial = recognizer.getPartialResult();
        JsonNode partialNode = objectMapper.readTree(partial);
        String partialText = partialNode.path("partial").asText("");

        boolean finalResult = false;
        // Check if we have a final result (e.g., after silence)
        // Vosk returns final result when acceptWaveForm returns true;
        // but in our loop we simply return what we have. We'll rely on client to signal stop.
        // Alternative: we can detect final via recognizer.getResult() but only after full stream.
        // For real-time, we'll keep returning partial; final will be handled when stopSession is called.

        // For simplicity, always return as partial until stopSession.
        // This ensures "real‑time" feel.
        return objectMapper.writeValueAsString(Map.of("text", partialText, "final", false));
    }

    /**
     * Stop a session and return the final recognized text.
     */
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

    // Cleanup on bean destroy
    @PreDestroy
    public void cleanup() {
        logger.info("Shutting down SpeechService. Closing all active sessions and models...");
        for (Recognizer rec : activeSessions.values()) {
            try { rec.close(); } catch (Exception ignored) {}
        }
        activeSessions.clear();
        modelCache.values().forEach(Model::close);
        if (fallbackModel != null && !modelCache.containsValue(fallbackModel)) {
            fallbackModel.close();
        }
    }
}