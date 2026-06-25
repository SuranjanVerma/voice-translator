package com.translator.language_translator.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import java.io.File;
import jakarta.annotation.PreDestroy;

@Service
public class VoskModelManager {

    private Model activeModel = null;
    private String activeLanguage = null;

    @Value("${vosk.model.dir:src/main/resources/vosk-model}")
    private String modelBaseDir;

    public synchronized Model getModel(String languageCode) {
        if (activeLanguage != null && activeLanguage.equals(languageCode) && activeModel != null) {
            return activeModel;
        }

        System.out.println("Language switch detected. Clearing old memory...");
        if (activeModel != null) {
            try {
                activeModel.close();
            } catch (Exception ignored) {}
            activeModel = null;
        }

        System.gc();

        try {
            String basePath = modelBaseDir + File.separator;
            String targetPath;

            // en-IN has been completely removed.
            switch (languageCode) {
                case "hi-IN": targetPath = basePath + "model-hi"; break;
                case "en-US":
                default:      targetPath = basePath + "model-en"; break;
            }

            System.out.println("Loading Vosk Model into RAM for: " + languageCode);
            activeModel = new Model(targetPath);
            activeLanguage = languageCode;

            return activeModel;

        } catch (Exception e) {
            System.err.println("CRITICAL: Failed to load model for [" + languageCode + "]: " + e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (activeModel != null) {
            try { activeModel.close(); } catch (Exception ignored) {}
        }
    }
}