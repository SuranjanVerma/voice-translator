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

    @Value("${vosk.model.dir:/opt/vosk-models}")
    private String modelDir;

    @GetMapping("/api/debug/models")
    public List<String> listModels() {
        File dir = new File(modelDir);
        if (dir.exists() && dir.isDirectory()) {
            return Arrays.stream(dir.listFiles())
                    .filter(File::isDirectory)
                    .map(f -> f.getName() + " (" + (new File(f, "am/final.mdl").exists() ? "valid" : "invalid") + ")")
                    .collect(Collectors.toList());
        }
        return List.of("Model directory not found: " + modelDir);
    }
}