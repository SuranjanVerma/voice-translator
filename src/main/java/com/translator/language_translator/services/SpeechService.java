package com.translator.language_translator.services;

import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;

@Service
public class SpeechService {

    private final Model model;


    public SpeechService() {
        try {
            this.model = new Model("src/main/resources/vosk-model/model-in");
        } catch (Exception e) {
            throw new RuntimeException("Vosk Model not found", e);
        }
    }

    public String transcribe(InputStream audio) throws Exception {
        try (Recognizer recognizer = new Recognizer(model, 16000.0f)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audio.read(buffer)) >= 0) {
                recognizer.acceptWaveForm(buffer, bytesRead);
            }

            // Parse JSON to get text
            String jsonResult = recognizer.getFinalResult();
            JsonNode rootNode = new ObjectMapper().readTree(jsonResult);
            return rootNode.has("text") ? rootNode.get("text").asText() : "";
        }
    }
}
