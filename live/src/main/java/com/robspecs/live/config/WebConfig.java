package com.robspecs.live.config;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Value("${files.video.base-path}")
    private String videoBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Normalize the base path to an absolute path, then prepend "file:///"
        // This ensures Spring Boot correctly interprets it as a file URI.
        // Replace backslashes for URL compatibility and ensure a trailing slash for the directory
        String absoluteVideoBasePath = new File(videoBasePath).getAbsolutePath().replace("\\", "/");
        String hlsServePath = "file:///" + absoluteVideoBasePath + (absoluteVideoBasePath.endsWith("/") ? "" : "/") ;


        logger.info("Configuring HLS resource handler:");
        logger.info("  Serving URL path: /hls/**");
        logger.info("  Mapping to file system location: {}", hlsServePath);

        registry.addResourceHandler("/hls/**")
                .addResourceLocations(hlsServePath)
                .setUseLastModified(true); // Recommended for caching
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.mediaType("m3u8", MediaType.valueOf("application/x-mpegURL"));
        configurer.mediaType("ts", MediaType.valueOf("video/MP2T"));
        logger.info("Configured content negotiation for .m3u8 (application/x-mpegURL) and .ts (video/MP2T).");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Apply CORS to all endpoints
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173") // Allow your React app's origin
                .allowedMethods("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS") // Allow necessary methods
                .allowedHeaders("*") // Allow all headers
                .allowCredentials(true); // Allow credentials (if you use cookies/auth tokens)
    }
}