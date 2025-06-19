package com.robspecs.live.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.slf4j.Logger; // <-- ADD THIS IMPORT
import org.slf4j.LoggerFactory; // <-- ADD THIS IMPORT

import java.io.File; // <-- ADD THIS IMPORT

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class); // <-- ADD THIS LINE

    @Value("${files.video.base-path}")
    private String videoBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolve the base path to an absolute path on the file system.
        // This makes sure ../videos-data is correctly translated to its full path (e.g., C:\YourProject\videos-data).
        String absoluteVideoBasePath = new File(videoBasePath).getAbsolutePath();

        // Construct the URL string with the "file:" prefix and ensure it ends with a separator.
        // Spring's addResourceLocations often prefers this explicit "file:" syntax.
        String hlsServePath = "file:" + absoluteVideoBasePath + File.separator;

        // --- NEW: Log the resolved path for debugging ---
        logger.info("Configuring HLS resource handler:");
        logger.info("  Serving URL path: /hls/**");
        logger.info("  Mapping to file system location: {}", hlsServePath);
        // --- END NEW LOGGING ---

        registry.addResourceHandler("/hls/**")
                .addResourceLocations(hlsServePath);
    }
}