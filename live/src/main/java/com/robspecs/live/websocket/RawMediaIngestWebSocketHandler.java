package com.robspecs.live.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import com.robspecs.live.ffmpeg.RedisFFmpegFeederService;

@Component
public class RawMediaIngestWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(RawMediaIngestWebSocketHandler.class);

    private final RedisTemplate<String, byte[]> redisRawDataTemplate;
    private final RedisFFmpegFeederService redisFFmpegFeederService;

    private final Map<String, String> sessionStreamIds = new ConcurrentHashMap<>();
    private final UriTemplate ingestUriTemplate = new UriTemplate("/raw-media-ingest/{streamId}");

    public RawMediaIngestWebSocketHandler(
            RedisTemplate<String, byte[]> redisRawDataTemplate,
            RedisFFmpegFeederService redisFFmpegFeederService) {
        this.redisRawDataTemplate = redisRawDataTemplate;
        this.redisFFmpegFeederService = redisFFmpegFeederService;
        logger.info("RawMediaIngestWebSocketHandler initialized.");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String streamId = getStreamIdFromSession(session);

        if (streamId == null || streamId.isEmpty()) {
            logger.warn("Connection established without streamId: {}", session.getUri());
            session.close(CloseStatus.BAD_DATA.withReason("Missing streamId in URI."));
            return;
        }

        sessionStreamIds.put(session.getId(), streamId);
        logger.info("WebSocket connected. streamId: {}, sessionId: {}", streamId, session.getId());

        redisFFmpegFeederService.subscribeToRawFrames(streamId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        String streamId = sessionStreamIds.get(session.getId());

        if (streamId == null) {
            logger.warn("Binary message received from unknown session: {}", session.getId());
            session.close(CloseStatus.BAD_DATA.withReason("Unknown streamId."));
            return;
        }

        byte[] payload = message.getPayload().array();
        String redisChannel = "raw_frames:" + streamId;

        try {
            redisRawDataTemplate.convertAndSend(redisChannel, payload);
            // logger.debug("Published {} bytes to Redis channel: {}", payload.length, redisChannel);
        } catch (Exception e) {
            logger.error("Error publishing to Redis for streamId {}: {}", streamId, e.getMessage(), e);
            session.close(CloseStatus.SERVER_ERROR.withReason("Redis publish failed."));
            redisFFmpegFeederService.stopFeedingFFmpeg(streamId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String streamId = sessionStreamIds.remove(session.getId());

        if (streamId != null) {
            logger.info("WebSocket disconnected. streamId: {}, sessionId: {}, status: {}", streamId, session.getId(), status);
            redisFFmpegFeederService.stopFeedingFFmpeg(streamId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String streamId = sessionStreamIds.get(session.getId());

        logger.error("Transport error. streamId: {}, sessionId: {}, error: {}", streamId, session.getId(), exception.getMessage(), exception);
        session.close(CloseStatus.SERVER_ERROR.withReason("Transport error."));
        if (streamId != null) {
            redisFFmpegFeederService.stopFeedingFFmpeg(streamId);
        }
    }

    private String getStreamIdFromSession(WebSocketSession session) {
        String path = session.getUri().getPath();
        Map<String, String> vars = ingestUriTemplate.match(path);
        return vars.get("streamId");
    }
}
