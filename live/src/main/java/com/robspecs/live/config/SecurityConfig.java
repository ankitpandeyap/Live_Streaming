package com.robspecs.live.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.robspecs.live.security.CustomUserDetailsService;
import com.robspecs.live.security.HlsTokenValidationFilter;
import com.robspecs.live.security.JWTAuthenticationEntryPoint;
import com.robspecs.live.security.JWTAuthenticationFilter;
import com.robspecs.live.security.JWTRefreshFilter;
import com.robspecs.live.security.JWTValidationFilter;
import com.robspecs.live.service.TokenBlacklistService;
import com.robspecs.live.utils.JWTUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;



import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final JWTAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JWTAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        logger.debug("SecurityConfig initialized. JWTAuthenticationEntryPoint injected: {}", jwtAuthenticationEntryPoint.getClass().getName());
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        AuthenticationManager authenticationManager = configuration.getAuthenticationManager();
        logger.info("AuthenticationManager bean created."); // Use INFO for bean creation
        return authenticationManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(AuthenticationManager authenticationManager, HttpSecurity http,
                                                     JWTUtils jwtUtils, CustomUserDetailsService customUserDetailsService,
                                                     TokenBlacklistService tokenService) throws Exception {

        logger.info("Configuring SecurityFilterChain.");

        // Instantiate your custom filters
        JWTAuthenticationFilter authFilter = new JWTAuthenticationFilter(authenticationManager, jwtUtils);
        logger.debug("JWTAuthenticationFilter instance created.");

        JWTValidationFilter validationFilter = new JWTValidationFilter(authenticationManager, jwtUtils,
                customUserDetailsService, tokenService);
        logger.debug("JWTValidationFilter instance created.");

        JWTRefreshFilter jwtRefreshFilter = new JWTRefreshFilter(authenticationManager, jwtUtils, customUserDetailsService, tokenService);
        logger.debug("JWTRefreshFilter instance created.");

        // New: Instantiate HlsTokenValidationFilter
        HlsTokenValidationFilter hlsFilter = new HlsTokenValidationFilter(jwtUtils); // <--- ADD THIS
        logger.debug("HlsTokenValidationFilter instance created."); // <--- ADD THIS

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> {
                    exception.authenticationEntryPoint(jwtAuthenticationEntryPoint);
                    logger.debug("AuthenticationEntryPoint set to: {}", jwtAuthenticationEntryPoint.getClass().getName());
                    exception.accessDeniedHandler(accessDeniedHandler());
                    logger.debug("AccessDeniedHandler set.");
                })
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/api/auth/login",
                            "/api/auth/refresh",
                            "/api/auth/signup",
                            "/api/auth/register",
                            "/api/auth/otp/verify",
                            "/api/auth/otp/request",
                            "/api/auth/forgot-password",
                            "/api/auth/reset-password",
                            "/api/videos/stream/**" // Consolidated into one entry
                    ).permitAll();
                    // Consolidated log message for clarity and accuracy
                    logger.debug("Public URLs configured: /api/auth/** endpoints and /api/videos/stream/** are permitted (HLS handled by HlsTokenValidationFilter).");

                    auth.anyRequest().authenticated();
                    logger.debug("All other requests require authentication.");
                })
                // Add custom filters to the chain
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
                // New: Add HlsTokenValidationFilter before the general JWTValidationFilter
                .addFilterBefore(hlsFilter, JWTAuthenticationFilter.class) // <--- ADD THIS LINE (Before other JWT filters)
                .addFilterAfter(validationFilter, JWTAuthenticationFilter.class)
                .addFilterAfter(jwtRefreshFilter, JWTValidationFilter.class)
                .build();
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        logger.info("PasswordEncoder bean (BCryptPasswordEncoder) created."); // Use INFO for bean creation
        return encoder;
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        logger.info("AccessDeniedHandler bean created."); // Use INFO for bean creation
        return (request, response, accessDeniedException) -> {
            logger.warn("Access denied for request URI: {} - Message: {}", request.getRequestURI(), accessDeniedException.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Access Denied!\"}");
            logger.debug("Sent 403 Forbidden response for access denied.");
        };
    }
}