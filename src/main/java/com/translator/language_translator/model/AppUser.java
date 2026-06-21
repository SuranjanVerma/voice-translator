package com.translator.language_translator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

@Data
@NoArgsConstructor
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

    // Optional explicit constructor (Lombok's @Data does not generate all-args)
    public AppUser(String username, String password, String role,
                   String securityQuestion, String securityAnswer) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.securityQuestion = securityQuestion;
        this.securityAnswer = securityAnswer;
    }

    public AppUser(String username, @Nullable String encode, String securityQuestion, String hashedAnswer) {
    }
}