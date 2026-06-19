package com.translator.language_translator.controller;

import com.translator.language_translator.model.TranslationRecord;
import com.translator.language_translator.repository.TranslationRepository;
import com.translator.language_translator.services.SpeechService;
import com.translator.language_translator.services.TranslationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TranslatorController {

    private final SpeechService speechService;
    private final TranslationService translationService;
    private final TranslationRepository repository;

    // Spring Boot automatically injects your services and database repository here
    public TranslatorController(SpeechService speechService, TranslationService translationService, TranslationRepository repository) {
        this.speechService = speechService;
        this.translationService = translationService;
        this.repository = repository;
    }

    // ==========================================
    // 1. THE TRANSLATION ENDPOINT
    // ==========================================
    @PostMapping("/translate")
    public ResponseEntity<?> handleTranslation(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("targetLang") String targetLang,
            Authentication auth) {

        try {
            // Step 1: Vosk listens to the audio and converts it to English text
            String originalText = speechService.transcribe(audio.getInputStream());
            if (originalText == null || originalText.isBlank()) {
                return ResponseEntity.badRequest().body("No speech detected.");
            }

            // Step 2: Send the English text to the internet to be translated
            String translatedText = translationService.translate(originalText, "en", targetLang);

            // Step 3: Securely identify who is logged in (Fallback to 'anonymous' just in case)
            String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";

            // Step 4: Save the transaction permanently in PostgreSQL
            TranslationRecord record = new TranslationRecord(username, "en", targetLang, originalText, translatedText);
            repository.save(record);

            // Step 5: Package the results into JSON and send them back to the browser
            return ResponseEntity.ok(Map.of(
                    "original", originalText,
                    "translated", translatedText
            ));

        } catch (Exception e) {
            e.printStackTrace(); // Prints the exact error to your Eclipse console for easy debugging
            return ResponseEntity.internalServerError().body("System Error: " + e.getMessage());
        }
    }

    // ==========================================
    // 2. THE HISTORY DASHBOARD ENDPOINT
    // ==========================================
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(Authentication auth) {

        // Security Check: Block access if the user's session expired
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body("Error: User must be logged in to view history.");
        }

        try {
            // Fetch only the logged-in user's history, sorted with the newest at the top
            List<TranslationRecord> history = repository.findByUsernameOrderByTimestampDesc(auth.getName());

            // Return the list to the Javascript fetch() request
            return ResponseEntity.ok(history);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error fetching history.");
        }
    }
}