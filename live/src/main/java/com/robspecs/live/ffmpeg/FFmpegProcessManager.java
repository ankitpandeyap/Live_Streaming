// src/main/java/com/robspecs/live/ffmpeg/FFmpegProcessManager.java
package com.robspecs.live.ffmpeg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
// import java.util.Comparator; // Not used for current cleanup logic, can be removed if not needed elsewhere

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

    private static final String FFMPEG_DOCKER_IMAGE = "ffmpeg-hls-streamer";

    /**
     * Starts a new FFmpeg Docker process for a given streamId.
     * The process reads raw video/audio data from its stdin and outputs HLS segments
     * for live preview AND directly saves the original WebM file for VOD recording.
     *
     * @param streamId The unique identifier for the stream.
     * @return The OutputStream connected to FFmpeg's stdin.
     * @throws IOException If there's an error starting the process or creating directories.
     */
    public OutputStream startFFmpegProcess(String streamId) throws IOException {
        Path streamOutputPath = Paths.get(videoBasePath, streamId);
        Files.createDirectories(streamOutputPath); // Ensure the directory exists on the host

        String containerHlsOutputPath = "/app/hls_output"; // Path inside the Docker container for HLS
        String containerVodOutputPath = "/app/vod_output"; // Path inside the Docker container for VOD
        // No separate 360p folder needed as we're saving the original WebM
        // Path vodOutputHostPath = streamOutputPath.resolve("360p"); // REMOVE OR ADJUST THIS IF YOU WANT A DEDICATED VOD FOLDER
        // Files.createDirectories(vodOutputHostPath); // REMOVE OR ADJUST THIS

        // For VOD, we will now save directly into the streamId folder as WebM
        Path vodOutputHostPath = streamOutputPath; // VOD will be saved directly in the streamId folder
        Files.createDirectories(vodOutputHostPath); // Ensure streamId folder exists

        // Docker volume mounts (for HLS and VOD)
        String hlsVolumeMount = streamOutputPath.toAbsolutePath().normalize().toString().replace("\\", "/") + ":" + containerHlsOutputPath;
        String vodVolumeMount = vodOutputHostPath.toAbsolutePath().normalize().toString().replace("\\", "/") + ":" + containerVodOutputPath;


        // Build the FFmpeg arguments as a List of Strings
        List<String> ffmpegArgs = new ArrayList<>();

        // --- Global Input Configuration ---
        ffmpegArgs.add("-f");
        ffmpegArgs.add("webm"); // Explicitly set input format to WebM
        ffmpegArgs.add("-probesize");
        ffmpegArgs.add("10M"); // Analyze 10MB of input to better detect format
        ffmpegArgs.add("-analyzeduration");
        ffmpegArgs.add("10M"); // Analyze for 10 million microseconds to better detect stream properties
        ffmpegArgs.add("-i");
        ffmpegArgs.add("pipe:0"); // Reads from stdin (where RedisFeederService will write)


        // --- Output 1: Live HLS Stream (Original Quality Preview - 720p H.264) ---
        // This section remains largely the same, optimized for live low-latency preview
        ffmpegArgs.add("-map");
        ffmpegArgs.add("0:v"); // Map video stream from input 0
        ffmpegArgs.add("-map");
        ffmpegArgs.add("0:a"); // Map audio stream from input 0

        ffmpegArgs.add("-c:v");
        ffmpegArgs.add("libx264");
        ffmpegArgs.add("-preset");
        ffmpegArgs.add("veryfast");
        ffmpegArgs.add("-tune"); // Added -tune zerolatency for live streaming
        ffmpegArgs.add("zerolatency");
        ffmpegArgs.add("-b:v");
        ffmpegArgs.add("1500k"); // Target video bitrate (adjust as needed for original quality)
        ffmpegArgs.add("-maxrate");
        ffmpegArgs.add("2000k");
        ffmpegArgs.add("-bufsize");
        ffmpegArgs.add("3000k");
        ffmpegArgs.add("-profile:v");
        ffmpegArgs.add("high");
        ffmpegArgs.add("-level");
        ffmpegArgs.add("4.0");
        ffmpegArgs.add("-sc_threshold");
        ffmpegArgs.add("0");
        ffmpegArgs.add("-g");
        ffmpegArgs.add("48"); // Keyframe interval (2 seconds * 24 fps = 48 frames)
        ffmpegArgs.add("-keyint_min");
        ffmpegArgs.add("48");
        ffmpegArgs.add("-r");
        ffmpegArgs.add("24"); // IMPORTANT: Ensure input frame rate matches this, or remove '-r' for auto-detection if variable.
        ffmpegArgs.add("-pix_fmt");
        ffmpegArgs.add("yuv420p");

        ffmpegArgs.add("-c:a");
        ffmpegArgs.add("aac");
        ffmpegArgs.add("-b:a");
        ffmpegArgs.add("128k");
        ffmpegArgs.add("-ac");
        ffmpegArgs.add("1");

        ffmpegArgs.add("-f");
        ffmpegArgs.add("hls");
        ffmpegArgs.add("-hls_time");
        ffmpegArgs.add("2");
        ffmpegArgs.add("-hls_list_size");
        ffmpegArgs.add("3");
        ffmpegArgs.add("-hls_flags");
        ffmpegArgs.add("delete_segments+append_list");
        ffmpegArgs.add("-hls_init_time");
        ffmpegArgs.add("6");
        ffmpegArgs.add("-hls_segment_filename");
        ffmpegArgs.add(containerHlsOutputPath + "/segment_%d.ts");
        ffmpegArgs.add(containerHlsOutputPath + "/index.m3u8"); // Output HLS master playlist file


        // --- Output 2: Direct Save of Original WebM for VOD Recording ---
        // This output is designed to be a persistent recording in the original incoming format
        ffmpegArgs.add("-map");
        ffmpegArgs.add("0:v"); // Map video stream from input 0
        ffmpegArgs.add("-map");
        ffmpegArgs.add("0:a"); // Map audio stream from input 0

        ffmpegArgs.add("-c:v");
        ffmpegArgs.add("copy"); // OPTIMIZATION: Copy video stream without re-encoding
        ffmpegArgs.add("-c:a");
        ffmpegArgs.add("copy"); // OPTIMIZATION: Copy audio stream without re-encoding
        // Removed -preset, -b:v, -maxrate, -bufsize, -vf, -profile:v, -level, -r, -pix_fmt, -ac, -ar as they are for encoding
        ffmpegArgs.add("-f");
        ffmpegArgs.add("webm"); // Output format is WebM
        // Consider adding '-frag_duration 10000000' or similar if you need it to be playable during recording
        // For simple save, no special movflags needed as it's not MP4
        ffmpegArgs.add(containerVodOutputPath + "/" + streamId + "_original.webm"); // Output WebM file name


        // Docker command to run the FFmpeg container and pass arguments directly
        List<String> dockerCommand = new ArrayList<>();
        dockerCommand.add("docker");
        dockerCommand.add("run");
        dockerCommand.add("-i"); // Interactive mode to allow stdin
        dockerCommand.add("--rm"); // Automatically remove container on exit
        dockerCommand.add("-v");
        dockerCommand.add(hlsVolumeMount); // Mount for HLS output
        dockerCommand.add("-v");
        dockerCommand.add(vodVolumeMount); // Mount for VOD output (now directly the streamId folder)
        dockerCommand.add(FFMPEG_DOCKER_IMAGE); // The Docker image to use
        dockerCommand.addAll(ffmpegArgs); // Add all FFmpeg arguments directly after the image name

        ProcessBuilder processBuilder = new ProcessBuilder(dockerCommand);

        logger.info("Starting FFmpeg Docker process for streamId: {}", streamId);
        logger.debug("FFmpeg Docker command: {}", String.join(" ", processBuilder.command()));

        Process ffmpegProcess = processBuilder.start();

        // Start threads to consume stdout and stderr to prevent blocking the FFmpeg process
        // FFmpeg's verbose output is usually to stderr.
        Executors.newSingleThreadExecutor().submit(new StreamGobbler(ffmpegProcess.getErrorStream(), msg -> {
            logger.error("FFmpeg stderr: {}", msg); // Keep as ERROR for now to capture all output during debugging.
        }));

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
                // Call cleanup after process exits, ensuring HLS files are removed
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
                // This is especially important for the MP4 output to be finalized correctly.
                boolean exited = ffmpegProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS); // Increased timeout for MP4 finalization
                if (!exited) {
                    logger.warn("FFmpeg process for streamId {} did not exit gracefully within 10s, forcing destruction.", streamId);
                    ffmpegProcess.destroyForcibly(); // Force kill if it doesn't exit within timeout
                }
            } catch (IOException | InterruptedException e) {
                logger.error("Error stopping FFmpeg process for streamId {}: {}", streamId, e.getMessage());
                ffmpegProcess.destroyForcibly(); // Ensure it's killed even on error
            } finally {
                // Cleanup is now called by the monitoring thread *after* process exit (or forced destroy)
                // This `finally` block ensures resources are removed from maps even if `waitFor` fails,
                // but the HLS file cleanup logic should ideally run after the process has fully stopped
                // which is handled by the separate thread calling `cleanupFFmpegProcess`.
            }
        } else {
            logger.info("No active FFmpeg process found for streamId: {}. No action needed.", streamId);
        }
    }

    /**
     * Cleans up internal maps after an FFmpeg process has stopped or been destroyed,
     * and deletes any remaining HLS files for the stream.
     *
     * @param streamId The unique identifier of the stream.
     */
    private void cleanupFFmpegProcess(String streamId) {
        activeFFmpegProcesses.remove(streamId);
        ffmpegInputStreams.remove(streamId);
        logger.info("Cleaned up FFmpeg process entry for streamId: {}", streamId);

        // --- Delete lingering HLS files ---
        Path hlsOutputPath = Paths.get(videoBasePath, streamId);
        if (Files.exists(hlsOutputPath) && Files.isDirectory(hlsOutputPath)) {
            try {
                // Delete index.m3u8 and any remaining .ts files directly under the streamId folder
                Files.walk(hlsOutputPath)
                     .filter(path -> path.getFileName().toString().endsWith(".m3u8") || path.getFileName().toString().endsWith(".ts"))
                     .forEach(path -> {
                         try {
                             Files.deleteIfExists(path);
                             logger.debug("Deleted lingering HLS file: {}", path);
                         } catch (IOException e) {
                             logger.warn("Could not delete HLS file {}: {}", path, e.getMessage());
                         }
                     });
                // Note: We are NOT deleting the entire streamId directory or its '360p' subfolder.
                // The VOD .webm file will remain directly in the streamId folder.
            } catch (IOException e) {
                logger.error("Error cleaning up HLS files for streamId {}: {}", streamId, e.getMessage(), e);
            }
        }
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