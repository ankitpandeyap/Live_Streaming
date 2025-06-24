package com.robspecs.live.serviceImpl;

import com.robspecs.live.exceptions.FileNotFoundException;
import com.robspecs.live.exceptions.FileStorageException;
import com.robspecs.live.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

@Service
public class FileStorageServiceImpl implements FileStorageService {

	private static final Logger logger = LoggerFactory.getLogger(FileStorageServiceImpl.class);

	private final Path fileStorageLocation;

	public FileStorageServiceImpl(@Value("${files.video.base-path}") String videoBasePath) {
		this.fileStorageLocation = Paths.get(videoBasePath).toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.fileStorageLocation);
			logger.info("फ़ाइल स्टोरेज डायरेक्टरी बनाई गई: {}", this.fileStorageLocation);
		} catch (Exception ex) {
			throw new FileStorageException("फ़ाइल स्टोरेज डायरेक्टरी नहीं बना सका।", ex);
		}
	}

	@Override
	public String storeFile(InputStream inputStream, String fileName, Long userId, String subDirectory)
			throws IOException {
		// फ़ाइल पाथ को सैनिटाइज़ करें
		fileName = StringUtils.cleanPath(fileName);

		// उपयोगकर्ता-विशिष्ट और उप-डायरेक्टरी पाथ बनाएँ
		Path targetLocation = this.fileStorageLocation.resolve("users").resolve(String.valueOf(userId));

		if (subDirectory != null && !subDirectory.trim().isEmpty()) {
			targetLocation = targetLocation.resolve(subDirectory);
		}

		try {
			Files.createDirectories(targetLocation); // सुनिश्चित करें कि लक्ष्य डायरेक्टरी मौजूद है
		} catch (IOException ex) {
			throw new FileStorageException("उपयोगकर्ता डायरेक्टरी या उप-डायरेक्टरी नहीं बना सका।", ex);
		}

		Path filePath = targetLocation.resolve(fileName);

		try {
			// फ़ाइल को लक्ष्य स्थान पर कॉपी करें
			Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
			logger.info("फ़ाइल {} को {} पर सफलतापूर्वक संग्रहीत किया गया", fileName, filePath);
		} catch (IOException ex) {
			throw new FileStorageException("फ़ाइल को स्टोर करने में विफल " + fileName + ". कृपया पुनः प्रयास करें!",
					ex);
		}

		// सापेक्ष पाथ लौटाएँ (बेस पाथ के सापेक्ष)
		// यह पाथ `users/<userId>/<subDirectory>/<fileName>` जैसा दिखेगा
		String relativePath = this.fileStorageLocation.relativize(filePath).toString();
		// विंडोज पर बैकस्लैश को फॉरवर्डस्लैश में बदलें ताकि पाथ संगत हों
		return relativePath.replace("\\", "/");
	}

	@Override
	public Resource loadFileAsResource(String relativePath) throws IOException {
		try {
			Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
			Resource resource = new FileSystemResource(filePath.toFile());
			if (resource.exists() || resource.isReadable()) {
				logger.debug("फ़ाइल Resource लोड किया गया: {}", filePath);
				return resource;
			} else {
				logger.warn("फ़ाइल नहीं मिली या पढ़ने योग्य नहीं: {}", filePath);
				throw new FileNotFoundException("फ़ाइल नहीं मिली " + relativePath);
			}
		} catch (Exception ex) {
			logger.error("फ़ाइल Resource लोड करने में त्रुटि {}: {}", relativePath, ex.getMessage(), ex);
			throw new FileNotFoundException("फ़ाइल लोड करने में त्रुटि " + relativePath, ex);
		}
	}

	@Override
	public boolean deleteFile(String relativePath) throws IOException {
		Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
		if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
			Files.delete(filePath);
			logger.info("फ़ाइल हटाई गई: {}", filePath);
			return true;
		}
		logger.warn("हटाने के लिए फ़ाइल नहीं मिली या वह एक डायरेक्टरी है: {}", filePath);
		return false;
	}

	@Override
	public boolean deleteDirectory(String relativePath) throws IOException {
		Path directoryPath = this.fileStorageLocation.resolve(relativePath).normalize();
		if (Files.exists(directoryPath) && Files.isDirectory(directoryPath)) {
			// डायरेक्टरी और उसकी सभी सामग्री को रिकर्सिव रूप से हटाएँ
			Files.walk(directoryPath).sorted(Comparator.reverseOrder()) // पहले फ़ाइलों को, फिर उप-डायरेक्टरीज़ को हटाएँ
					.forEach(path -> {
						try {
							Files.delete(path);
							logger.debug("हटाया गया पाथ: {}", path);
						} catch (IOException e) {
							logger.error("पाथ {} हटाने में विफल: {}", path, e.getMessage(), e);
						}
					});
			logger.info("डायरेक्टरी हटाई गई: {}", directoryPath);
			return true;
		}
		logger.warn("हटाने के लिए डायरेक्टरी नहीं मिली या वह एक फ़ाइल है: {}", directoryPath);
		return false;
	}

	@Override
	public boolean doesFileExist(String relativePath) {
		Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
		return Files.exists(filePath) && Files.isRegularFile(filePath);
	}

	@Override
	public long getFileSize(String relativePath) throws IOException {
		Path filePath = this.fileStorageLocation.resolve(relativePath).normalize();
		if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
			return Files.size(filePath);
		}
		throw new FileNotFoundException("फ़ाइल नहीं मिली या पढ़ने योग्य नहीं: " + relativePath);
	}

	@Override
	public Path getFilePath(String relativePath) {
		return this.fileStorageLocation.resolve(relativePath).normalize();
	}
}