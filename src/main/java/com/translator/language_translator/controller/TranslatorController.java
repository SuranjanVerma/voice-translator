package com.translator.language_translator.controller;

import com.translator.language_translator.model.TranslationRecord;
import com.translator.language_translator.repository.TranslationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranslatorController {
    private final TranslationRepository repository;
    public TranslatorController(TranslationRepository repository) { this.repository = repository; }

    public record HistorySaveRequest(String sourceLang, String targetLang, String originalText, String translatedText) {}

    @PostMapping("/history/save")
    public ResponseEntity<?> saveHistory(@RequestBody HistorySaveRequest request, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return ResponseEntity.status(401).build();
        repository.save(new TranslationRecord(auth.getName(), request.sourceLang(), request.targetLang(), request.originalText(), request.translatedText()));
        return ResponseEntity.ok(Map.of("message", "Saved"));
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return ResponseEntity.status(401).build();
        // FIX: Using OrderByIdDesc to guarantee database compatibility
        return ResponseEntity.ok(repository.findByUsernameOrderByIdDesc(auth.getName()));
    }

    @DeleteMapping("/history")
    public ResponseEntity<?> clearHistory(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return ResponseEntity.status(401).build();
        repository.deleteByUsername(auth.getName());
        return ResponseEntity.ok(Map.of("message", "Cleared"));
    }
}