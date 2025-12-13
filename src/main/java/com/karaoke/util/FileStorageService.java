package com.karaoke.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {
    
    @Value("${karaoke.storage.recordings-path}")
    private String recordingsPath;
    
    @Value("${karaoke.storage.reference-tracks-path}")
    private String referenceTracksPath;
    
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(recordingsPath));
            Files.createDirectories(Paths.get(referenceTracksPath));
            log.info("Storage directories initialized: recordings={}, references={}", 
                     recordingsPath, referenceTracksPath);
        } catch (IOException e) {
            log.error("Failed to create storage directories", e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }
    
    /**
     * Store user recording
     */
    public String storeRecording(MultipartFile file, String userId, String songId) throws IOException {
        String filename = String.format("%s_%s_%s_%s",
            userId,
            songId,
            UUID.randomUUID().toString(),
            getFileExtension(file.getOriginalFilename())
        );
        
        Path targetPath = Paths.get(recordingsPath).resolve(filename);
        Files.copy(file.getInputStream(), targetPath);
        
        log.info("Stored recording: {}", targetPath);
        return targetPath.toString();
    }
    
    /**
     * Store reference track
     */
    public String storeReferenceTrack(MultipartFile file, String songId) throws IOException {
        String filename = String.format("ref_%s_%s",
            songId,
            getFileExtension(file.getOriginalFilename())
        );
        
        Path targetPath = Paths.get(referenceTracksPath).resolve(filename);
        Files.copy(file.getInputStream(), targetPath);
        
        log.info("Stored reference track: {}", targetPath);
        return targetPath.toString();
    }
    
    /**
     * Delete file
     */
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }
    
    /**
     * Check if file exists
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "audio";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
