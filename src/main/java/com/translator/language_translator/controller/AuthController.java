package com.translator.language_translator.controller;

import com.translator.language_translator.model.AppUser;
import com.translator.language_translator.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Register a new user. Security answer is stored hashed (lowercased before hashing for case‑insensitive comparison).
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String securityQuestion,
            @RequestParam String securityAnswer) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        if (password == null || password.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 4 characters"));
        }

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username is already taken"));
        }

        // Hash the security answer for storage (case‑insensitive)
        String hashedAnswer = passwordEncoder.encode(securityAnswer.trim().toLowerCase());

        // Use the constructor without role (role defaults to "USER")
        AppUser newUser = new AppUser(
                username.trim(),
                passwordEncoder.encode(password),
                securityQuestion.trim(),
                hashedAnswer
        );

        userRepository.save(newUser);
        logger.info("New user registered: {}", username);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully"));
    }

    /**
     * Reset password using the security answer.
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(
            @RequestParam String username,
            @RequestParam String securityAnswer,
            @RequestParam String newPassword) {

        if (newPassword == null || newPassword.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 4 characters"));
        }

        Optional<AppUser> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        AppUser user = userOptional.get();
        if (user.getSecurityAnswer() == null ||
                !passwordEncoder.matches(securityAnswer.trim().toLowerCase(), user.getSecurityAnswer())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Incorrect security answer"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("Password reset for user: {}", username);

        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    /**
     * Check if a username already exists (for frontend pre‑validation).
     */
    @GetMapping("/check-user")
    public ResponseEntity<?> checkUser(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}