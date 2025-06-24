package com.robspecs.live.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robspecs.live.dto.LoginDTO;
import com.robspecs.live.utils.JWTUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

public class JWTAuthenticationFilter extends OncePerRequestFilter {

	private final AuthenticationManager authenticationManager;
	private final JWTUtils jwtUtil;
	private final Validator validator;
	private final static Logger logger = LoggerFactory.getLogger(JWTAuthenticationFilter.class);

	public JWTAuthenticationFilter(AuthenticationManager authenticationManager, JWTUtils jwtUtil) {
		this.authenticationManager = authenticationManager;
		this.jwtUtil = jwtUtil;
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
		logger.info("JWTAuthenticationFilter initialized."); // Log filter initialization
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		logger.info("Entering JWTAuthenticationFilter for request URI: {}", request.getRequestURI()); // Log method
																										// entry

		if (!request.getServletPath().equals("/api/auth/login")) {
			logger.info("Request path {} is not /api/auth/login. Skipping authentication filter.",
					request.getServletPath());
			filterChain.doFilter(request, response);
			return;
		}

		logger.info("Processing login request for path: {}", request.getServletPath()); // Info for actual login attempt

		try {
			ObjectMapper objectMapper = new ObjectMapper();
			LoginDTO loginRequest = objectMapper.readValue(request.getInputStream(), LoginDTO.class);
			logger.info("Login request payload parsed for user: {}", loginRequest.getUsernameOrEmail()); // Log parsed
																											// payload

			Set<ConstraintViolation<LoginDTO>> violations = validator.validate(loginRequest);
			if (!violations.isEmpty()) {
				// Collect all error messages
				String errorMessages = violations.stream()
						.map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
						.collect(Collectors.joining(", "));
				logger.warn("LoginDTO validation failed for user {}: {}", loginRequest.getUsernameOrEmail(),
						errorMessages);
				response.setStatus(HttpStatus.BAD_REQUEST.value()); // Set 400 Bad Request
				response.setContentType("application/json");
				response.getWriter().write("{\"error\": \"" + errorMessages + "\"}");
				return; // Stop the filter chain here
			}
			logger.info("LoginDTO validation successful for user: {}", loginRequest.getUsernameOrEmail());

			UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
					loginRequest.getUsernameOrEmail(), loginRequest.getPassword());
			logger.info("Created UsernamePasswordAuthenticationToken for user: {}", loginRequest.getUsernameOrEmail()); // Log
																														// token
																														// creation

			Authentication authResult = authenticationManager.authenticate(authToken);
			logger.info("AuthenticationManager returned result for user: {}", authResult.getName()); // Log
																										// authentication
																										// result

			UserDetails userDetails = (UserDetails) authResult.getPrincipal();
			logger.info("User details principal obtained: {}", userDetails.getUsername()); // Log principal details

			if (!userDetails.isEnabled()) {
				logger.warn("Authentication failed for user {} - profile not enabled. Throwing exception.",
						userDetails.getUsername()); // Warn if not enabled
				response.setStatus(HttpStatus.UNAUTHORIZED.value()); // 401 Unauthorized
				response.setContentType("application/json");
				response.getWriter().write("{\"error\": \"Profile not enabled. Please re-verify your profile.\"}");
				return; // Stop further processing
			}
			logger.info("User {} profile is enabled. Proceeding with token generation.", userDetails.getUsername()); // Debug
																														// if
																														// enabled

			if (authResult.isAuthenticated()) {
				logger.info("User: {} authenticated successfully. Generating tokens.", userDetails.getUsername()); // Info
																													// for
																													// successful
																													// authentication
				String token = jwtUtil.generateToken(loginRequest.getUsernameOrEmail(), 15); // 15min
				response.setHeader("Authorization", "Bearer " + token);
				logger.info("Access Token generated and set in Authorization header (first 10 chars): {}",
						token.substring(0, Math.min(token.length(), 10)) + "..."); // Debug partial token

				String refreshToken = jwtUtil.generateToken(loginRequest.getUsernameOrEmail(), 7 * 24 * 60);
				ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken).httpOnly(true)
						.secure(false) // Set to true in production with HTTPS
						.path("/") // Or "/" if you want it available everywhere
						.maxAge(Duration.ofDays(7)) // Use Duration for clarity and safety (7 days)
						.build();
				response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
				logger.info("Refresh Token generated and set as HTTP-Only cookie (first 10 chars): {}",
						refreshToken.substring(0, Math.min(refreshToken.length(), 10)) + "..."); // Debug partial
																									// refresh token

				response.setContentType("application/json");
				response.getWriter().write("{\"message\":\"Login successful\"}");
				logger.info("Login successful response sent for user: {}.", userDetails.getUsername()); // Info for
																										// response sent

			} else {
				logger.warn("Authentication result indicated not authenticated for user: {}",
						userDetails.getUsername()); // Warn if not authenticated
			}

		} catch (BadCredentialsException e) {
			logger.error("Authentication failed for user (Bad Credentials) or disabled account: {}", e.getMessage());
			response.setStatus(HttpStatus.UNAUTHORIZED.value()); // 401 Unauthorized
			response.setContentType("application/json");
			// You might want to return a more generic "Invalid credentials" to avoid
			// enumeration attacks
			response.getWriter().write("{\"error\": \"Invalid username or password\"}");
		} catch (Exception e) {
			logger.error("Authentication failed due to unexpected error for request URI {}: {}",
					request.getRequestURI(), e.getMessage(), e);
			response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()); // 500 Internal Server Error
			response.setContentType("application/json");
			response.getWriter()
					.write("{\"error\": \"An unexpected error occurred during login. Please try again later.\"}");
		}
		logger.info("Exiting JWTAuthenticationFilter for request URI: {}", request.getRequestURI());
	}
}
