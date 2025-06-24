package com.robspecs.live.serviceImpl;

import com.robspecs.live.dto.VideoDetailsDTO;
import com.robspecs.live.entities.User;
import com.robspecs.live.entities.Video;
import com.robspecs.live.exceptions.FileNotFoundException;
import com.robspecs.live.repository.VideoRepository;
import com.robspecs.live.service.FileStorageService;
import com.robspecs.live.service.VideoService;
import com.robspecs.live.enums.Roles;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// KafkaTemplate का इम्पोर्ट हटाया गया
// VideoProcessingRequest का इम्पोर्ट हटाया गया
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.file.Files; // Files इम्पोर्ट किया गया
import java.nio.file.Path; // Path इम्पोर्ट किया गया
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger logger = LoggerFactory.getLogger(VideoServiceImpl.class);

    private final VideoRepository videoRepository;
    private final FileStorageService fileStorageService;
  

    public VideoServiceImpl(VideoRepository videoRepository, FileStorageService fileStorageService
                        ) { 
        this.videoRepository = videoRepository;
        this.fileStorageService = fileStorageService;
       
    }

    @Override
    @Transactional
    public VideoDetailsDTO createLiveStreamRecord(String streamId, User currentUser, String originalFilePath) {
        String normalizedOriginalFilePath = originalFilePath.replace("\\", "/");

        Video newVideo = new Video();
        newVideo.setUploadUser(currentUser);
        newVideo.setUploadedAt(LocalDateTime.now());
        newVideo.setOriginalFilePath(normalizedOriginalFilePath);

        Video savedVideo = videoRepository.save(newVideo);
        logger.info("New live stream record created for streamId: {}. User: {}. RecordId: {}", streamId, currentUser.getUsername(), savedVideo.getVideoId());

        

        return mapToVideoDetailsDTO(savedVideo);
    }

    @Override
    @Transactional
    public void deleteLiveStreamRecord(Long videoId, User currentUser) {
        logger.info("लाइव स्ट्रीम रिकॉर्ड ID: {} को उपयोगकर्ता: {} द्वारा हटाने का प्रयास कर रहा है", videoId, currentUser.getUsername());

        Video video = videoRepository.findByVideoIdAndUploadUser(videoId, currentUser)
                .orElseThrow(() -> {
                    logger.warn("लाइव स्ट्रीम रिकॉर्ड ID: {} उपयोगकर्ता: {} के लिए नहीं मिला या हटाने के लिए एक्सेस अस्वीकृत।", videoId, currentUser.getUsername());
                    return new FileNotFoundException("लाइव स्ट्रीम रिकॉर्ड नहीं मिला या आपके पास हटाने की अनुमति नहीं है।");
                });

        try {
            // कच्ची मूल .webm फ़ाइल हटाएँ
            if (video.getOriginalFilePath() != null && !video.getOriginalFilePath().isEmpty()) {
                fileStorageService.deleteFile(video.getOriginalFilePath());
                logger.info("रिकॉर्ड ID: {} के लिए मूल कच्ची लाइव स्ट्रीम फ़ाइल हटाई गई: {}", video.getOriginalFilePath(), videoId);
            }

            // उस डायरेक्टरी को भी हटाएँ जो इस streamId/videoId के लिए विशिष्ट थी (उदाहरण के लिए, videos-data/stream-id)
            Path fullOriginalFilePath = fileStorageService.getFilePath(video.getOriginalFilePath());
            Path streamDirectory = fullOriginalFilePath.getParent(); // यह stream-id फ़ोल्डर होगा

            if (streamDirectory != null && Files.exists(streamDirectory) && Files.isDirectory(streamDirectory)) {
                 // सुनिश्चित करें कि यह आपके बेस वीडियो पाथ के अंदर ही है ताकि गलती से रूट डायरेक्टरी न हट जाए
                if (streamDirectory.startsWith(fileStorageService.getFilePath(""))) { // compare with base path
                    fileStorageService.deleteDirectory(fileStorageService.getFilePath("").relativize(streamDirectory).toString());
                    logger.info("रिकॉर्ड ID: {} के लिए लाइव स्ट्रीम रिकॉर्ड डायरेक्टरी हटाई गई: {}", streamDirectory, videoId);
                } else {
                    logger.warn("स्ट्रीम डायरेक्टरी {} फ़ाइल स्टोरेज बेस पाथ के बाहर है। हटाया नहीं जाएगा।", streamDirectory);
                }
            }
        } catch (IOException e) {
            logger.error("रिकॉर्ड ID {} के लिए स्टोरेज से लाइव स्ट्रीम फ़ाइलों को हटाने में विफल: {}", videoId, e.getMessage(), e);
            throw new RuntimeException("स्टोरेज से लाइव स्ट्रीम फ़ाइलों को हटाने में विफल।", e);
        } catch (Exception e) {
            logger.error("रिकॉर्ड ID {} के लिए फ़ाइल हटाने के दौरान एक अप्रत्याशित त्रुटि हुई: {}", videoId, e.getMessage(), e);
            throw new RuntimeException("फ़ाइल हटाने के दौरान एक अप्रत्याशित त्रुटि हुई।", e);
        }

        videoRepository.delete(video);
        logger.info("लाइव स्ट्रीम रिकॉर्ड इकाई ID: {} को डेटाबेस से सफलतापूर्वक हटा दिया गया है।", videoId);
    }

    @Override
    public VideoDetailsDTO getLiveStreamRecordById(Long videoId, User currentUser) {
        return videoRepository.findByVideoIdAndUploadUser(videoId, currentUser)
                .map(this::mapToVideoDetailsDTO)
                .orElseThrow(() -> {
                    logger.warn("लाइव स्ट्रीम रिकॉर्ड ID: {} उपयोगकर्ता: {} के लिए नहीं मिला", videoId, currentUser.getUsername());
                    return new FileNotFoundException("लाइव स्ट्रीम रिकॉर्ड ID: " + videoId + " नहीं मिला");
                });
    }

    @Override
    public List<VideoDetailsDTO> getMyLiveStreamRecords(User currentUser) {
        return videoRepository.findByUploadUser(currentUser).stream()
                .map(this::mapToVideoDetailsDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Video findLiveStreamEntityById(Long videoId) throws FileNotFoundException {
        logger.debug("लाइव स्ट्रीम इकाई को ID: {} द्वारा खोजने का प्रयास कर रहा है", videoId);
        return videoRepository.findById(videoId)
                .orElseThrow(() -> {
                    logger.warn("लाइव स्ट्रीम इकाई ID: {} नहीं मिली", videoId);
                    return new FileNotFoundException("लाइव स्ट्रीम इकाई ID: " + videoId + " नहीं मिली");
                });
    }

    @Override
    @Transactional
    public Video getActualLiveStreamEntity(Long videoId, User user) {
        logger.debug("वास्तविक लाइव स्ट्रीम इकाई को ID: {} द्वारा उपयोगकर्ता: {} के लिए प्राप्त कर रहा है", videoId, user.getUsername());
        Video video = videoRepository.findById(videoId).orElseThrow(() -> {
            logger.warn("लाइव स्ट्रीम इकाई ID: {} नहीं मिली", videoId);
            return new FileNotFoundException("लाइव स्ट्रीम इकाई ID: " + videoId + " नहीं मिली");
        });

        if (!video.getUploadUser().getUserId().equals(user.getUserId())
                && user.getRole() != Roles.ADMIN) {
            logger.warn("उपयोगकर्ता {} ने लाइव स्ट्रीम इकाई {} तक पहुंचने का प्रयास किया जिसका उनका स्वामित्व नहीं है और वे व्यवस्थापक नहीं हैं।",
                    user.getUsername(), videoId);
            throw new SecurityException("लाइव स्ट्रीम इकाई तक पहुंच अस्वीकृत: " + videoId);
        }
        return video;
    }

    @Override
    public Resource prepareWebmStream(Long recordId, User currentUser) throws FileNotFoundException, SecurityException, IOException {
        logger.info("रिकॉर्ड ID: {} के लिए .webm स्ट्रीम तैयार कर रहा है, उपयोगकर्ता: {}", recordId, currentUser.getUsername());

        // पहले रिकॉर्ड इकाई प्राप्त करें और सुनिश्चित करें कि उपयोगकर्ता मालिक है
        Video videoRecord = getActualLiveStreamEntity(recordId, currentUser);

        // FileStorageService से सीधे .webm फ़ाइल लोड करें
        String relativeWebmPath = videoRecord.getOriginalFilePath();

        // सुनिश्चित करें कि फ़ाइल मौजूद है और पढ़ने योग्य है
        if (!fileStorageService.doesFileExist(relativeWebmPath)) {
            logger.warn("रिकॉर्ड ID {} के लिए .webm फ़ाइल नहीं मिली: {}", recordId, relativeWebmPath);
            throw new FileNotFoundException(".webm फ़ाइल नहीं मिली या संसाधित नहीं हुई: " + recordId);
        }

        // फ़ाइल को Resource के रूप में लोड करें
        Resource resource = fileStorageService.loadFileAsResource(relativeWebmPath);
        logger.info(".webm फ़ाइल को Resource के रूप में लोड किया गया: {}", relativeWebmPath);
        return resource;
    }

    private VideoDetailsDTO mapToVideoDetailsDTO(Video video) {
        VideoDetailsDTO dto = new VideoDetailsDTO();
        dto.setVideoId(video.getVideoId());
        dto.setUploadedAt(video.getUploadedAt());
        dto.setOriginalFilePath(video.getOriginalFilePath());

        if (video.getUploadUser() != null) {
            dto.setUploadUserId(video.getUploadUser().getUserId());
            dto.setUploadUsername(video.getUploadUser().getUserName());
        }
        return dto;
    }


}