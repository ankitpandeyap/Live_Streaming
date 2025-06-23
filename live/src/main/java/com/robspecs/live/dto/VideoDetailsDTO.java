package com.robspecs.live.dto;

import java.time.LocalDateTime;

public class VideoDetailsDTO {

    private Long videoId;
    private Long uploadUserId; // ID of the user who owns it
    private String uploadUsername; // Username of the owner
    private LocalDateTime uploadedAt;
    private String originalFilePath;

    // Constructors, Getters, and Setters

    public VideoDetailsDTO() {}

    public VideoDetailsDTO(Long videoId, Long uploadUserId, String uploadUsername, LocalDateTime uploadedAt, String originalFilePath) {
        this.videoId = videoId;
        this.uploadUserId = uploadUserId;
        this.uploadUsername = uploadUsername;
        this.uploadedAt = uploadedAt;
        this.originalFilePath = originalFilePath;
    }

    // Getters and Setters
    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Long getUploadUserId() {
        return uploadUserId;
    }

    public void setUploadUserId(Long uploadUserId) {
        this.uploadUserId = uploadUserId;
    }

    public String getUploadUsername() {
        return uploadUsername;
    }

    public void setUploadUsername(String uploadUsername) {
        this.uploadUsername = uploadUsername;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getOriginalFilePath() {
        return originalFilePath;
    }

    public void setOriginalFilePath(String originalFilePath) {
        this.originalFilePath = originalFilePath;
    }
}