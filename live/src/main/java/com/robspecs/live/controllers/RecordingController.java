// src/main/java/com/robspecs/live/controllers/RecordingController.java
package com.robspecs.live.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recordings")
public class RecordingController {

    private static final Logger logger = LoggerFactory.getLogger(RecordingController.class);

    @Value("${files.video.base-path}")
    private String videoBasePath;

    /**
     * Serves the HLS master playlist for a recorded stream.
     * This endpoint will later be secured to allow only the stream owner to access it.
     * Example: GET /api/recordings/{streamId}/index.m3u8
     */
    @GetMapping("/{streamId}/index.m3u8")
    public ResponseEntity<Resource> getRecordedHlsManifest(@PathVariable String streamId) {
        // --- PHASE 4.2 Placeholder: JWT Validation & Stream Ownership Check ---
        // In Phase 4.2, you will add logic here to:
        // 1. Get the authenticated user's ID from the JWT.
        // 2. Query your database to check if this user is the owner of 'streamId'.
        // 3. If not authorized, return ResponseEntity.status(403).build();
        logger.info("Attempting to access recorded HLS manifest for streamId: {}", streamId);
        // For now, we proceed without authorization check.
        // ---------------------------------------------------------------------

        Path filePath = Paths.get(videoBasePath, streamId, "index.m3u8").normalize();

        return serveHlsFile(filePath, MediaType.valueOf("application/x-mpegURL"), streamId, "index.m3u8");
    }

    /**
     * Serves HLS video segments for a recorded stream.
     * This endpoint will also later be secured to allow only the stream owner to access it.
     * Example: GET /api/recordings/{streamId}/segment_0.ts
     */
    @GetMapping("/{streamId}/{segmentName}.ts")
    public ResponseEntity<Resource> getRecordedHlsSegment(
            @PathVariable String streamId,
            @PathVariable String segmentName) {

        // --- PHASE 4.2 Placeholder: JWT Validation & Stream Ownership Check ---
        // Same authorization logic as for the manifest will apply here.
        logger.info("Attempting to access recorded HLS segment {} for streamId: {}", segmentName, streamId);
        // For now, we proceed without authorization check.
        // ---------------------------------------------------------------------

        // Basic validation to prevent path traversal (though Path.normalize() helps)
        if (!segmentName.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warn("Invalid segment name format requested: {}", segmentName);
            return ResponseEntity.badRequest().build();
        }

        Path filePath = Paths.get(videoBasePath, streamId, segmentName + ".ts").normalize();

        return serveHlsFile(filePath, MediaType.valueOf("video/MP2T"), streamId, segmentName + ".ts");
    }

    private ResponseEntity<Resource> serveHlsFile(Path filePath, MediaType mediaType, String streamId, String fileName) {
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            logger.warn("File not found or not readable: {}", filePath);
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new FileSystemResource(filePath.toFile());

            // Set content type and ETag/Last-Modified for caching (optional but good practice)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(mediaType);
            headers.setCacheControl("no-cache"); // For live-ish segments, but for recordings, might be longer
            headers.setLastModified(Files.getLastModifiedTime(filePath).toMillis());
            headers.setETag(Long.toString(Files.size(filePath))); // Simple ETag

            logger.debug("Serving recorded HLS file: {}", filePath);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
        } catch (IOException e) {
            logger.error("Error serving recorded HLS file {}: {}", filePath, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}