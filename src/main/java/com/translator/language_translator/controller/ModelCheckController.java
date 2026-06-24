package com.translator.language_translator.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ModelCheckController {

    // ALIGNED FIX: Matches the exact zero-config fallback path used in SpeechService
    @Value("${vosk.model.dir:src/main/resources/vosk-model}")
    private String modelDir;

    @GetMapping("/api/debug/models")
    public List<String> listModels() {
        File dir = new File(modelDir);

        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();

            if (files == null || files.length == 0) {
                return List.of("Model directory is empty. Models will auto-download on first use.");
            }

            return Arrays.stream(files)
                    .filter(File::isDirectory)
                    // CLOUD FIX: Just verifies the folder has contents, rather than checking for strict heavy-model structures
                    .map(f -> {
                        boolean hasContents = f.list() != null && f.list().length > 0;
                        return f.getName() + (hasContents ? " (Ready/Installed)" : " (Empty - Will download on request)");
                    })
                    .collect(Collectors.toList());
        }

        return List.of("Model directory not found at " + modelDir + ". The system will create it and auto-download models on first use.");
    }
}