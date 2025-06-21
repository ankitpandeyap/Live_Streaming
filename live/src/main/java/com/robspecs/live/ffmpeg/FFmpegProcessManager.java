// src/main/java/com/robspecs/live/ffmpeg/FFmpegProcessManager.java
package com.robspecs.live.ffmpeg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays; // Still used, but mostly for initial List creation
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FFmpegProcessManager {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegProcessManager.class);

    private final ConcurrentHashMap<String, Process> activeFFmpegProcesses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OutputStream> ffmpegInputStreams = new ConcurrentHashMap<>();

    @Value("${files.video.base-path}")
    private String videoBasePath;

    // Confirmed by user: This is the correct Docker image name
    private static final String FFMPEG_DOCKER_IMAGE = "ffmpeg-hls-streamer";

    /**
     * Starts a new FFmpeg Docker process for a given streamId.
     * The process reads raw video/audio data from its stdin and outputs HLS segments.
     *
     * @param streamId The unique identifier for the stream.
     * @return The OutputStream connected to FFmpeg's stdin.
     * @throws IOException If there's an error starting the process or creating directories.
     */
    public OutputStream startFFmpegProcess(String streamId) throws IOException {
        Path streamOutputPath = Paths.get(videoBasePath, streamId);
        Files.createDirectories(streamOutputPath); // Ensure the directory exists on the host

        String containerOutputPath = "/app/hls_output"; // Path inside the Docker container

        // Build the FFmpeg arguments as a List of Strings
        List<String> ffmpegArgs = new ArrayList<>();

        // --- Input Configuration ---
        // IMPORTANT: Explicitly set input format to webm for pipe:0.
        // This helps FFmpeg correctly interpret the MediaRecorder's output from the frontend.
        ffmpegArgs.add("-f");
        ffmpegArgs.add("webm"); // Explicitly set input format to WebM
        ffmpegArgs.add("-probesize");
        ffmpegArgs.add("10M"); // Analyze 10MB of input to better detect format
        ffmpegArgs.add("-analyzeduration");
        ffmpegArgs.add("10M"); // Analyze for 10 million microseconds to better detect stream properties
        ffmpegArgs.add("-i");
        ffmpegArgs.add("pipe:0"); // Reads from stdin (where RedisFeederService will write)

        // --- Video Output Options (re-encode to H.264) ---
        ffmpegArgs.add("-c:v");
        ffmpegArgs.add("libx264"); // Use H.264 video codec
        ffmpegArgs.add("-preset");
        ffmpegArgs.add("veryfast"); // Encoding preset for speed (e.g., ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow)

        // REPLACEMENT: Using target bitrate instead of CRF for more consistent live stream output
        ffmpegArgs.add("-b:v");
        ffmpegArgs.add("1500k"); // Target video bitrate (e.g., 1.5 Mbps for decent 720p)
        ffmpegArgs.add("-maxrate");
        ffmpegArgs.add("2000k"); // Maximum video bitrate (e.g., 2 Mbps to avoid spikes)
        ffmpegArgs.add("-bufsize");
        ffmpegArgs.add("3000k"); // Decoder buffer size (typically 1.5-2x maxrate for VBR, or 2x maxrate for CBR-like)

        ffmpegArgs.add("-profile:v");
        ffmpegArgs.add("high"); // H.264 profile for broad compatibility
        ffmpegArgs.add("-level");
        ffmpegArgs.add("4.0"); // H.264 level for broad compatibility
        ffmpegArgs.add("-sc_threshold");
        ffmpegArgs.add("0"); // Disable scene change detection to ensure consistent GOP (Group of Pictures)
        ffmpegArgs.add("-g");
        ffmpegArgs.add("48"); // Keyframe interval (e.g., 2 seconds * 24 fps = 48 frames, for 2-second segments)
        ffmpegArgs.add("-keyint_min");
        ffmpegArgs.add("48"); // Minimum keyframe interval (should match -g for fixed GOP)
        ffmpegArgs.add("-r");
        ffmpegArgs.add("24"); // Explicit output framerate (e.g., 24, 30) - align with desired output
        ffmpegArgs.add("-pix_fmt");
        ffmpegArgs.add("yuv420p"); // Pixel format for broad compatibility (especially important for H.264)

        // --- Audio Output Options (re-encode to AAC) ---
        ffmpegArgs.add("-c:a");
        ffmpegArgs.add("aac"); // Re-encode audio to AAC
        ffmpegArgs.add("-b:a");
        ffmpegArgs.add("128k"); // Audio bitrate
        ffmpegArgs.add("-ac");
        ffmpegArgs.add("1"); // Audio channels: 1 for mono, 2 for stereo (mono saves bandwidth and is common for voice-centric streams)

        // --- HLS Muxer Options (Adjusted for low-latency live preview) ---
        ffmpegArgs.add("-f");
        ffmpegArgs.add("hls"); // Output format is HLS
        ffmpegArgs.add("-hls_time");
        ffmpegArgs.add("2"); // Segment duration in seconds (e.g., 2 seconds for low latency)
        ffmpegArgs.add("-hls_list_size");
        ffmpegArgs.add("3"); // Keep only 3 segments in playlist (e.g., 3 * 2s = 6 seconds of live buffer)
                             // This is ideal for current live preview. For full recording (VOD), it would be 0 or a very large number.
        ffmpegArgs.add("-hls_flags");
        ffmpegArgs.add("delete_segments+append_list"); // Delete old segments and append new ones to the playlist
        ffmpegArgs.add("-hls_init_time");
        ffmpegArgs.add("6"); // Generate 6 seconds (3 segments) of video before writing the first index.m3u8.
                             // This ensures the player has enough initial data to start smoothly.
        ffmpegArgs.add("-hls_segment_filename");
        ffmpegArgs.add(containerOutputPath + "/segment_%d.ts"); // Pattern for segment filenames
        ffmpegArgs.add(containerOutputPath + "/index.m3u8"); // Output HLS master playlist file

        // Docker command to run the FFmpeg container and pass arguments directly
        List<String> dockerCommand = new ArrayList<>();
        dockerCommand.add("docker");
        dockerCommand.add("run");
        dockerCommand.add("-i"); // Interactive mode to allow stdin
        dockerCommand.add("--rm"); // Automatically remove container on exit
        dockerCommand.add("-v");
        // Ensure the host path is correctly normalized for Docker volume mounts on Windows (e.g., /c/Users/...)
        dockerCommand.add(streamOutputPath.toAbsolutePath().normalize().toString().replace("\\", "/") + ":" + containerOutputPath);
        dockerCommand.add(FFMPEG_DOCKER_IMAGE); // The Docker image to use
        dockerCommand.addAll(ffmpegArgs); // Add all FFmpeg arguments directly after the image name

        ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);

        logger.info("Starting FFmpeg Docker process for streamId: {}", streamId);
        logger.debug("FFmpeg Docker command: {}", String.join(" ", processBuilder.command()));

        Process ffmpegProcess = processBuilder.start();

        // Start threads to consume stdout and stderr to prevent blocking the FFmpeg process
        // FFmpeg's verbose output is usually to stderr.
        Executors.newSingleThreadExecutor().submit(new StreamGobbler(ffmpegProcess.getErrorStream(), msg -> {
            // Keep as ERROR for now to capture all output during debugging.
            // In production, consider filtering or changing level to INFO for non-critical messages.
            logger.error("FFmpeg stderr: {}", msg);
        }));

        // FFmpeg usually doesn't output much to stdout unless specifically configured.
        Executors.newSingleThreadExecutor().submit(new StreamGobbler(ffmpegProcess.getInputStream(), msg -> {
            logger.info("FFmpeg stdout: {}", msg);
        }));

        activeFFmpegProcesses.put(streamId, ffmpegProcess);
        ffmpegInputStreams.put(streamId, ffmpegProcess.getOutputStream());

        // Monitor FFmpeg process exit in a separate thread
        new Thread(() -> {
            try {
                int exitCode = ffmpegProcess.waitFor();
                logger.info("FFmpeg process for streamId {} exited with code {}", streamId, exitCode);
                cleanupFFmpegProcess(streamId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logger.error("FFmpeg process for streamId {} was interrupted: {}", streamId, e.getMessage());
                cleanupFFmpegProcess(streamId); // Ensure cleanup even on interruption
            }
        }, "ffmpeg-monitor-" + streamId).start(); // Give thread a meaningful name

        logger.info("FFmpeg process started successfully for streamId: {}", streamId);
        return ffmpegProcess.getOutputStream(); // Return stdin for feeding raw data
    }

    /**
     * Stops the FFmpeg process associated with a given streamId.
     * It attempts a graceful shutdown by closing stdin, then forcibly destroys if needed.
     *
     * @param streamId The unique identifier of the stream to stop.
     */
    public void stopFFmpegProcess(String streamId) {
        Process ffmpegProcess = activeFFmpegProcesses.get(streamId);
        if (ffmpegProcess != null) {
            logger.info("Attempting to stop FFmpeg process for streamId: {}", streamId);
            try {
                OutputStream os = ffmpegInputStreams.get(streamId);
                if (os != null) {
                    os.close(); // Close stdin to FFmpeg, signaling end of input stream
                    logger.debug("Closed FFmpeg stdin for streamId: {}", streamId);
                }
                // Give FFmpeg a bit of time to finish processing and close files gracefully
                boolean exited = ffmpegProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    logger.warn("FFmpeg process for streamId {} did not exit gracefully within 5s, forcing destruction.", streamId);
                    ffmpegProcess.destroyForcibly(); // Force kill if it doesn't exit within timeout
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error stopping FFmpeg process for streamId {}: {}", streamId, e.getMessage());
                ffmpegProcess.destroyForcibly(); // Ensure it's killed even on error
            } finally {
                cleanupFFmpegProcess(streamId); // Always clean up resources
            }
        } else {
            logger.info("No active FFmpeg process found for streamId: {}. No action needed.", streamId);
        }
    }

    /**
     * Cleans up internal maps after an FFmpeg process has stopped or been destroyed.
     *
     * @param streamId The unique identifier of the stream.
     */
    private void cleanupFFmpegProcess(String streamId) {
        activeFFmpegProcesses.remove(streamId);
        ffmpegInputStreams.remove(streamId);
        logger.info("Cleaned up FFmpeg process entry for streamId: {}", streamId);
    }

    /**
     * Helper class to consume and log a process's InputStream (stdout or stderr)
     * to prevent the process from blocking due to full pipes.
     */
    private static class StreamGobbler implements Runnable {
        private final java.io.InputStream inputStream;
        private final Consumer<String> consumer;

        public StreamGobbler(java.io.InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new java.io.BufferedReader(new java.io.InputStreamReader(inputStream)).lines()
                .forEach(consumer);
        }
    }
}
