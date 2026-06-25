package com.translator.language_translator.services;

import org.springframework.stereotype.Service;
import org.vosk.Model;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoskModelManager {

    // Track loaded models dynamically
    private final ConcurrentHashMap<String, Model> loadedModels = new ConcurrentHashMap<>();

    /**
     * Retrieves an existing model or loads it on-the-fly if missing.
     * @param languageCode "en" for US English, "en-in" for Indian English, "hi" for Hindi
     */
    public synchronized Model getModel(String languageCode) {
        // If the model is already in RAM, just use it!
        if (loadedModels.containsKey(languageCode)) {
            return loadedModels.get(languageCode);
        }

        // Otherwise, clear the old model to protect our 512MB limit
        clearAllModels();

        try {
            Model newModel;
            // We match the exact 'value' from your index.html <select> dropdown
            switch (languageCode) {
                case "en-IN":
                    System.out.println("Loading Indian English Model...");
                    // Change this to your actual absolute path
                    newModel = new Model("C:\\Users\\KIIT\\Desktop\\language_translator\\src\\main\\resources\\vosk-model\\model-in");
                    break;
                case "hi-IN":
                    System.out.println("Loading Hindi Model...");
                    newModel = new Model("C:\\Users\\KIIT\\Desktop\\language_translator\\src\\main\\resources\\vosk-model\\model-hi");
                    break;
                case "en-US":
                default:
                    System.out.println("Loading US English Model...");
                    newModel = new Model("C:\\Users\\KIIT\\Desktop\\language_translator\\src\\main\\resources\\vosk-model\\model-en");
                    break;
            }
            loadedModels.put(languageCode, newModel);
            return newModel;
        } catch (Exception e) {
            System.err.println("Failed to load model for language: " + languageCode + " -> " + e.getMessage());
            return null;
        }
    }

    private void clearAllModels() {
        System.out.println("Clearing old speech models from memory to free up RAM...");
        loadedModels.forEach((key, model) -> {
            if (model != null) {
                model.close(); // Explicitly frees native C++ memory
            }
        });
        loadedModels.clear();
    }
}