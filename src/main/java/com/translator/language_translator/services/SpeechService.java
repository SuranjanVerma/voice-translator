package com.translator.language_translator.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpeechService {

    private final String modelDir;
    private final ConcurrentHashMap<String, Model> modelCache = new ConcurrentHashMap<>();
    private Model fallbackModel;

    // /vosk-models is the default, overridable via VOSK_MODEL_DIR env variable
    public SpeechService(@Value("${vosk.model.dir:/vosk-models}") String modelDir) {
        this.modelDir = modelDir;
        this.fallbackModel = loadModel("model-in", false);
        if (fallbackModel == null) {
            System.err.println("⚠️ CRITICAL: No fallback model loaded. Speech recognition will not work.");
        }
    }

    private Model getModelForLanguage(String sourceLang) {
        return modelCache.computeIfAbsent(sourceLang, lang -> {
            String folderName = switch (lang) {
                case "hi-IN" -> "model-hi";
                case "te-IN" -> "model-te";
                case "en-US" -> "model-en";
                default      -> "model-in";   // en-IN or unknown → fallback
            };

            Model loaded = loadModel(folderName, true);
            return loaded != null ? loaded : fallbackModel;
        });
    }

    private Model loadModel(String folderName, boolean log) {
        File folder = new File(modelDir, folderName);
        if (!folder.exists() || !folder.isDirectory() || folder.list().length == 0) {
            if (log) System.out.println("⚠️ Vosk model folder " + folder.getAbsolutePath() + " missing or empty.");
            return null;
        }

        try {
            if (log) System.out.println("✅ Loading Vosk model: " + folder.getAbsolutePath());
            return new Model(folder.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("⚠️ Failed to load Vosk model from " + folder.getAbsolutePath() + ": " + e.getMessage());
            return null;
        }
    }

    public String transcribe(InputStream audio, String sourceLang) throws Exception {
        Model model = getModelForLanguage(sourceLang);
        if (model == null) {
            throw new RuntimeException("No Vosk model available for transcription.");
        }

        try (Recognizer recognizer = new Recognizer(model, 16000.0f)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audio.read(buffer)) >= 0) {
                recognizer.acceptWaveForm(buffer, bytesRead);
            }

            String jsonResult = recognizer.getFinalResult();
            JsonNode rootNode = new ObjectMapper().readTree(jsonResult);
            return rootNode.has("text") ? rootNode.get("text").asText() : "";
        }
    }
}