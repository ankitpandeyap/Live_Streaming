package com.robspecs.live.websocket;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component // Makes this class a Spring managed component (bean)
public class LiveStreamWebSocketHandler extends AbstractWebSocketHandler {

 private static final Logger logger = LoggerFactory.getLogger(LiveStreamWebSocketHandler.class);

 // Map to hold active WebSocket sessions, keyed by streamId
 // For a real application, consider a more robust session management.
 private final Map<String, WebSocketSession> activeStreams = new ConcurrentHashMap<>();

 // We'll use this to extract the streamId from the WebSocket URI
 private final UriTemplate liveStreamUriTemplate = new UriTemplate("/live-stream/{streamId}");

 public LiveStreamWebSocketHandler() {
     logger.info("LiveStreamWebSocketHandler initialized.");
 }

 @Override
 public void afterConnectionEstablished(WebSocketSession session) throws Exception {
     String streamId = getStreamIdFromSession(session);

     if (streamId == null || streamId.isEmpty()) {
         logger.warn("WebSocket session established with no streamId in URI: {}", session.getUri());
         session.close(CloseStatus.BAD_DATA.withReason("Missing streamId in URI"));
         return;
     }

     activeStreams.put(streamId, session); // Store the session
     logger.info("WebSocket connection established for streamId: {}. Session ID: {}", streamId, session.getId());

     // For now, no FFmpeg process is started. This will be Phase 1.5.
     // You could send a confirmation back to the client here if needed.
     // session.sendMessage(new TextMessage("Connected to live stream: " + streamId));
 }

 @Override
 protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
     String streamId = getStreamIdFromSession(session);
     if (streamId == null) {
         logger.warn("Received binary message from session {} with no associated streamId. Closing session.", session.getId());
         session.close(CloseStatus.BAD_DATA.withReason("Missing streamId for message processing"));
         return;
     }

     logger.debug("Received binary message for streamId: {} ({} bytes)", streamId, message.getPayload().remaining());

     // Currently, we're just receiving and logging.
     // In Phase 1.5, this is where you'll pipe the data to FFmpeg's stdin.
 }

 @Override
 public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
     // This method is called for all message types.
     // Since handleBinaryMessage is overridden, BinaryMessages will be routed there.
     // If you were to send TextMessages from frontend, you'd override handleTextMessage.
     super.handleMessage(session, message);
 }

 @Override
 public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
     String streamId = getStreamIdFromSession(session);
     logger.error("WebSocket transport error for streamId {}: {}", streamId, exception.getMessage(), exception);
     // Future: Clean up associated FFmpeg process, etc.
 }

 @Override
 public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
     String streamId = getStreamIdFromSession(session);

     if (streamId != null) {
         activeStreams.remove(streamId); // Remove the session
         logger.info("WebSocket connection closed for streamId: {}. Session ID: {}. Status: {}", streamId, session.getId(), status);
         // Future: Stop FFmpeg process, finalize HLS, update stream status in DB
     } else {
         logger.warn("WebSocket connection closed for session {} with no associated streamId. Status: {}", session.getId(), status);
     }
 }

 // Helper method to extract streamId from the session's URI
 private String getStreamIdFromSession(WebSocketSession session) {
     URI uri = session.getUri();
     if (uri == null) {
         return null;
     }
     // The URI path is like /live-stream/stream-1750323159034
     // UriTemplate helps extract 'stream-1750323159034' as streamId
     Map<String, String> vars = liveStreamUriTemplate.match(uri.getPath());
     return vars.get("streamId");
 }
}