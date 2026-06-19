package com.translator.language_translator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data // Lombok automatically creates getters/setters
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // This will store the encrypted BCrypt hash

    private String role = "USER"; // Default role for new signups

    public AppUser(String username, String password) {
        this.username = username;
        this.password = password;
    }
}