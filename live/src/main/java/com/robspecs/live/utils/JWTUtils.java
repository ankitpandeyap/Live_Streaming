package com.robspecs.live.utils; // Changed package

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

@Component
public class JWTUtils {
    private final static Logger logger = LoggerFactory.getLogger(JWTUtils.class);

    // Main JWT Secret for authentication/refresh tokens
    private final String AUTH_SECRET_KEY_STRING;
    private final Key authKey;

    // Separate JWT Secret for HLS streaming tokens
    private final String HLS_SECRET_KEY_STRING;
    private final Key hlsKey;

    public JWTUtils(@Value("${jwt.secret}") String authSecretKeyString,
                    @Value("${hls.jwt.secret}") String hlsSecretKeyString) { // Constructor injection for both secrets
        this.AUTH_SECRET_KEY_STRING = authSecretKeyString;
        this.authKey = Keys.hmacShaKeyFor(this.AUTH_SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8));

        this.HLS_SECRET_KEY_STRING = hlsSecretKeyString;
        this.hlsKey = Keys.hmacShaKeyFor(this.HLS_SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8));
        logger.info("JWTUtils initialized with separate keys for Auth and HLS.");
    }

    // Existing: Generate regular JWT Token (for authentication/refresh)
    public String generateToken(String username, long expiryMinutes) {
        logger.info("Generating AUTH JWT token for username: {} with expiry: {} minutes", username, expiryMinutes);
        try {
            String token = Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000)) //in milliseconds
                    .signWith(authKey, SignatureAlgorithm.HS256) // Use authKey
                    .compact();
            logger.info("Successfully generated AUTH JWT token for username: {}", username);
            return token;
        } catch (Exception e) {
            logger.error("Error generating AUTH JWT token for user: {}. Error: {}", username, e.getMessage(), e);
            return null; // Or throw an exception, depending on your error handling policy
        }
    }

    // Existing: Validate regular JWT Token and Extract Username
    public String validateAndExtractUsername(String token) {
        logger.info("Validating and extracting username from AUTH token: {}", token);
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(authKey) // Use authKey
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String username = claims.getSubject();
            logger.debug("Username extracted from AUTH token: {}", username);
            return username;
        } catch (MalformedJwtException m) {
            logger.error("MalformedJwtException: Invalid AUTH JWT token format. Token: {}  Error: {}", token, m.getMessage());
            throw m;
        } catch (ExpiredJwtException e) {
            logger.error("ExpiredJwtException: AUTH JWT token has expired. Token: {}  Error: {}", token, e.getMessage());
            throw e;
        } catch (SignatureException s) {
            logger.error("SignatureException: AUTH JWT token signature is invalid. Token: {} Error: {}", token, s.getMessage());
            throw s;
        } catch (IllegalArgumentException i) {
            logger.error("IllegalArgumentException: AUTH JWT token is invalid. Token: {} Error: {}", token, i.getMessage());
            throw i;
        } catch (Exception e) {
            logger.error("Exception:  Error validating/extracting from AUTH token {}. Error: {}", token, e.getMessage());
            throw e;
        }
    }

    // New: Generate HLS Streaming JWT Token
    /**
     * Generates a short-lived JWT token specifically for HLS streaming.
     * This token includes the videoId as a custom claim.
     * @param videoId The ID of the video being streamed.
     * @param userId The ID of the user requesting the stream.
     * @param expiryMinutes The expiration time for the token in minutes.
     * @return The signed HLS JWT token.
     */
    public String generateHlsToken(Long videoId, Long userId, long expiryMinutes) {
        logger.info("Generating HLS JWT token for videoId: {} by userId: {} with expiry: {} minutes", videoId, userId, expiryMinutes);
        try {
            String token = Jwts.builder()
                    .setSubject(userId.toString()) // Subject can be the userId
                    .claim("videoId", videoId)      // Custom claim for video ID
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + expiryMinutes * 60 * 1000)) // in milliseconds
                    .signWith(hlsKey, SignatureAlgorithm.HS256) // Use hlsKey
                    .compact();
            logger.info("Successfully generated HLS JWT token for videoId: {}", videoId);
            return token;
        } catch (Exception e) {
            logger.error("Error generating HLS JWT token for videoId: {} userId: {}. Error: {}", videoId, userId, e.getMessage(), e);
            return null; // Or throw a specific exception
        }
    }

    // New: Validate HLS Streaming JWT Token and Extract Claims
    /**
     * Validates the HLS-specific JWT token and extracts claims.
     * @param token The HLS JWT token from the query parameter.
     * @return Claims object if valid, throws exception otherwise.
     */
    public Claims validateAndExtractHlsClaims(String token) {
        logger.info("Validating and extracting claims from HLS token: {}", token);
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(hlsKey) // Use hlsKey
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            logger.error("ExpiredJwtException: HLS token has expired. Token: {} Error: {}", token, e.getMessage());
            throw e;
        } catch (SignatureException s) {
            logger.error("SignatureException: HLS token signature is invalid. Token: {} Error: {}", token, s.getMessage());
            throw s;
        } catch (MalformedJwtException m) {
            logger.error("MalformedJwtException: Invalid HLS JWT token format. Token: {} Error: {}", token, m.getMessage());
            throw m;
        } catch (IllegalArgumentException i) {
            logger.error("IllegalArgumentException: HLS JWT token is invalid. Token: {} Error: {}", token, i.getMessage());
            throw i;
        } catch (Exception e) {
            logger.error("Exception: Error validating/extracting from HLS token {}. Error: {}", token, e.getMessage());
            throw e;
        }
    }
}