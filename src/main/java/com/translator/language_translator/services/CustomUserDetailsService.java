package com.translator.language_translator.services;

import com.translator.language_translator.model.AppUser;
import com.translator.language_translator.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.warn("Login attempt for unknown user: {}", username);
                    return new UsernameNotFoundException("User not found");
                });

        // Ensure role is never null (though entity default handles it, belt and suspenders)
        String role = appUser.getRole() != null ? appUser.getRole() : "USER";

        logger.debug("User '{}' authenticated with role '{}'", username, role);

        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPassword())
                .roles(role)   // Spring Security prefixes with "ROLE_"
                .build();
    }
}