// src/main/java/com/robspecs/live/ffmpeg/RedisFFmpegFeederService.java
package com.robspecs.live.ffmpeg;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener; // NEW IMPORT
import org.springframework.context.event.ContextRefreshedEvent; // NEW IMPORT
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.PatternTopic; // NEW IMPORT
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

@Service
// IMPORTANT: Implement ApplicationListener to get notified when context is refreshed
public class RedisFFmpegFeederService implements MessageListener, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(RedisFFmpegFeederService.class);

    private final RedisTemplate<String, byte[]> redisRawDataTemplate;
    private final FFmpegProcessManager ffmpegProcessManager;
    private final RedisMessageListenerContainer redisContainer;

    private final ConcurrentHashMap<String, OutputStream> ffmpegOutputStreams = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> subscribedChannels = new ConcurrentHashMap<>();

    // Keep a flag to ensure subscription happens only once after context refresh
    private volatile boolean initialSubscriptionDone = false;


    public RedisFFmpegFeederService(
            RedisTemplate<String, byte[]> redisRawDataTemplate,
            FFmpegProcessManager ffmpegProcessManager,
            RedisMessageListenerContainer redisContainer) {
        this.redisRawDataTemplate = redisRawDataTemplate;
        this.ffmpegProcessManager = ffmpegProcessManager;
        this.redisContainer = redisContainer;
        logger.info("RedisFFmpegFeederService initialized.");
    }

    /**
     * This method is called when the Spring application context is fully refreshed.
     * We use this to register the broad pattern listener for raw_frames:*.
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!initialSubscriptionDone) {
            // Subscribe to the pattern topic for raw frames *after* all beans are ready
            // This catches all channels like 'raw_frames:streamId'
            redisContainer.addMessageListener(this, new PatternTopic("raw_frames:*"));
            logger.info("RedisFFmpegFeederService: Subscribed to Redis PatternTopic 'raw_frames:*' for all raw frame ingestion on application startup.");
            initialSubscriptionDone = true;
        }
    }


    /**
     * Called when a message is received on a subscribed Redis channel.
     * @param message The Redis message.
     * @param pattern The pattern that was matched (not used here).
     */
    @Override
    public  void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String streamId = channel.replace("raw_frames:", "");
        byte[] rawFrameData = message.getBody();

        OutputStream ffmpegIn = ffmpegOutputStreams.get(channel);

        if (ffmpegIn == null) {
            // This is the first frame for this stream, so start the FFmpeg process
            try {
                logger.info("First frame received for streamId: {}. Starting FFmpeg process.", streamId);
                ffmpegIn = ffmpegProcessManager.startFFmpegProcess(streamId);
                ffmpegOutputStreams.put(channel, ffmpegIn);
            } catch (IOException e) {
                logger.error("Failed to start FFmpeg process for streamId {}: {}", streamId, e.getMessage(), e);
                // Handle cleanup: unsubscribe from Redis for this stream if FFmpeg fails to start
                stopFeedingFFmpeg(streamId); // This method will also unsubscribe
                return;
            }
        }

        try {
            ffmpegIn.write(rawFrameData);
            // logger.debug("Piped {} bytes to FFmpeg for streamId: {} (via Redis).", rawFrameData.length, streamId);
        } catch (IOException e) {
            logger.error("Error piping data to FFmpeg for streamId {} (via Redis): {}", streamId, e.getMessage(), e);
            // FFmpeg process likely died, clean up
            stopFeedingFFmpeg(streamId);
        }
    }

    /**
     * Subscribes to a Redis channel for a specific stream ID.
     * (This method is still called from RawMediaIngestWebSocketHandler, but the PatternTopic above
     * ensures reception even if this specific dynamic subscription call isn't the first).
     * @param streamId Unique ID of the video stream.
     */
    public void subscribeToRawFrames(String streamId) {
        String channel = "raw_frames:" + streamId;
        // The PatternTopic("raw_frames:*") added in onApplicationEvent already covers this.
        // This method can remain if you want explicit per-stream subscription tracking
        // or finer-grained control over specific channel lifecycles in the future.
        // For now, it mostly serves to prevent duplicate explicit subscriptions.
        if (subscribedChannels.putIfAbsent(channel, true) == null) {
            // No need to addMessageListener here if the PatternTopic already covers it.
            // But if you had specific per-channel listeners, you'd add them here.
            logger.info("Explicit 'subscribeToRawFrames' called for streamId: {}. Channel: {}", streamId, channel);
        } else {
            logger.warn("Explicit 'subscribeToRawFrames' called for already handled streamId: {}", streamId);
        }
    }

    /**
     * Stops the FFmpeg process and unsubscribes from the Redis channel.
     * @param streamId Unique ID of the video stream.
     */
    public void stopFeedingFFmpeg(String streamId) {
        String channel = "raw_frames:" + streamId;

        OutputStream ffmpegIn = ffmpegOutputStreams.remove(channel);
        if (ffmpegIn != null) {
            try {
                ffmpegIn.close();
                logger.info("Closed FFmpeg stdin for streamId: {} (via Redis feeder).", streamId);
            } catch (IOException e) {
                logger.error("Error closing FFmpeg stdin for streamId {}: {}", streamId, e.getMessage());
            }
        }

        ffmpegProcessManager.stopFFmpegProcess(streamId);

        // Remove the listener for this specific streamId's channel, if dynamically added.
        // If it's covered by a PatternTopic, removing individual listeners is more complex.
        // For PatternTopic, it means the listener will continue listening for new raw_frames:* channels,
        // but the FFmpeg process for this specific streamId is now stopped.
        // If you need to unsubscribe from a specific channel within a PatternTopic,
        // you'd need more complex logic with `redisContainer.removeMessageListener(listener, topic)`.
        // For now, stopping FFmpeg is the primary goal here.
        redisContainer.removeMessageListener(this, new ChannelTopic(channel)); // Attempt to remove specific listener
        subscribedChannels.remove(channel); // Clean up tracking map

        logger.info("Unsubscribed and stopped FFmpeg for Redis channel: {} for raw frame ingestion.", channel);
    }
}