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

    // Java 17 Records for clean, immutable Data Transfer Objects (DTOs)
    public record RegisterRequest(String username, String password, String securityQuestion, String securityAnswer) {}
    public record ResetRequest(String username, String securityAnswer, String newPassword) {}

    /**
     * Register a new user via JSON payload.
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {

        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required"));
        }
        if (request.password() == null || request.password().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 4 characters"));
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username is already taken"));
        }

        String hashedAnswer = passwordEncoder.encode(request.securityAnswer().trim().toLowerCase());

        AppUser newUser = new AppUser(
                request.username().trim(),
                passwordEncoder.encode(request.password()),
                request.securityQuestion().trim(),
                hashedAnswer
        );

        userRepository.save(newUser);
        logger.info("New user registered: {}", request.username());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully"));
    }

    /**
     * Reset password via JSON payload.
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(@RequestBody ResetRequest request) {

        if (request.newPassword() == null || request.newPassword().length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 4 characters"));
        }

        Optional<AppUser> userOptional = userRepository.findByUsername(request.username());
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        AppUser user = userOptional.get();
        if (user.getSecurityAnswer() == null ||
                !passwordEncoder.matches(request.securityAnswer().trim().toLowerCase(), user.getSecurityAnswer())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Incorrect security answer"));
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        logger.info("Password reset for user: {}", request.username());

        return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
    }

    @GetMapping("/check-user")
    public ResponseEntity<?> checkUser(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}