package com.karaoke.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Interface for audio file storage operations
 * Implementations can be local filesystem or S3/MinIO
 */
public interface AudioStorageService {

    /**
     * Store a performance recording
     * @param file the audio file
     * @param userId the user who uploaded the file
     * @param songId the song ID
     * @return S3 key or file path
     */
    String storeRecording(MultipartFile file, Long userId, String songId);

    /**
     * Store a reference track
     * @param file the audio file
     * @param songUuid the song UUID
     * @return S3 key or file path
     */
    String storeReferenceTrack(MultipartFile file, String songUuid);

    /**
     * Download audio file as byte array
     * @param s3Key the S3 key or file path
     * @return audio file bytes
     */
    byte[] downloadAudio(String s3Key);

    /**
     * Get audio file as input stream
     * @param s3Key the S3 key or file path
     * @return input stream
     */
    InputStream getAudioStream(String s3Key);

    /**
     * Generate presigned URL for temporary access
     * @param s3Key the S3 key
     * @return presigned URL (or null for local storage)
     */
    String generatePresignedUrl(String s3Key);

    /**
     * Delete audio file
     * @param s3Key the S3 key or file path
     */
    void deleteAudio(String s3Key);

    /**
     * Check if audio file exists
     * @param s3Key the S3 key or file path
     * @return true if exists
     */
    boolean audioExists(String s3Key);
}
