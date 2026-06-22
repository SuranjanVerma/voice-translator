package com.translator.language_translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF: enabled for browser safety, but ignored for REST API endpoints
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // makes token accessible to JS if needed
                        .ignoringRequestMatchers("/api/**", "/ws/**")   // safe because APIs use separate auth (JWT or session cookie)
                )

                // 2. Endpoint access rules
                .authorizeHttpRequests(auth -> auth
                        // Public pages & endpoints
                        .requestMatchers(
                                "/login.html", "/login",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico",
                                "/error",
                                "/api/auth/**"       // registration & login API
                        ).permitAll()
                        // WebSocket endpoint (must be authenticated – handshake uses session cookie)
                        .requestMatchers("/ws/**").authenticated()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )

                // 3. Custom form login
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/index.html", true)
                        .failureUrl("/login.html?error=true")
                        .permitAll()
                )

                // 4. Logout
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

    /**
     * CORS configuration – adjust allowed origins as needed.
     * On Render, frontend and backend are on the same domain, so CORS is not
     * required in production, but this bean enables local development on different ports.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:8089", "http://localhost:3000")); // add your dev origins
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}