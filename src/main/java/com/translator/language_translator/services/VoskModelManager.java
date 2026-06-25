package com.translator.language_translator.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import jakarta.annotation.PreDestroy;

@Service
public class VoskModelManager {

    // We only cache the heavy Dictionaries (Models), NOT the ears (Recognizers)
    private final ConcurrentHashMap<String, Model> loadedModels = new ConcurrentHashMap<>();

    @Value("${vosk.model.dir:src/main/resources/vosk-model}")
    private String modelBaseDir;

    public synchronized Model getModel(String languageCode) {
        // 1. If the dictionary is already in RAM, share it!
        if (loadedModels.containsKey(languageCode)) {
            return loadedModels.get(languageCode);
        }

        try {
            String basePath = modelBaseDir + File.separator;
            String targetPath;

            switch (languageCode) {
                case "en-IN": targetPath = basePath + "model-in"; break;
                case "hi-IN": targetPath = basePath + "model-hi"; break;
                case "en-US":
                default:      targetPath = basePath + "model-en"; break;
            }

            System.out.println("Loading 40MB Vosk Model into RAM for: " + languageCode);
            Model newModel = new Model(targetPath);
            loadedModels.put(languageCode, newModel);

            return newModel;

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to load model for [" + languageCode + "]: " + e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Shutting down server, flushing native memory...");
        loadedModels.forEach((key, model) -> {
            if (model != null) {
                try { model.close(); } catch (Exception ignored) {}
            }
        });
        loadedModels.clear();
    }
}