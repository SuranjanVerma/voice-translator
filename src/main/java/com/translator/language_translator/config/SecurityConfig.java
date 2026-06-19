package com.translator.language_translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF for our API endpoints so POST requests don't get blocked
                .csrf(csrf -> csrf.disable())

                // 2. Configure which pages are public and which are private
                .authorizeHttpRequests(auth -> auth
                        // CRITICAL: Make the login page, error page, and ALL /api/auth/ endpoints public!
                        .requestMatchers("/login.html", "/login", "/api/auth/**", "/error").permitAll()
                        // Everything else (like the translator page) requires the user to be logged in
                        .anyRequest().authenticated()
                )

                // 3. Tell Spring Security to use our custom HTML login page
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/index.html", true)
                        .failureUrl("/login.html?error=true")
                        .permitAll()
                )

                // 4. Configure logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login.html?logout=true")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}