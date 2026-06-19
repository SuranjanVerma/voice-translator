package com.translator.language_translator.services;

import com.translator.language_translator.model.AppUser;
import com.translator.language_translator.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Find the user in PostgresSQL
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // 2. Convert it into a Spring Security User
        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPassword()) // Spring Security will verify this hash automatically
                .roles(appUser.getRole())
                .build();
    }
}