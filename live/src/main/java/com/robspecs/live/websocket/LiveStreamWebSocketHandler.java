// src/main/java/com/robspecs/live/websocket/LiveStreamWebSocketHandler.java
package com.robspecs.live.websocket;

import java.io.IOException;
import java.io.OutputStream; // <-- NEW IMPORT
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import com.robspecs.live.ffmpeg.FFmpegProcessManager; // <-- NEW IMPORT

@Component
public class LiveStreamWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LiveStreamWebSocketHandler.class);

    // Map to hold active WebSocket sessions, keyed by streamId
    // We keep this to manage WebSocket sessions, not FFmpeg processes directly
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>(); // Renamed from activeStreams

    // Map to hold the OutputStream to FFmpeg for each streamId
    private final Map<String, OutputStream> ffmpegInputStreams = new ConcurrentHashMap<>(); // To send data to ffmpeg

    private final UriTemplate liveStreamUriTemplate = new UriTemplate("/live-stream/{streamId}");

    private final FFmpegProcessManager ffmpegProcessManager; // <-- NEW: Inject FFmpegProcessManager

    public LiveStreamWebSocketHandler(FFmpegProcessManager ffmpegProcessManager) { // <-- NEW: Constructor Injection
        this.ffmpegProcessManager = ffmpegProcessManager;
        logger.info("LiveStreamWebSocketHandler initialized with FFmpegProcessManager.");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String streamId = getStreamIdFromSession(session);

        if (streamId == null || streamId.isEmpty()) {
            logger.warn("WebSocket session established with no streamId in URI: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA.withReason("Missing streamId in URI"));
            return;
        }

        activeSessions.put(streamId, session); // Store the WebSocket session

        try {
            // Start the FFmpeg process for this stream
            OutputStream ffmpegIn = ffmpegProcessManager.startFFmpegProcess(streamId);
            ffmpegInputStreams.put(streamId, ffmpegIn); // Store the OutputStream
            logger.info("FFmpeg process successfully started for streamId: {}", streamId);
        } catch (IOException e) {
            logger.error("Failed to start FFmpeg process for streamId {}: {}", streamId, e.getMessage(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason("Failed to start streaming backend"));
            return;
        }

        logger.info("WebSocket connection established for streamId: {}. Session ID: {}", streamId, session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String streamId = getStreamIdFromSession(session);
        if (streamId == null) {
            logger.warn("Received binary message from session {} with no associated streamId. Closing session.", session.getId());
            session.close(CloseStatus.BAD_DATA.withReason("Missing streamId for message processing"));
            return;
        }

        OutputStream ffmpegIn = ffmpegInputStreams.get(streamId);
        if (ffmpegIn == null) {
            logger.warn("No FFmpeg process found for streamId {}. Discarding message.", streamId);
            // Optionally, close session if no FFmpeg process is running for it
            // session.close(CloseStatus.SERVER_ERROR.withReason("FFmpeg process not active"));
            return;
        }

        try {
            // Write the binary video data directly to FFmpeg's stdin
            ffmpegIn.write(message.getPayload().array());
            logger.debug("Piped {} bytes to FFmpeg for streamId: {}", message.getPayload().array().length, streamId);
        } catch (IOException e) {
            logger.error("Error piping data to FFmpeg for streamId {}: {}", streamId, e.getMessage());
            // This usually means FFmpeg process died or pipe broke.
            // Close the WebSocket session to signal the client.
            session.close(CloseStatus.SERVICE_RESTARTED.withReason("Stream processing error"));
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // We only expect BinaryMessages for video data.
        // If you send TextMessages for chat, you'd override handleTextMessage.
        super.handleMessage(session, message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String streamId = getStreamIdFromSession(session);
        logger.error("WebSocket transport error for streamId {}: {}", streamId, exception.getMessage(), exception);
        // Clean up FFmpeg process on transport error
        ffmpegProcessManager.stopFFmpegProcess(streamId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String streamId = getStreamIdFromSession(session);

        if (streamId != null) {
            activeSessions.remove(streamId);
            ffmpegInputStreams.remove(streamId); // Remove the stream from map
            logger.info("WebSocket connection closed for streamId: {}. Session ID: {}. Status: {}", streamId, session.getId(), status);
            // Stop the associated FFmpeg process
            ffmpegProcessManager.stopFFmpegProcess(streamId);
        } else {
            logger.warn("WebSocket connection closed for session {} with no associated streamId. Status: {}", session.getId(), status);
        }
    }

    private String getStreamIdFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        Map<String, String> vars = liveStreamUriTemplate.match(uri.getPath());
        return vars.get("streamId");
    }
}