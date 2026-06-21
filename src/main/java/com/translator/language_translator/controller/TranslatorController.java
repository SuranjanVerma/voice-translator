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

    @PostMapping("/translate")
    public ResponseEntity<?> handleTranslation(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("sourceLang") String sourceLang,
            @RequestParam("targetLang") String targetLang,
            Authentication auth) {

        // Use try-with-resources to ensure the InputStream is properly closed
        try (InputStream audioStream = audio.getInputStream()) {

            // *** The crucial fix: pass sourceLang to transcribe ***
            String originalText = speechService.transcribe(audioStream, sourceLang);

            if (originalText == null || originalText.isBlank()) {
                return ResponseEntity.badRequest().body("No speech detected.");
            }

            String translatedText = translationService.translate(originalText, sourceLang, targetLang);

            String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
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
                    .body("System Error: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication auth) {
        // If Spring Security is configured tightly, it might block unauthenticated
        // requests before they even hit this controller, but this is a good safety net.
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Error: User must be logged in to view history.");
        }

        try {
            List<TranslationRecord> history = repository.findByUsernameOrderByTimestampDesc(auth.getName());
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            logger.error("Error fetching translation history for user: {}", auth.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching history.");
        }
    }
}