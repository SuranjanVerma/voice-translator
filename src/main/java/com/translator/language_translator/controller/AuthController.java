package com.translator.language_translator.controller;

import com.translator.language_translator.model.AppUser;
import com.translator.language_translator.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> registerUser(@RequestParam String username, @RequestParam String password) {

        // 1. Check if username is taken
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        // 2. Encrypt the password and save to Database
        AppUser newUser = new AppUser(username, passwordEncoder.encode(password));
        userRepository.save(newUser);

        return ResponseEntity.ok("User registered successfully!");
    }
}