package com.translator.language_translator.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column
    private String role;

    @Column
    private String securityQuestion;

    @Column
    private String securityAnswer;

    // Default constructor (Replaces Lombok's @NoArgsConstructor - Required by JPA)
    public AppUser() {
    }

    // Explicit constructor
    public AppUser(String username, String password, String role,
                   String securityQuestion, String securityAnswer) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.securityQuestion = securityQuestion;
        this.securityAnswer = securityAnswer;
    }

    // Second explicit constructor
    public AppUser(String username, @Nullable String encode, String securityQuestion, String hashedAnswer) {
        this.username = username;
        this.password = encode;
        this.securityQuestion = securityQuestion;
        this.securityAnswer = hashedAnswer;
    }

    // --- Explicit Getters and Setters (Replaces Lombok's @Data) ---

    public void setId(Long id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setSecurityQuestion(String securityQuestion) {
        this.securityQuestion = securityQuestion;
    }

    public void setSecurityAnswer(String securityAnswer) {
        this.securityAnswer = securityAnswer;
    }
}