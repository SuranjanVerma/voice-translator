package com.translator.language_translator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    // Security fields for password recovery
    private String securityQuestion;
    private String securityAnswer;

    private String role = "USER";

    public AppUser(String username, String password, String securityQuestion, String securityAnswer) {
        this.username = username;
        this.password = password;
        this.securityQuestion = securityQuestion;
        // Save the answer in lowercase to prevent case-sensitive login errors later
        this.securityAnswer = securityAnswer != null ? securityAnswer.toLowerCase() : null;
    }
}