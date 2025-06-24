package com.robspecs.live.security; // Changed package

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.robspecs.live.exceptions.JWTBlackListedTokenException; // Changed import
import com.robspecs.live.exceptions.TokenNotFoundException; // Changed import
import com.robspecs.live.service.TokenBlacklistService; // Changed import
import com.robspecs.live.utils.JWTUtils; // Changed import

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.InvalidClaimException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MissingClaimException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JWTValidationFilter extends OncePerRequestFilter {

	private final AuthenticationManager authenticationManager;
	private final JWTUtils jwtUtil;
	private final CustomUserDetailsService customUserDetailsService;
	private final TokenBlacklistService tokenService;
	private final static Logger logger = LoggerFactory.getLogger(JWTValidationFilter.class);

	public JWTValidationFilter(AuthenticationManager authenticationManager, JWTUtils jwtUtil,
			CustomUserDetailsService customUserDetailsService, TokenBlacklistService tokenService) {
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		this.customUserDetailsService = customUserDetailsService;
		this.tokenService = tokenService;
		logger.info("JWTValidationFilter initialized.");
	}

	private static final List<String> PUBLIC_URLS = List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/signup",
			"/api/auth/register", "/api/auth/otp/verify", "/api/auth/otp/request", "/api/auth/forgot-password",
			"/api/auth/reset-password");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		String path = request.getRequestURI();
		// This filter should NOT run for HLS stream paths, as HlsTokenValidationFilter
		// handles them.
		// It also should skip other public/authentication endpoints already handled by
		// other filters or explicitly permitted.
		return path.startsWith("/api/videos/stream/") || // **CRITICAL for HLS**
				PUBLIC_URLS.contains(path); // Uses your existing PUBLIC_URLS list
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		logger.info("Entering JWTValidationFilter for request URI: {}", request.getRequestURI());

		String path = request.getRequestURI();

		if (PUBLIC_URLS.contains(path)) {
			logger.info("Request path {} is public. Skipping JWT validation.", path);
			filterChain.doFilter(request, response);
			return;
		}
		logger.info("Request path {} is protected. Proceeding with JWT validation.", path);

		try {
			String token = extractTokenFromRequest(request);
			logger.debug("Extracted token (first 10 chars): {}",
					token != null ? token.substring(0, Math.min(token.length(), 10)) + "..." : "null");

			if (tokenService.isBlacklisted(token)) {
				logger.warn("Access token is blacklisted: (first 10 chars) {}. Throwing exception.",
						token != null ? token.substring(0, Math.min(token.length(), 10)) + "..." : "null");
				throw new JWTBlackListedTokenException("Access token is Blacklisted");
			}
			logger.debug("Access token is not blacklisted. Proceeding with validation.");

			String usernameFromToken = jwtUtil.validateAndExtractUsername(token);
			logger.debug("Username extracted from token: {}", usernameFromToken);

			UserDetails currentUser = customUserDetailsService.loadUserByUsername(usernameFromToken);
			logger.debug("User details loaded for: {}", currentUser.getUsername());

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(currentUser, null,
					currentUser.getAuthorities());

			authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			logger.debug("Authentication token created and details set for user: {}", currentUser.getUsername());

			SecurityContextHolder.getContext().setAuthentication(authToken);
			logger.info("User: {} authenticated via JWT and SecurityContextHolder updated.", usernameFromToken);

		} catch (TokenNotFoundException e) {
			logger.warn("JWT validation failed: Token not found in request. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Token not found: " + e.getMessage());
			request.setAttribute("custom-exception", "JWTTokenNotFoundException");
			throw new BadCredentialsException("Token not found");
		} catch (MissingClaimException e) {
			logger.warn("JWT validation failed: Missing claim in token. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Missing claim in token: " + e.getMessage());
			request.setAttribute("custom-exception", "JWTTokenMissingClaimException");
			throw new BadCredentialsException("Missing claim in token");
		} catch (InvalidClaimException e) {
			logger.warn("JWT validation failed: Invalid claim in token. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Invalid claim in token: " + e.getMessage());
			request.setAttribute("custom-exception", "JWTTokenInvalidClaimException");
			throw new BadCredentialsException("Invalid claim");
		} catch (UsernameNotFoundException e) {
			logger.warn("JWT validation failed: Username from token not found in DB. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Username not found: " + e.getMessage());
			request.setAttribute("custom-exception", "JWTTokenUsernameNotFoundException");
			throw new BadCredentialsException("Username not found");
		} catch (ExpiredJwtException e) {
			// Instead of setting attributes and continuing the chain,
			// we now throw an exception, leading to a 401 which the frontend Axios
			// interceptor will catch.
			logger.warn("JWT validation failed: Access token expired for user: {}. Throwing BadCredentialsException.",
					e.getClaims().getSubject());
			request.setAttribute("custom-error", "Access Token Expired. Please refresh.");
			request.setAttribute("custom-exception", "ExpiredJwtException");
			throw new BadCredentialsException("Access token expired"); // This will trigger 401
		} catch (JWTBlackListedTokenException e) {
			logger.warn("JWT validation failed: Blacklisted access token. Message: {}", e.getMessage());
			request.setAttribute("custom-error", "Authentication failed: Blacklisted access token.");
			request.setAttribute("custom-exception", "JWTBlackListedTokenException");
			throw new BadCredentialsException("Access token is Blacklisted");
		} catch (JwtException e) {
			logger.error("JWT validation failed: General JWT parsing error. Message: {}", e.getMessage(), e);
			request.setAttribute("custom-error", "JWT parsing error: " + e.getMessage());
			request.setAttribute("custom-exception", "JWTGeneralParsingException");
			throw new BadCredentialsException("Invalid JWT token");
		} catch (Exception e) {
			logger.error("JWT validation failed: Unhandled authentication error for request URI {}. Message: {}",
					request.getRequestURI(), e.getMessage(), e);
			request.setAttribute("custom-error", "Unhandled authentication error: " + e.getMessage());
			request.setAttribute("custom-exception", "UnexpectedAuthenticationException");
			throw new BadCredentialsException("Unexpected error");
		}
		filterChain.doFilter(request, response);
		logger.info("Exiting JWTValidationFilter for request URI: {}", request.getRequestURI());
	}

	private String extractTokenFromRequest(HttpServletRequest request) throws TokenNotFoundException {
		logger.debug("Attempting to extract token from Authorization header.");
		String bearerToken = request.getHeader("Authorization");
		if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
			String token = bearerToken.substring(7);
			logger.debug("Token successfully extracted from Authorization header (first 10 chars): {}",
					token.substring(0, Math.min(token.length(), 10)) + "...");
			return token;
		}
		logger.warn("Authorization header does not contain a Bearer token or is malformed.");
		throw new TokenNotFoundException("Authorization header missing or invalid Bearer token.");
	}
}