package com.translator.language_translator.controller;

import com.translator.language_translator.model.AppUser;
import com.translator.language_translator.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map; // <-- REQUIRED FOR THE JSON RESPONSE
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String securityQuestion,
            @RequestParam String securityAnswer) {

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        String hashedAnswer = passwordEncoder.encode(securityAnswer.toLowerCase());

        AppUser newUser = new AppUser(username, passwordEncoder.encode(password), securityQuestion, hashedAnswer);
        userRepository.save(newUser);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/reset")
    public ResponseEntity<?> resetPassword(
            @RequestParam String username,
            @RequestParam String securityAnswer,
            @RequestParam String newPassword) {

        Optional<AppUser> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Error: User not found.");
        }

        AppUser user = userOptional.get();

        if (user.getSecurityAnswer() == null || !passwordEncoder.matches(securityAnswer.toLowerCase(), user.getSecurityAnswer())) {
            return ResponseEntity.badRequest().body("Error: Incorrect security answer.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok("Password reset successfully! You can now log in.");
    }

    // NEW ENDPOINT: Safely checks if a user exists for the frontend Pre-Login check
    @GetMapping("/check-user")
    public ResponseEntity<?> checkUser(@RequestParam String username) {
        boolean exists = userRepository.findByUsername(username).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists));
    }
}