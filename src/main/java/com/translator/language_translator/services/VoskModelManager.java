package com.translator.language_translator.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import jakarta.annotation.PreDestroy;

@Service
public class VoskModelManager {

    private final ConcurrentHashMap<String, Model> loadedModels = new ConcurrentHashMap<>();

    // Dynamically inject the directory from application.properties or environment variables
    @Value("${vosk.model.dir:src/main/resources/vosk-model}")
    private String modelBaseDir;

    public synchronized Model getModel(String languageCode) {
        if (loadedModels.containsKey(languageCode)) {
            return loadedModels.get(languageCode);
        }

        // Unload previous models to prevent memory exhaustion on Render's free tier
        clearAllModels();

        try {
            Model newModel;
            // File.separator automatically adjusts to '/' on Linux and '\' on Windows
            String basePath = modelBaseDir + File.separator;
            String targetPath;

            switch (languageCode) {
                case "en-IN":
                    targetPath = basePath + "model-in";
                    break;
                case "hi-IN":
                    targetPath = basePath + "model-hi";
                    break;
                case "en-US":
                default:
                    targetPath = basePath + "model-en";
                    break;
            }

            System.out.println("Attempting to load Vosk model from path: " + targetPath);
            newModel = new Model(targetPath);
            loadedModels.put(languageCode, newModel);
            return newModel;

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to load model for language [" + languageCode + "]: " + e.getMessage());
            return null;
        }
    }

    public void clearAllModels() {
        System.out.println("Aggressively clearing old speech models from memory...");

        loadedModels.forEach((key, model) -> {
            if (model != null) {
                try {
                    model.close(); // Frees the C++ Native Memory
                } catch (Exception e) {
                    System.err.println("Error closing model: " + e.getMessage());
                }
            }
        });
        loadedModels.clear();

        // Force Java to clean up the proxy objects immediately
        System.gc();

        // Give the OS a tiny fraction of a second to reclaim the RAM before loading the next one
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
    }

    @PreDestroy
    public void cleanup() {
        clearAllModels();
    }
}