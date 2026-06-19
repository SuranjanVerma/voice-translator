package com.translator.language_translator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "translation_history")
public class TranslationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String sourceLanguage;
    private String targetLanguage;

    @Column(columnDefinition = "TEXT")
    private String originalText;

    @Column(columnDefinition = "TEXT")
    private String translatedText;

    //Hibernate will automatically stamp the exact time this is saved to MySQL!
    @CreationTimestamp
    @Column(updatable = false) // Ensures the timestamp is never accidentally changed later
    private LocalDateTime timestamp;

    public TranslationRecord(String username, String sourceLanguage, String targetLanguage, String originalText, String translatedText) {
        this.username = username;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.originalText = originalText;
        this.translatedText = translatedText;
    }
}