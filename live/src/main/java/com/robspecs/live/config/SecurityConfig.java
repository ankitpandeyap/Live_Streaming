//// src/main/java/com/robspecs/live/config/SecurityConfig.java
//package com.robspecs.live.config;
//
//import static org.springframework.security.config.Customizer.withDefaults;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
//import org.springframework.security.config.annotation.web.builders.HttpSecurity;
//import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
//import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
//import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.security.web.SecurityFilterChain;
//import org.springframework.security.web.access.AccessDeniedHandler;
//import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // Crucial for filter ordering
//
//import com.robspecs.live.security.CustomUserDetailsService;
//import com.robspecs.live.security.HlsTokenValidationFilter;
//import com.robspecs.live.security.JWTAuthenticationEntryPoint;
//import com.robspecs.live.security.JWTAuthenticationFilter; // Your custom filter handling /api/auth/login
//import com.robspecs.live.security.JWTRefreshFilter;
//import com.robspecs.live.security.JWTValidationFilter; // Your custom filter for general JWT validation
//import com.robspecs.live.service.TokenBlacklistService;
//import com.robspecs.live.utils.JWTUtils;
//
//import jakarta.servlet.http.HttpServletResponse;
//
//@Configuration
//@EnableWebSecurity
//public class SecurityConfig {
//
//    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
//
//    private final JWTAuthenticationEntryPoint jwtAuthenticationEntryPoint;
//
//    public SecurityConfig(JWTAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
//        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
//        logger.debug("SecurityConfig initialized. JWTAuthenticationEntryPoint injected: {}", jwtAuthenticationEntryPoint.getClass().getName());
//    }
//
//    @Bean
//    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
//        AuthenticationManager authenticationManager = configuration.getAuthenticationManager();
//        logger.info("AuthenticationManager bean created.");
//        return authenticationManager;
//    }
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(
//            AuthenticationManager authenticationManager,
//            HttpSecurity http,
//            JWTUtils jwtUtils,
//            CustomUserDetailsService customUserDetailsService,
//            TokenBlacklistService tokenService) throws Exception {
//
//        logger.info("Configuring SecurityFilterChain.");
//
//        // Instantiate your custom filters
//        // JWTAuthenticationFilter handles the /api/auth/login endpoint
//        JWTAuthenticationFilter authFilter = new JWTAuthenticationFilter(authenticationManager, jwtUtils);
//        logger.debug("JWTAuthenticationFilter instance created.");
//
//        // JWTValidationFilter handles general JWT validation from Authorization header for protected APIs
//        JWTValidationFilter validationFilter = new JWTValidationFilter(authenticationManager, jwtUtils,
//                customUserDetailsService, tokenService);
//        logger.debug("JWTValidationFilter instance created.");
//
//        // JWTRefreshFilter handles the /api/auth/refresh endpoint specifically
//        JWTRefreshFilter jwtRefreshFilter = new JWTRefreshFilter(authenticationManager, jwtUtils, customUserDetailsService, tokenService);
//        logger.debug("JWTRefreshFilter instance created.");
//
//        // HlsTokenValidationFilter handles JWT tokens in query parameters for HLS/VOD streaming
//        HlsTokenValidationFilter hlsFilter = new HlsTokenValidationFilter(jwtUtils);
//        logger.debug("HlsTokenValidationFilter instance created.");
//
//        return http
//                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for stateless API
//                .cors(withDefaults())
//                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Use stateless sessions
//                .exceptionHandling(exception -> {
//                    exception.authenticationEntryPoint(jwtAuthenticationEntryPoint);
//                    logger.debug("AuthenticationEntryPoint set to: {}", jwtAuthenticationEntryPoint.getClass().getName());
//                    exception.accessDeniedHandler(accessDeniedHandler());
//                    logger.debug("AccessDeniedHandler set.");
//                })
//                .authorizeHttpRequests(auth -> {
//                    auth.requestMatchers(
//                            "/api/auth/login",
//                            "/api/auth/refresh",
//                            "/api/auth/signup",
//                            "/api/auth/register",
//                            "/api/auth/otp/verify",
//                            "/api/auth/otp/request",
//                            "/api/auth/forgot-password",
//                            "/api/auth/reset-password",
//                            "/api/videos/stream/**",     // HLS/VOD playback (token in query param, handled by HlsTokenValidationFilter)
//                            "/raw-media-ingest/**",     // Live stream raw data ingestion (WebSocket, typically unauthenticated for initial connection)
//                            "/hls/**"                   // Static HLS files served directly by Spring (if applicable)
//                    ).permitAll();
//                    logger.debug("Public URLs configured: /api/auth/**, /api/videos/stream/**, /raw-media-ingest/**, /hls/** are permitted.");
//
//                    auth.anyRequest().authenticated();
//                    logger.debug("All other requests require authentication.");
//                })
//                // Add custom filters to the chain in the correct order
//                // HlsTokenValidationFilter needs to run very early for stream URLs
//                .addFilterBefore(hlsFilter, UsernamePasswordAuthenticationFilter.class)
//                // JWTAuthenticationFilter handles the /api/auth/login endpoint
//                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
//                // JWTValidationFilter handles general API JWT validation, after authentication attempts
//                .addFilterAfter(validationFilter, JWTAuthenticationFilter.class)
//                // JWTRefreshFilter specifically handles /api/auth/refresh
//                .addFilterAfter(jwtRefreshFilter, JWTValidationFilter.class)
//                .build();
//    }
//
//    @Bean
//    public static PasswordEncoder passwordEncoder() {
//        PasswordEncoder encoder = new BCryptPasswordEncoder();
//        logger.info("PasswordEncoder bean (BCryptPasswordEncoder) created.");
//        return encoder;
//    }
//
//    @Bean
//    public AccessDeniedHandler accessDeniedHandler() {
//        logger.info("AccessDeniedHandler bean created.");
//        return (request, response, accessDeniedException) -> {
//            logger.warn("Access denied for request URI: {} - Message: {}", request.getRequestURI(), accessDeniedException.getMessage());
//            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
//            response.setContentType("application/json");
//            response.getWriter().write("{\"error\": \"Access Denied!\"}");
//            logger.debug("Sent 403 Forbidden response for access denied.");
//        };
//    }
//}

// src/main/java/com/robspecs/live/config/SecurityConfig.java
package com.robspecs.live.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

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
