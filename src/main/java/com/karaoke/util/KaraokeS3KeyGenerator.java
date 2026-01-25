package com.karaoke.util;

import com.karaoke.config.StorageEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@Slf4j
public class KaraokeS3KeyGenerator {
    
    /**
     * Generate S3 key for user performance recordings.
     * Format: {env}/{hashPrefix}/user/{userId}/song/{songId}/perf/{performanceId}/{uuid}.{ext}
     * Example: prod/3a/user/48291/song/abc-123/perf/def-456/8f3c9d2e.wav
     */
    public String generateRecordingKey(StorageEnvironment env, Long userId, 
                                       String songId, String performanceId, String extension) {
        validateInputs(userId, songId, performanceId);
        
        String hashPrefix = computeHashPrefix(userId);
        String uuid = UUID.randomUUID().toString();
        String ext = normalizeExtension(extension);
        
        return String.format("%s/%s/user/%d/song/%s/perf/%s/%s.%s",
            env.getPathValue(),
            hashPrefix,
            userId,
            songId,
            performanceId,
            uuid,
            ext
        );
    }
    
    /**
     * Generate S3 key for reference tracks.
     * Format: {env}/{hashPrefix}/system/songs/{songUuid}/reference/{uuid}.{ext}
     * Example: prod/7f/system/songs/abc-def-123/reference/9d2e8f3c.mp3
     */
    public String generateReferenceTrackKey(StorageEnvironment env, String songUuid, String extension) {
        validateSongUuid(songUuid);
        
        String hashPrefix = computeHashPrefixFromUuid(songUuid);
        String uuid = UUID.randomUUID().toString();
        String ext = normalizeExtension(extension);
        
        return String.format("%s/%s/system/songs/%s/reference/%s.%s",
            env.getPathValue(),
            hashPrefix,
            songUuid,
            uuid,
            ext
        );
    }
    
    public String computeHashPrefix(Long id) {
        return String.format("%02x", Math.abs(id.hashCode()) % 256);
    }
    
    public String computeHashPrefixFromUuid(String uuid) {
        return String.format("%02x", Math.abs(uuid.hashCode()) % 256);
    }
    
    private void validateInputs(Long userId, String songId, String performanceId) {
        if (userId == null || !StringUtils.hasText(songId) || !StringUtils.hasText(performanceId)) {
            throw new IllegalArgumentException("UserId, songId, and performanceId are required");
        }
    }
    
    private void validateSongUuid(String songUuid) {
        if (!StringUtils.hasText(songUuid)) {
            throw new IllegalArgumentException("Song UUID is required");
        }
    }
    
    private String normalizeExtension(String extension) {
        if (!StringUtils.hasText(extension)) {
            return "wav";
        }
        return extension.startsWith(".") ? extension.substring(1).toLowerCase() : extension.toLowerCase();
    }
}
