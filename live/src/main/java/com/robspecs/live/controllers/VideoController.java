package com.robspecs.live.controllers;

import com.robspecs.live.dto.VideoDetailsDTO;
import com.robspecs.live.entities.User;
import com.robspecs.live.entities.Video;
import com.robspecs.live.exceptions.FileNotFoundException;
import com.robspecs.live.service.FileStorageService;
import com.robspecs.live.service.VideoService;
import com.robspecs.live.utils.JWTUtils;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.InputStreamResource; // <-- New import
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream; // <-- New import
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/recorded-streams")
public class VideoController {

    private static final Logger logger = LoggerFactory.getLogger(VideoController.class);

    private final VideoService videoService;
    private final FileStorageService fileStorageService;
    private final JWTUtils jwtUtils;

    public VideoController(VideoService videoService, FileStorageService fileStorageService, JWTUtils jwtUtils) {
        this.videoService = videoService;
        this.fileStorageService = fileStorageService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * Retrieves details of a specific recorded stream.
     * Only the owner of the stream can access this record.
     */
    @GetMapping("/{recordId}")
    public ResponseEntity<VideoDetailsDTO> getRecordedStreamDetails(@PathVariable("recordId") Long recordId,
                                                                  @AuthenticationPrincipal User currentUser) {
        logger.info("Fetching recorded stream details for ID: {} by user: {}", recordId, currentUser.getUsername());
        try {
            VideoDetailsDTO recordDetails = videoService.getLiveStreamRecordById(recordId, currentUser);
            return ResponseEntity.ok(recordDetails);
        } catch (FileNotFoundException e) {
            logger.warn("Recorded stream {} not found for user {}: {}", recordId, currentUser.getUsername(), e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            logger.warn("Access denied for recorded stream {} to user {}: {}", recordId, currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (Exception e) {
            logger.error("Error fetching recorded stream details for ID {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Lists all recorded streams belonging to the current authenticated user.
     */
    @GetMapping("/my-records")
    public ResponseEntity<List<VideoDetailsDTO>> getMyRecordedStreams(@AuthenticationPrincipal User currentUser) {
        logger.info("Fetching recorded streams for current user: {}", currentUser.getUsername());
        try {
            List<VideoDetailsDTO> userRecords = videoService.getMyLiveStreamRecords(currentUser);
            return ResponseEntity.ok(userRecords);
        } catch (Exception e) {
            logger.error("Error fetching recorded streams for user {}: {}", currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Deletes a recorded stream and its associated files.
     * Only the owner of the stream can delete it.
     */
    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> deleteRecordedStream(@PathVariable("recordId") Long recordId,
                                                   @AuthenticationPrincipal User currentUser) {
        logger.info("Received delete request for recorded stream ID: {} by user: {}", recordId, currentUser.getUsername());
        try {
            videoService.deleteLiveStreamRecord(recordId, currentUser);
            return ResponseEntity.noContent().build(); // Successful deletion: 204 No Content
        } catch (FileNotFoundException e) {
            logger.warn("Recorded stream not found for deletion: {}", recordId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (SecurityException e) {
            logger.warn("Access denied for deleting recorded stream {} to user {}: {}", recordId, currentUser.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            logger.error("An unexpected error occurred during recorded stream deletion for ID {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Generates a signed URL for `.webm` streaming of a recorded live stream.
     * The generated URL includes a short-lived JWT token as a query parameter.
     * This endpoint requires the requesting user to be authenticated and authorized to access the record.
     *
     * @param recordId The ID of the recorded live stream for which to generate the stream URL.
     * @param currentUser The authenticated user.
     * @return ResponseEntity containing the signed `.webm` stream URL.
     */
    @GetMapping("/{recordId}/stream-url")
    public ResponseEntity<String> generateWebmStreamUrl(@PathVariable("recordId") Long recordId,
                                                       @AuthenticationPrincipal User currentUser) {
        logger.info("Request to generate .webm stream URL for record ID: {} by user: {}", recordId, currentUser.getUsername());

        try {
            // 1. Validate user access to the recorded stream.
            // getActualLiveStreamEntity includes an ownership/admin authorization check.
            Video videoRecord = videoService.getActualLiveStreamEntity(recordId, currentUser);

            // 2. Infer "readiness" by checking if the .webm file exists at the expected path.
            // originalFilePath from DB will be like `streamId/streamId_original.webm`.
            String originalWebmPath = videoRecord.getOriginalFilePath();

            if (!fileStorageService.doesFileExist(originalWebmPath)) {
                logger.warn(".webm file not found for record ID {}. Processing might not be complete or files are missing. Path: {}", recordId, originalWebmPath);
                return ResponseEntity.status(HttpStatus.LOCKED).body("Recorded stream not ready for playback. File is missing."); // 423 Locked
            }
            logger.debug(".webm file found for record ID: {}. Path: {}", recordId, originalWebmPath);

            // 3. Generate a short-lived stream-specific JWT token
            long streamTokenExpiryMinutes = 15; // Set a short expiry for stream tokens
            String streamToken = jwtUtils.generateHlsToken(recordId, currentUser.getUserId(), streamTokenExpiryMinutes); // HLS token method reused for general stream token

            if (streamToken == null) {
                logger.error("Failed to generate stream token for record ID: {}", recordId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to generate stream token.");
            }
            logger.debug("Stream token generated for record ID: {}.", recordId);

            // 4. Construct the full signed .webm stream URL
            // This URL will be like: /api/recorded-streams/{recordId}/file?token={streamToken}
            String baseUrl = "/api/recorded-streams"; // Streaming base path
            String signedUrl = String.format("%s/%d/file?token=%s", baseUrl, recordId, streamToken);
            logger.info("Generated signed .webm stream URL for record ID: {}: {}", recordId, signedUrl);

            return ResponseEntity.ok(signedUrl);

        } catch (FileNotFoundException e) {
            logger.warn("Recorded stream not found when generating stream URL for ID: {}. Message: {}", recordId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (SecurityException e) {
            logger.warn("Access denied for user {} to record ID {} when generating stream URL. Message: {}", currentUser.getUsername(), recordId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            logger.error("An unexpected error occurred during stream URL generation for record ID: {} by user {}: {}", recordId, currentUser.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An unexpected error occurred while generating the stream URL.");
        }
    }

    /**
     * Endpoint to stream the recorded `.webm` file.
     * This endpoint is intended to be accessed with the JWT token generated by `generateWebmStreamUrl`.
     * The `StreamTokenValidationFilter` (to be implemented) should handle the token validation.
     * It supports byte range requests so players can seek.
     *
     * @param recordId The ID of the recorded stream.
     * @param streamToken Streaming authorization JWT token (provided as query parameter).
     * @param request HttpServletRequest for byte range headers and logging URI.
     * @return ResponseEntity containing the `.webm` content.
     */
    @GetMapping("/{recordId}/file")
    public ResponseEntity<Resource> streamWebmFile(
            @PathVariable("recordId") Long recordId,
            @RequestParam(name = "token", required = false) String streamToken,
            HttpServletRequest request) {

        logger.info("Received .webm stream request for record ID: {}. Stream token present: {}", recordId, streamToken != null);

        try {
            // Load the .webm file Resource with ownership/authorization check via VideoService.
            // The JWT filter will have already validated the user from the token,
            // so we pass null for currentUser here as `prepareWebmStream` will get the User ID from the token's context.
            Resource resource = videoService.prepareWebmStream(recordId, null);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("video/webm"));
            headers.setCacheControl(CacheControl.noCache()); // No-cache for dynamic stream content
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes"); // Indicate support for byte ranges

            // Ensure the resource is a file-backed resource to get its Path for Files.size
            if (!resource.isFile()) {
                logger.error("Resource for record ID {} is not a file-backed resource. Cannot serve byte ranges.", recordId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            Path filePath = resource.getFile().toPath(); // Get Path from Resource
            long fileSize = Files.size(filePath);

            String rangeHeader = request.getHeader(HttpHeaders.RANGE);

            if (rangeHeader != null) {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long rangeStart = Long.parseLong(ranges[0]);
                long rangeEnd = fileSize - 1; // Default to end of file if not specified

                if (ranges.length > 1 && !ranges[1].isEmpty()) {
                    rangeEnd = Long.parseLong(ranges[1]);
                }

                // Handle cases where rangeEnd is past the end of the file or rangeStart is invalid
                if (rangeStart < 0 || rangeStart >= fileSize) {
                    return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                            .header("Content-Range", "bytes */" + fileSize)
                            .build();
                }
                if (rangeEnd >= fileSize) {
                    rangeEnd = fileSize - 1;
                }

                long contentLength = rangeEnd - rangeStart + 1;

                headers.add("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + fileSize);
                headers.add("Content-Length", String.valueOf(contentLength));

                InputStream inputStream = Files.newInputStream(filePath);
                inputStream.skip(rangeStart); // Skip to the starting byte

                logger.debug("Serving partial .webm file for record ID {}: bytes {}-{}/{}", recordId, rangeStart, rangeEnd, fileSize);
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT) // 206 Partial Content
                        .headers(headers)
                        .body(new InputStreamResource(new BoundedInputStream(inputStream, contentLength))); // Custom BoundedInputStream
            } else {
                // Full content request
                headers.setContentLength(fileSize); // Set full content length

                logger.debug("Serving full .webm file for record ID: {}", recordId);
                return ResponseEntity.ok()
                        .headers(headers)
                        .body(new InputStreamResource(Files.newInputStream(filePath))); // Stream full file
            }

        } catch (FileNotFoundException e) {
            logger.warn("Stream file not found for record ID {}: {}", recordId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            logger.warn("Access denied for record ID {}: {}", recordId, e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        } catch (IOException e) {
            logger.error("IO error streaming record ID {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        } catch (NumberFormatException e) {
            logger.warn("Invalid Range header format for record ID {}: {}", recordId, request.getHeader(HttpHeaders.RANGE));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during streaming record ID {}: {}", recordId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Helper class to limit the bytes read from an InputStream,
     * useful for serving byte ranges.
     */
    private static class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long bytesRemaining;

        public BoundedInputStream(InputStream delegate, long length) {
            this.delegate = delegate;
            this.bytesRemaining = length;
        }

        @Override
        public int read() throws IOException {
            if (bytesRemaining <= 0) {
                return -1;
            }
            int result = delegate.read();
            if (result != -1) {
                bytesRemaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (bytesRemaining <= 0) {
                return -1;
            }
            int bytesToRead = (int) Math.min(len, bytesRemaining);
            int result = delegate.read(b, off, bytesToRead);
            if (result != -1) {
                bytesRemaining -= result;
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}