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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpeechService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechService.class);
    private static final float SAMPLE_RATE = 16000.0f;

    private final String modelDir;
    private final ConcurrentHashMap<String, Model> modelCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private Model fallbackModel;

    public SpeechService(@Value("${vosk.model.dir:/vosk-models}") String modelDir, ObjectMapper objectMapper) {
        this.modelDir = modelDir;
        this.objectMapper = objectMapper;

        this.fallbackModel = loadModel("model-in", false);
        if (fallbackModel == null) {
            logger.error("CRITICAL: No fallback model loaded. Speech recognition will fail for missing models.");
        }
    }

    private Model getModelForLanguage(String sourceLang) {
        return modelCache.computeIfAbsent(sourceLang, lang -> {
            String folderName = switch (lang) {
                case "hi-IN" -> "model-hi";
                case "te-IN" -> "model-te";
                case "en-US" -> "model-en";
                default      -> "model-in";
            };

            Model loaded = loadModel(folderName, true);
            return loaded != null ? loaded : fallbackModel;
        });
    }

    private Model loadModel(String folderName, boolean log) {
        File folder = new File(modelDir, folderName);
        if (!folder.exists() || !folder.isDirectory() || folder.list() == null || folder.list().length == 0) {
            if (log) logger.warn("Vosk model folder {} missing or empty.", folder.getAbsolutePath());
            return null;
        }

        try {
            if (log) logger.info("Successfully loaded Vosk model: {}", folder.getAbsolutePath());
            return new Model(folder.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to load Vosk model from {}: {}", folder.getAbsolutePath(), e.getMessage());
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
                recognizer.acceptWaveForm(buffer, bytesRead);
            }

            String jsonResult = recognizer.getFinalResult();
            JsonNode rootNode = objectMapper.readTree(jsonResult);
            return rootNode.has("text") ? rootNode.get("text").asText() : "";
        }
    }

    @PreDestroy
    public void cleanupModels() {
        logger.info("Shutting down SpeechService. Releasing native Vosk models from memory...");
        modelCache.values().forEach(Model::close);
        if (fallbackModel != null && !modelCache.containsValue(fallbackModel)) {
            fallbackModel.close();
        }
    }
}