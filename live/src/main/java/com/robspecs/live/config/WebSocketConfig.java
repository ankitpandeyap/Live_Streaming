
package com.robspecs.live.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.robspecs.live.websocket.LiveStreamWebSocketHandler; // We will create this next

@Configuration
@EnableWebSocket // Enables WebSocket server capabilities
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    private final LiveStreamWebSocketHandler liveStreamWebSocketHandler;

    // Inject our custom WebSocket handler
    public WebSocketConfig(LiveStreamWebSocketHandler liveStreamWebSocketHandler) {
        this.liveStreamWebSocketHandler = liveStreamWebSocketHandler;
        logger.info("WebSocketConfig initialized with LiveStreamWebSocketHandler.");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Registers our LiveStreamWebSocketHandler
        // The path /live-stream/{streamId} matches the frontend's WebSocket URL
        // .setAllowedOrigins("*") is for development. Restrict this in production!
        registry.addHandler(liveStreamWebSocketHandler, "/live-stream/{streamId}")
                .setAllowedOrigins("*"); // Allow all origins for development (CORS)
        logger.info("WebSocket handler registered for /live-stream/{streamId} with allowed origins: *");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set maximum buffer size for binary messages (e.g., 5MB)
        // This should be large enough to accommodate your video chunks.
        // The value is in bytes. 5 * 1024 * 1024 = 5242880 bytes
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024); // 5 MB
        container.setMaxTextMessageBufferSize(5 * 1024 * 1024); // Also set for text if needed

        // Optional: You can also set max session idle timeout (in milliseconds)
        // container.setMaxSessionIdleTimeout(30 * 60 * 1000L); // 30 minutes

        logger.info("WebSocket container configured with max binary message buffer size: {} bytes", container.getMaxBinaryMessageBufferSize());
        return container;
    }
}