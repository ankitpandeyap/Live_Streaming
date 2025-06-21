// src/main/java/com/robspecs/live/ffmpeg/FFmpegProcessManager.java
package com.robspecs.live.ffmpeg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
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

    public OutputStream startFFmpegProcess(String streamId) throws IOException {
        Path streamOutputPath = Paths.get(videoBasePath, streamId);
        Files.createDirectories(streamOutputPath); // Ensure the directory exists on the host

        String containerOutputPath = "/app/hls_output"; // Path inside the Docker container

        // Define FFmpeg arguments as a List of Strings
        List<String> ffmpegArgs = Arrays.asList(
                // Input options for pipe:0
                "-probesize", "10M", // Analyze 10MB of input to better detect format
                "-analyzeduration", "10M", // Analyze for 10 million microseconds to better detect stream properties
                "-i", "pipe:0", // Reads from stdin

                // Video output options (re-encode to H.264)
                "-c:v", "libx264", // Use H.264 video codec
                "-preset", "veryfast", // Encoding preset for speed (e.g., ultrafast, superfast, veryfast, faster, fast, medium, slow, slower, veryslow)
                "-crf", "23", // Constant Rate Factor for quality (lower is better quality, larger file size)
                "-profile:v", "high", // H.264 profile for broad compatibility
                "-level", "4.0", // H.264 level for broad compatibility
                "-sc_threshold", "0", // Disable scene change detection to ensure consistent GOP (Group of Pictures)
                "-g", "48", // Keyframe interval (e.g., 2 seconds * 24 fps = 48 frames) - adjust based on expected input FPS
                "-keyint_min", "48", // Minimum keyframe interval
                "-r", "24", // Explicit output framerate (e.g., 24, 30) - ensure this matches your input or desired output
                "-pix_fmt", "yuv420p", // Pixel format for broad compatibility

                // Audio output options (re-encode to AAC)
                "-c:a", "aac", // Re-encode audio to AAC
                "-b:a", "128k", // Audio bitrate
                "-ac", "1", // Audio channels: 1 for mono, 2 for stereo (mono saves bandwidth)

                // HLS muxer options (Adjusted for live preview - limited playlist size)
                "-f", "hls", // Output format is HLS
                "-hls_time", "2", // Segment duration in seconds (e.g., 2 seconds)
                "-hls_list_size", "3", // Keep only 3 segments in playlist (e.g., 3 * 2s = 6 seconds of live buffer)
                                        // This is ideal for current live preview. For full recording (VOD), it would be 0 or a very large number.
                "-hls_flags", "delete_segments+append_list", // Delete old segments and append new ones
                "-hls_init_time", "6", // *** NEW ADDITION: Generate 6 seconds (3 segments) before writing the first index.m3u8 ***
                "-hls_segment_filename", containerOutputPath + "/segment_%d.ts", // Pattern for segment filenames
                containerOutputPath + "/index.m3u8" // Output HLS master playlist file
        );

        // Docker command to run the FFmpeg container and pass arguments directly
        List<String> dockerCommand = new ArrayList<>();
        dockerCommand.add("docker");
        dockerCommand.add("run");
        dockerCommand.add("-i"); // Interactive mode to allow stdin
        dockerCommand.add("--rm"); // Automatically remove container on exit
        dockerCommand.add("-v");
        // Ensure the host path is correctly normalized for Docker volume mounts on Windows
        dockerCommand.add(streamOutputPath.toAbsolutePath().normalize().toString() + ":" + containerOutputPath);
        dockerCommand.add(FFMPEG_DOCKER_IMAGE); // The Docker image to use
        dockerCommand.addAll(ffmpegArgs); // Add all FFmpeg arguments directly after the image name

        ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);

        logger.info("Starting FFmpeg Docker process for streamId: {}", streamId);
        logger.debug("FFmpeg Docker command: {}", String.join(" ", processBuilder.command())); // This will now show the correct command

        Process ffmpegProcess = processBuilder.start();

        // Gobble error stream (FFmpeg's main output is usually to stderr)
        StreamGobbler errorGobbler = new StreamGobbler(ffmpegProcess.getErrorStream(), msg -> {
            // Keep as ERROR for now to catch all output, but consider INFO for non-critical messages
            logger.error("FFmpeg stderr: {}", msg);
        });
        Executors.newSingleThreadExecutor().submit(errorGobbler);

        // Gobble output stream (FFmpeg usually doesn't output much to stdout)
        StreamGobbler outputGobbler = new StreamGobbler(ffmpegProcess.getInputStream(), msg -> {
            logger.info("FFmpeg stdout: {}", msg);
        });
        Executors.newSingleThreadExecutor().submit(outputGobbler);

        activeFFmpegProcesses.put(streamId, ffmpegProcess);
        ffmpegInputStreams.put(streamId, ffmpegProcess.getOutputStream());

        // Monitor FFmpeg process exit
        new Thread(() -> {
            try {
                int exitCode = ffmpegProcess.waitFor();
                logger.info("FFmpeg process for streamId {} exited with code {}", streamId, exitCode);
                cleanupFFmpegProcess(streamId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("FFmpeg process for streamId {} was interrupted: {}", streamId, e.getMessage());
            }
        }).start();

        logger.info("FFmpeg process started successfully for streamId: {}", streamId);
        return ffmpegProcess.getOutputStream();
    }

    public void stopFFmpegProcess(String streamId) {
        Process ffmpegProcess = activeFFmpegProcesses.get(streamId);
        if (ffmpegProcess != null) {
            logger.info("Stopping FFmpeg process for streamId: {}", streamId);
            try {
                OutputStream os = ffmpegInputStreams.get(streamId);
                if (os != null) {
                    os.close(); // Close stdin to FFmpeg, signaling end of stream
                    logger.debug("Closed FFmpeg stdin for streamId: {}", streamId);
                }
                // Give FFmpeg a bit of time to finish processing and close files
                boolean exited = ffmpegProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    logger.warn("FFmpeg process for streamId {} did not exit gracefully within 5s, forcing destruction.", streamId);
                    ffmpegProcess.destroyForcibly(); // Force kill if it doesn't exit
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error stopping FFmpeg process for streamId {}: {}", streamId, e.getMessage());
                ffmpegProcess.destroyForcibly(); // Ensure it's killed even on error
            } finally {
                cleanupFFmpegProcess(streamId);
            }
        }
    }

    private void cleanupFFmpegProcess(String streamId) {
        activeFFmpegProcesses.remove(streamId);
        ffmpegInputStreams.remove(streamId);
        logger.info("Cleaned up FFmpeg process entry for streamId: {}", streamId);
    }

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