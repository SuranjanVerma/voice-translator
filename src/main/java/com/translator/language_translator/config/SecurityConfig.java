package com.translator.language_translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // CRITICAL: We added "/login.html" here so users can actually see the page before logging in!
                        .requestMatchers("/css/**", "/js/**", "/api/auth/register", "/login.html").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        // Tell Spring to use our custom HTML file
                        .loginPage("/login.html")
                        // Tell Spring to listen for POST requests at this URL to verify passwords
                        .loginProcessingUrl("/login")
                        // If the password is correct, send them to the Translator page!
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        // When they log out, send them back to the custom login page
                        .logoutSuccessUrl("/login.html")
                        .permitAll()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    // This is the engine that encrypts passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}