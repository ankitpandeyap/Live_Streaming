package com.robspecs.live.repository;

import com.robspecs.live.entities.Video;
import com.robspecs.live.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {

    // Find all videos by a specific user
    List<Video> findByUploadUser(User uploadUser);

    // Find a video by its ID and ensure it belongs to a specific user
    Optional<Video> findByVideoIdAndUploadUser(Long videoId, User uploadUser);
}