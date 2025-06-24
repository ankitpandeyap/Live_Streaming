package com.robspecs.live.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "videos", uniqueConstraints = {
        // As per your explicit instructions, the 'Video' entity does not currently have a 'videoName' field.
        // Therefore, this unique constraint cannot be applied without adding a 'videoName' field.
        // If you intend to add 'videoName' or use another field (e.g., originalFilePath) for uniqueness,
        // please provide updated instructions for the Video entity's fields.
        // @UniqueConstraint(name = "uq_video_name_user", columnNames = { "videoName", "upload_user_id" })
}, indexes = {
        // As per your explicit instructions, the 'Video' entity does not currently have a 'videoName' field.
        // Therefore, this index cannot be applied without adding a 'videoName' field.
        // @Index(name = "idx_video_name", columnList = "videoName"
		// This index is implemented as 'upload_user_id' column exists due to the 'uploadUser' relationship.
        @Index(name = "idx_upload_user_id", columnList = "upload_user_id")
})
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long videoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_user_id", nullable = false)
    private User uploadUser;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(nullable = false)
    private String originalFilePath;

    // Constructors
    public Video() {
        this.uploadedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public User getUploadUser() {
        return uploadUser;
    }

    public void setUploadUser(User uploadUser) {
        this.uploadUser = uploadUser;
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