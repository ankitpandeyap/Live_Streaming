// src/main/java/com/robspecs/live/config/WebSocketConfig.java
package com.robspecs.live.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.robspecs.live.websocket.LiveStreamWebSocketHandler;
import com.robspecs.live.websocket.RawMediaIngestWebSocketHandler; // NEW IMPORT: Import the new handler

@Configuration
@EnableWebSocket // Enables WebSocket server capabilities
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private final LiveStreamWebSocketHandler liveStreamWebSocketHandler;
    private final RawMediaIngestWebSocketHandler rawMediaIngestWebSocketHandler; // NEW INJECTION: For raw media ingest

    // Inject both custom WebSocket handlers
    public WebSocketConfig(LiveStreamWebSocketHandler liveStreamWebSocketHandler,
                           RawMediaIngestWebSocketHandler rawMediaIngestWebSocketHandler) { // NEW PARAMETER
        this.liveStreamWebSocketHandler = liveStreamWebSocketHandler;
        this.rawMediaIngestWebSocketHandler = rawMediaIngestWebSocketHandler; // Assign the new handler
        logger.info("WebSocketConfig initialized with LiveStreamWebSocketHandler and RawMediaIngestWebSocketHandler.");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Registers our LiveStreamWebSocketHandler
        // The path /live-stream/{streamId} matches the frontend's WebSocket URL
        registry.addHandler(liveStreamWebSocketHandler, "/live-stream/{streamId}")
                .setAllowedOrigins("*"); // Allow all origins for development (CORS)
        logger.info("WebSocket handler registered for /live-stream/{streamId} with allowed origins: *");

        // NEW REGISTRATION for raw media ingestion
        registry.addHandler(rawMediaIngestWebSocketHandler, "/raw-media-ingest/{streamId}")
                .setAllowedOrigins("*"); // Allow all origins for development (CORS)
        logger.info("WebSocket handler registered for /raw-media-ingest/{streamId} with allowed origins: *");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set maximum buffer size for binary messages (e.g., 5MB)
        // This should be large enough to accommodate your video chunks.
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024); // 5 MB
        container.setMaxTextMessageBufferSize(5 * 1024 * 1024); // Also set for text if needed

        logger.info("WebSocket container configured with max binary message buffer size: {} bytes", container.getMaxBinaryMessageBufferSize());
        return container;
    }
}