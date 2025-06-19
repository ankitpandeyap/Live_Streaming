// src/main/java/com/robspecs/live/config/SecurityConfig.java
package com.robspecs.live.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity // Enables Spring Security's web security support
public class SecurityConfig {

  

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Add your CorsFilter at an appropriate position if it's a custom one
            // This ensures CORS is handled before authentication/authorization
            .cors(withDefaults())
            // Disable CSRF for WebSocket connections as they don't use CSRF tokens in the same way
            .csrf(AbstractHttpConfigurer::disable)
            // Configure authorization for HTTP requests
            .authorizeHttpRequests(authorize -> authorize
                // Allow WebSocket handshake requests to /live-stream/** without authentication
                // This is crucial for your WebSocket connection
                .requestMatchers("/live-stream/**").permitAll()
                // You will add specific security rules for other API endpoints here later
                // For now, if you have other endpoints, you might allow them or secure them as needed
                // Example: .requestMatchers("/api/auth/**").permitAll() // For login/registration
                // .anyRequest().authenticated() // All other requests require authentication
                .anyRequest().permitAll() // Temporarily permit all for easy testing, will secure later
            );
            // If you're building an API, you typically wouldn't use formLogin() or httpBasic()
            // .formLogin(AbstractHttpConfigurer::disable) // Disable default form login
            // .httpBasic(AbstractHttpConfigurer::disable); // Disable default HTTP Basic auth

        return http.build();
    }
}