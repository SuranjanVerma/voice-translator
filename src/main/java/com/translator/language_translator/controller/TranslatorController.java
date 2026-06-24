package com.translator.language_translator.controller;

import com.translator.language_translator.model.TranslationRecord;
import com.translator.language_translator.repository.TranslationRepository;
import com.translator.language_translator.services.SpeechService;
import com.translator.language_translator.services.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranslatorController {

    private static final Logger logger = LoggerFactory.getLogger(TranslatorController.class);

    private final SpeechService speechService;
    private final TranslationService translationService;
    private final TranslationRepository repository;

    public TranslatorController(SpeechService speechService, TranslationService translationService, TranslationRepository repository) {
        this.speechService = speechService;
        this.translationService = translationService;
        this.repository = repository;
    }

    // NEW: Java 17 Record to map the incoming JSON payload from the frontend
    public record HistorySaveRequest(String sourceLang, String targetLang, String originalText, String translatedText) {}

    // NEW: Bulletproof REST endpoint to save history with guaranteed Spring Security Context
    @PostMapping("/history/save")
    public ResponseEntity<?> saveHistory(@RequestBody HistorySaveRequest request, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        TranslationRecord record = new TranslationRecord(
                auth.getName(), // Guaranteed to be the logged-in user
                request.sourceLang(),
                request.targetLang(),
                request.originalText(),
                request.translatedText()
        );
        repository.save(record);

        return ResponseEntity.ok(Map.of("message", "History saved successfully"));
    }

    @PostMapping("/translate")
    public ResponseEntity<?> handleTranslation(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("sourceLang") String sourceLang,
            @RequestParam("targetLang") String targetLang,
            Authentication auth) {

        if (audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Audio file is empty."));
        }

        String contentType = audio.getContentType();
        String filename = audio.getOriginalFilename();
        boolean isWav = (contentType != null && contentType.contains("wav")) ||
                (filename != null && filename.toLowerCase().endsWith(".wav"));

        if (!isWav) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .body(Map.of("error", "Invalid format. Please upload a 16kHz mono WAV file."));
        }

        try (InputStream audioStream = audio.getInputStream()) {
            String originalText = speechService.transcribe(audioStream, sourceLang);

            if (originalText == null || originalText.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No speech detected."));
            }

            String translatedText = translationService.translate(originalText, sourceLang, targetLang);

            String username = (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser"))
                    ? auth.getName()
                    : "anonymous";

            TranslationRecord record = new TranslationRecord(username, sourceLang, targetLang, originalText, translatedText);
            repository.save(record);

            return ResponseEntity.ok(Map.of(
                    "original", originalText,
                    "translated", translatedText
            ));

        } catch (Exception e) {
            logger.error("Error during speech translation process for user: {}",
                    (auth != null ? auth.getName() : "anonymous"), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "System Error: " + e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User must be logged in to view history."));
        }

        try {
            List<TranslationRecord> history = repository.findByUsernameOrderByTimestampDesc(auth.getName());
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error fetching translation history for user: {}", auth.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching history."));
        }
    }
}