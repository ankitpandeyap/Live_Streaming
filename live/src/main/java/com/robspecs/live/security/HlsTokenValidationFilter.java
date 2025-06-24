package com.robspecs.live.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.robspecs.live.utils.JWTUtils;

import java.io.IOException;

public class HlsTokenValidationFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(HlsTokenValidationFilter.class);
	private final JWTUtils jwtUtils;

	public HlsTokenValidationFilter(JWTUtils jwtUtils) {
		this.jwtUtils = jwtUtils;
		logger.info("HlsTokenValidationFilter initialized.");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String requestURI = request.getRequestURI();
		logger.debug("Entering HlsTokenValidationFilter for URI: {}", requestURI);

		// Only process requests that are part of the HLS stream path
		// This filter will be specifically configured in SecurityConfig for
		// /api/videos/stream/**
		// But adding a check here provides an extra layer of clarity/safety.
		if (!requestURI.startsWith("/api/videos/stream/")) {
			logger.debug("URI {} is not an HLS stream path. Skipping HlsTokenValidationFilter.", requestURI);
			filterChain.doFilter(request, response);
			return;
		}

		String hlsToken = request.getParameter("token");
		logger.debug("Extracted HLS token from query parameter: {}",
				hlsToken != null ? hlsToken.substring(0, Math.min(hlsToken.length(), 10)) + "..." : "null");

		if (hlsToken == null || hlsToken.isEmpty()) {
			logger.warn("HLS token is missing for stream request URI: {}", requestURI);
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\": \"HLS Token Missing\"}");
			return; // Stop the filter chain
		}

		try {
			Claims claims = jwtUtils.validateAndExtractHlsClaims(hlsToken);
			Long videoIdFromToken = claims.get("videoId", Long.class);
			String userIdFromToken = claims.getSubject(); // Subject holds userId

			// Extract videoId from the URL path, e.g., /api/videos/stream/123/master.m3u8
			// -> 123
			Long videoIdFromPath = extractVideoIdFromPath(requestURI);

			if (videoIdFromPath == null || !videoIdFromPath.equals(videoIdFromToken)) {
				logger.warn("HLS token videoId mismatch or invalid path videoId. Token videoId: {}, Path videoId: {}",
						videoIdFromToken, videoIdFromPath);
				response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
				response.setContentType("application/json");
				response.getWriter().write("{\"error\": \"Invalid HLS token for this video\"}");
				return;
			}

			// If token is valid, set an anonymous authentication.
			// We use AnonymousAuthenticationToken as we don't need full user details for
			// each stream segment,
			// just validation that the token is legit for the video.
			// If you needed specific user details (e.g., for per-user watermarking),
			// you'd load UserDetails here and create a UsernamePasswordAuthenticationToken.
			AnonymousAuthenticationToken authentication = new AnonymousAuthenticationToken("hls-key", // Principal name,
																										// could be
																										// "hls-user-" +
																										// userIdFromToken
					"anonymousUser", // Credentials
					AuthorityUtils.createAuthorityList("ROLE_HLS_STREAMER") // Grant a specific role
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			logger.debug("HLS token valid for videoId: {}. Authentication set.", videoIdFromToken);

			filterChain.doFilter(request, response); // Continue the chain

		} catch (ExpiredJwtException e) {
			logger.warn("HLS token expired for URI: {}. Message: {}", requestURI, e.getMessage());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
			response.setContentType("application/json");
			response.getWriter().write("{\"error\": \"HLS Token Expired\"}");
		} catch (JwtException e) {
			logger.warn("Invalid HLS token for URI: {}. Message: {}", requestURI, e.getMessage());
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized
			response.setContentType("application/json");
			response.getWriter().write("{\"error\": \"Invalid HLS Token\"}");
		} catch (Exception e) {
			logger.error("Unexpected error in HlsTokenValidationFilter for URI: {}. Message: {}", requestURI,
					e.getMessage(), e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\": \"Internal server error during HLS token validation.\"}");
		}
	}

	/**
	 * Extracts the video ID from an HLS stream path (e.g.,
	 * /api/videos/stream/123/master.m3u8).
	 * 
	 * @param requestURI The full request URI.
	 * @return The extracted video ID as a Long, or null if not found/invalid.
	 */
	private Long extractVideoIdFromPath(String requestURI) {
		// Expected format: /api/videos/stream/{videoId}/...
		String prefix = "/api/videos/stream/";
		if (requestURI.startsWith(prefix)) {
			String pathSegment = requestURI.substring(prefix.length());
			int nextSlash = pathSegment.indexOf('/');
			if (nextSlash > 0) {
				String videoIdStr = pathSegment.substring(0, nextSlash);
				try {
					return Long.parseLong(videoIdStr);
				} catch (NumberFormatException e) {
					logger.warn("Could not parse video ID from path segment: {}", videoIdStr);
					return null;
				}
			}
		}
		return null;
	}

	// This filter should NOT be applied for every request, only for HLS stream
	// requests.
	// So, we'll mark it to skip when not needed. SecurityConfig will handle the
	// path matching.
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		// This is a safety check. The primary control is in SecurityConfig.
		String path = request.getRequestURI();
		return !path.startsWith("/api/videos/stream/");
	}
}