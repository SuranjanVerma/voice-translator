package com.translator.language_translator.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "translation_records")
public class TranslationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String sourceLang;
    private String targetLang;

    @Column(columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String translatedText;

    private LocalDateTime timestamp;

    public TranslationRecord() {
        this.timestamp = LocalDateTime.now();
    }

    public TranslationRecord(String username, String sourceLang, String targetLang, String originalText, String translatedText) {
        this.username = username;
        this.sourceLang = sourceLang;
        this.targetLang = targetLang;
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getSourceLang() { return sourceLang; }
    public void setSourceLang(String sourceLang) { this.sourceLang = sourceLang; }

    public String getTargetLang() { return targetLang; }
    public void setTargetLang(String targetLang) { this.targetLang = targetLang; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public String getTranslatedText() { return translatedText; }
    public void setTranslatedText(String translatedText) { this.translatedText = translatedText; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}