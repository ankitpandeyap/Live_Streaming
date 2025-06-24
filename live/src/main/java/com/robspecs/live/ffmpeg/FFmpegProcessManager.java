// src/main/java/com/robspecs/live/ffmpeg/FFmpegProcessManager.java
package com.robspecs.live.ffmpeg;

import com.robspecs.live.service.VideoService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files; // Import Files
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator; // Import Comparator
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream; // Import Stream

@Component
public class FFmpegProcessManager {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegProcessManager.class);

    // Map to hold running FFmpeg processes and their input streams for writing data
    private final Map<String, Process> ffmpegProcesses = new ConcurrentHashMap<>();
    private final Map<String, OutputStream> ffmpegInputStreams = new ConcurrentHashMap<>();
    private final ExecutorService logReadersExecutor = Executors.newCachedThreadPool();

    @Value("${files.video.base-path}")
    private String videoBasePath;

    private Path fileStorageLocation; // Derived from videoBasePath

    // Lazy inject VideoService to prevent circular dependency at startup
    private final VideoService videoService;

    public FFmpegProcessManager(@Lazy VideoService videoService) {
        this.videoService = videoService;
    }

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(videoBasePath).toAbsolutePath().normalize();
        logger.info("FFmpegProcessManager initialized. Video base path: {}", fileStorageLocation);
    }

    /**
     * Starts a new Dockerized FFmpeg process for a given stream ID.
     * This FFmpeg process will:
     * 1. Read raw WebM data from its standard input (pipe:0).
     * 2. Transcode the stream into HLS segments for live playback.
     * 3. Copy the original WebM stream to a VOD file for later playback.
     *
     * @param streamId The unique ID for the live stream.
     * @return The OutputStream connected to FFmpeg's stdin, or null if the process failed to start.
     */
    public OutputStream startFFmpegProcess(String streamId) { // CHANGED RETURN TYPE TO OutputStream
        if (ffmpegProcesses.containsKey(streamId)) {
            logger.warn("FFmpeg process for streamId {} is already running.", streamId);
            return ffmpegInputStreams.get(streamId); // Return existing stream if already running
        }

        logger.info("Starting FFmpeg Docker process for streamId: {}", streamId);

        // Define output directories within the mounted volume
        // These paths are *inside* the Docker container
        String hlsOutputContainerPath = "/app/hls_output";
        String vodOutputContainerPath = "/app/vod_output";

        // Construct the full Docker run command for FFmpeg
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("-i"); // Interactive mode to keep stdin open
        command.add("--rm"); // Remove container after exit
        // Mount host video base path to container's output paths
        command.add("-v");
        command.add(fileStorageLocation.resolve(streamId).toString() + ":" + hlsOutputContainerPath);
        command.add("-v");
        command.add(fileStorageLocation.resolve(streamId).toString() + ":" + vodOutputContainerPath);
        command.add("ffmpeg-hls-streamer"); // Your custom Docker image name

        // FFmpeg input options (reading from stdin)
        command.add("-f");
        command.add("webm"); // Input format
        command.add("-probesize");
        command.add("10M"); // Analyze up to 10MB of data to detect stream info
        command.add("-analyzeduration");
        command.add("10M"); // Analyze up to 10M duration for stream info
        command.add("-i");
        command.add("pipe:0"); // Read from standard input

        // --- IMPORTANT: Timestamp handling for live HLS output ---
        // -use_wallclock_as_timestamps: Use current time as timestamp, crucial for live streams
        // where input DTS might be non-monotonic due to network jitter or browser behavior.
        // This helps FFmpeg create more consistent HLS segments.
        command.add("-use_wallclock_as_timestamps");
        command.add("1");
        command.add("-correct_ts_overflow"); // NEW: Handles timestamp overflows
        command.add("1");                   // NEW: Enables overflow correction
        command.add("-vsync");              // NEW: Forces constant frame rate output
        command.add("cfr");                 // NEW: 'cfr' for constant frame rate


        // --- HLS Output Configuration (for live playback) ---
        command.add("-map");
        command.add("0:v"); // Map video stream from input
        command.add("-map");
        command.add("0:a"); // Map audio stream from input
        command.add("-c:v");
        command.add("libx264"); // Video codec: H.264
        command.add("-preset");
        command.add("veryfast"); // Encoding preset: very fast (for low latency)
        command.add("-tune");
        command.add("zerolatency"); // Tune for zero latency
        command.add("-b:v");
        command.add("1500k"); // Video bitrate
        command.add("-maxrate");
        command.add("2000k"); // Maximum video bitrate
        command.add("-bufsize");
        command.add("3000k"); // Coder buffer size
        command.add("-profile:v");
        command.add("high"); // H.264 profile
        command.add("-level");
        command.add("4.0"); // H.264 level
        command.add("-sc_threshold");
        command.add("0"); // Disable scene change detection for fixed GOP
        command.add("-g");
        command.add("48"); // GOP size (keyframe interval) - 2 seconds for 24fps
        command.add("-keyint_min");
        command.add("24"); // Minimum keyframe interval - ensure keyframes often
        command.add("-r");
        command.add("24"); // Output frame rate
        command.add("-pix_fmt");
        command.add("yuv420p"); // Pixel format
        command.add("-c:a");
        command.add("aac"); // Audio codec: AAC
        command.add("-b:a");
        command.add("128k"); // Audio bitrate
        command.add("-ac");
        command.add("1"); // Audio channels: mono
        command.add("-f");
        command.add("hls"); // Output format: HLS
        command.add("-hls_time");
        command.add("2"); // HLS segment duration (2 seconds)
        command.add("-hls_list_size");
        command.add("3"); // Number of segments in the playlist (for live window)
        command.add("-hls_flags");
        command.add("delete_segments+append_list"); // Delete old segments, append new ones
        // Removed -hls_init_time as it conflicts with append_list mode as per FFmpeg warning.
        command.add("-hls_segment_filename");
        command.add(hlsOutputContainerPath + "/segment_%d.ts");
        command.add(hlsOutputContainerPath + "/index.m3u8");

        // --- VOD Output Configuration (recording original WebM) ---
        command.add("-map");
        command.add("0:v"); // Map video stream from input
        command.add("-map");
        command.add("0:a"); // Map audio stream from input
        command.add("-c:v");
        command.add("copy"); // Copy video stream (no re-encoding)
        command.add("-c:a");
        command.add("copy"); // Copy audio stream (no re-encoding)
        command.add("-f");
        command.add("webm"); // Output format: WebM
        command.add(vodOutputContainerPath + "/" + streamId + "_original.webm");

        try {
            // Create the directory for this stream's output if it doesn't exist
            Path streamDirectory = fileStorageLocation.resolve(streamId);
            if (!streamDirectory.toFile().exists()) {
                java.nio.file.Files.createDirectories(streamDirectory);
                logger.debug("Created stream directory: {}", streamDirectory);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false); // Do not merge stdout and stderr

            Process process = pb.start();
            OutputStream processOutputStream = process.getOutputStream(); // Get the OutputStream

            ffmpegProcesses.put(streamId, process);
            ffmpegInputStreams.put(streamId, processOutputStream); // Store the OutputStream

            // Consume FFmpeg's stdout (if any) and stderr to prevent buffer blocking
            logReadersExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("FFmpeg stdout: {}", line);
                    }
                } catch (IOException e) {
                    logger.error("Error reading FFmpeg stdout for streamId {}: {}", streamId, e.getMessage(), e);
                }
            });

            logReadersExecutor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Log FFmpeg's stderr output, which often contains progress and warnings
                        logger.error("FFmpeg stderr: {}", line);
                    }
                } catch (IOException e) {
                    logger.error("Error reading FFmpeg stderr for streamId {}: {}", streamId, e.getMessage(), e);
                }
            });

            logger.info("FFmpeg process started successfully for streamId: {}", streamId);
            return processOutputStream; // Return the OutputStream on success
        } catch (IOException e) {
            logger.error("Failed to start FFmpeg process for streamId {}: {}", streamId, e.getMessage(), e);
            ffmpegProcesses.remove(streamId);
            ffmpegInputStreams.remove(streamId);
            return null; // Return null on failure
        }
    }

    /**
     * Writes raw media data to the FFmpeg process's stdin for a given stream ID.
     *
     * @param streamId The ID of the stream.
     * @param data The byte array of raw media data.
     */
    public void writeFrameToFFmpeg(String streamId, byte[] data) {
        OutputStream os = ffmpegInputStreams.get(streamId);
        if (os != null) {
            try {
                os.write(data);
                // os.flush(); // Flush aggressively if needed, but might reduce performance.
                        // Often not needed for continuous streams as FFmpeg buffers.
            } catch (IOException e) {
                logger.error("Error writing frame to FFmpeg process for streamId {}: {}", streamId, e.getMessage(), e);
                // Consider stopping the process if writing fails persistently
            }
        } else {
            logger.warn("FFmpeg process input stream not found for streamId: {}", streamId);
        }
    }

    /**
     * Stops the FFmpeg process for a given stream ID and performs cleanup.
     *
     * @param streamId The ID of the stream to stop.
     */
    public void stopFFmpegProcess(String streamId) {
        Process process = ffmpegProcesses.remove(streamId);
        OutputStream os = ffmpegInputStreams.remove(streamId);

        if (os != null) {
            try {
                os.close(); // Close the input stream to signal EOF to FFmpeg
                logger.info("Closed FFmpeg input stream for streamId: {} (via Redis feeder).", streamId);
            } catch (IOException e) {
                logger.error("Error closing FFmpeg input stream for streamId {}: {}", streamId, e.getMessage(), e);
            }
        }

        if (process != null) {
            // Give FFmpeg a bit of time to finish processing after stdin is closed
            try {
                boolean terminated = process.waitFor(10, TimeUnit.SECONDS); // Wait for 10 seconds
                if (terminated) {
                    logger.info("FFmpeg process for streamId {} terminated gracefully.", streamId);
                } else {
                    // If it didn't terminate, destroy it forcibly
                    process.destroyForcibly();
                    logger.warn("FFmpeg process for streamId {} did not terminate, forcibly destroyed.", streamId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logger.warn("Interrupted while waiting for FFmpeg process to terminate for streamId {}.", streamId);
                process.destroyForcibly();
            } finally {
                // IMPORTANT: Clean up HLS segments here after the process is stopped
                cleanupHlsSegments(streamId);
            }
        } else {
            logger.warn("No FFmpeg process found for streamId: {}", streamId);
        }
        // Assuming video record creation is handled elsewhere, e.g., on WebSocket close or stream stop signal.
    }

    /**
     * Deletes the HLS segments and manifest file for a given stream ID.
     * This method should be called after the FFmpeg process for that stream has stopped.
     *
     * @param streamId The ID of the stream for which to delete HLS files.
     */
    private void cleanupHlsSegments(String streamId) {
        Path hlsDirectory = fileStorageLocation.resolve(streamId);
        if (Files.exists(hlsDirectory) && Files.isDirectory(hlsDirectory)) {
            logger.info("Initiating HLS segment cleanup for streamId: {}", streamId);
            try (Stream<Path> walk = Files.walk(hlsDirectory)) {
                walk.sorted(Comparator.reverseOrder()) // Delete files before directories
                    .filter(p -> p.getFileName().toString().endsWith(".ts") || p.getFileName().toString().endsWith(".m3u8"))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                            logger.debug("Deleted HLS file: {}", p.getFileName());
                        } catch (IOException e) {
                            logger.error("Failed to delete HLS file {}: {}", p, e.getMessage());
                        }
                    });
                // Optionally, delete the stream-specific directory if it's empty or only contains the .webm file
                // If you want to keep the .webm recording, check if it's the only remaining file.
                // For simplicity, let's assume we keep the directory with the .webm.
                // If you want to delete the directory only if it becomes empty:
                if (Files.list(hlsDirectory).findAny().isEmpty()) {
                     Files.delete(hlsDirectory);
                     logger.info("Deleted empty HLS directory: {}", hlsDirectory);
                } else {
                    logger.info("HLS directory {} not empty after cleanup (likely contains VOD .webm).", hlsDirectory);
                }

            } catch (IOException e) {
                logger.error("Error during HLS segment cleanup for streamId {}: {}", streamId, e.getMessage());
            }
        } else {
            logger.warn("HLS directory not found for cleanup for streamId: {}", streamId);
        }
    }


    @PreDestroy
    public void cleanupAllFFmpegProcesses() {
        logger.info("Shutting down FFmpegProcessManager. Stopping all running FFmpeg processes.");
        // Create a copy to avoid ConcurrentModificationException during iteration and removal
        new ArrayList<>(ffmpegProcesses.keySet()).forEach(this::stopFFmpegProcess);
        logReadersExecutor.shutdownNow(); // Attempt to stop all log reader threads
    }
}