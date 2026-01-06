package com.karaoke.service;

import com.karaoke.exception.AudioStorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * MinIO-based audio storage service using S3-compatible API
 * Replaces file-based storage with object storage
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class MinioAudioStorageService implements AudioStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${app.storage.s3.bucket-name}")
    private String bucketName;

    @Value("${app.storage.minio.presigned-url-duration:60}")
    private int presignedUrlDuration; // minutes

    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/wav", "audio/mp3", "audio/ogg", "audio/m4a",
            "audio/aac", "audio/mpeg", "audio/x-wav"
    );

    @Override
    public String storeRecording(MultipartFile file, Long userId, String songId) {
        try {
            validateAudioFile(file);

            // Generate S3 key: recordings/{userId}/{yyyy/MM/dd}/{uuid}.{ext}
            String s3Key = generateS3Key("recordings", userId.toString(), file.getOriginalFilename());

            // Upload to MinIO
            uploadToMinio(s3Key, file.getBytes(), file.getContentType());

            log.info("Stored recording: {} for user {} and song {}", s3Key, userId, songId);
            return s3Key;

        } catch (IOException e) {
            log.error("Failed to store recording for user {} and song {}", userId, songId, e);
            throw new AudioStorageException("Failed to store recording", e);
        }
    }

    @Override
    public String storeReferenceTrack(MultipartFile file, String songUuid) {
        try {
            validateAudioFile(file);

            // Generate S3 key: reference-tracks/{songUuid}/{uuid}.{ext}
            String s3Key = generateS3Key("reference-tracks", songUuid, file.getOriginalFilename());

            // Upload to MinIO
            uploadToMinio(s3Key, file.getBytes(), file.getContentType());

            log.info("Stored reference track: {} for song {}", s3Key, songUuid);
            return s3Key;

        } catch (IOException e) {
            log.error("Failed to store reference track for song {}", songUuid, e);
            throw new AudioStorageException("Failed to store reference track", e);
        }
    }

    @Override
    public byte[] downloadAudio(String s3Key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            InputStream inputStream = s3Client.getObject(getRequest);
            byte[] bytes = inputStream.readAllBytes();

            log.debug("Downloaded audio from MinIO: {} ({} bytes)", s3Key, bytes.length);
            return bytes;

        } catch (Exception e) {
            log.error("Failed to download audio from MinIO: {}", s3Key, e);
            throw new AudioStorageException("Failed to download audio from MinIO", e);
        }
    }

    @Override
    public InputStream getAudioStream(String s3Key) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            InputStream stream = s3Client.getObject(getRequest);
            log.debug("Retrieved audio stream from MinIO: {}", s3Key);
            return stream;

        } catch (Exception e) {
            log.error("Failed to get audio stream from MinIO: {}", s3Key, e);
            throw new AudioStorageException("Failed to get audio stream from MinIO", e);
        }
    }

    @Override
    public String generatePresignedUrl(String s3Key) {
        try {
            if (s3Key == null || s3Key.trim().isEmpty()) {
                return null;
            }

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(presignedUrlDuration))
                    .getObjectRequest(getRequest)
                    .build();

            String url = s3Presigner.presignGetObject(presignRequest).url().toString();
            log.debug("Generated presigned URL for: {}", s3Key);
            return url;

        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", s3Key, e);
            return null;
        }
    }

    @Override
    public void deleteAudio(String s3Key) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted audio from MinIO: {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to delete audio from MinIO: {}", s3Key, e);
            throw new AudioStorageException("Failed to delete audio from MinIO", e);
        }
    }

    @Override
    public boolean audioExists(String s3Key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            s3Client.headObject(headRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking if audio exists: {}", s3Key, e);
            return false;
        }
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void uploadToMinio(String s3Key, byte[] content, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(content));
            log.debug("Uploaded audio to MinIO: {} ({} bytes)", s3Key, content.length);

        } catch (Exception e) {
            log.error("Failed to upload to MinIO: {}", s3Key, e);
            // Retry once
            try {
                log.info("Retrying upload to MinIO: {}", s3Key);
                PutObjectRequest putRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();
                s3Client.putObject(putRequest, RequestBody.fromBytes(content));
                log.info("Retry successful for: {}", s3Key);
            } catch (Exception retryException) {
                log.error("Retry failed for: {}", s3Key, retryException);
                throw new AudioStorageException("Failed to upload to MinIO after retry", retryException);
            }
        }
    }

    private void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file cannot be null or empty");
        }

        if (file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("Audio file must have a valid filename");
        }

        String contentType = file.getContentType();
        if (contentType == null || !AUDIO_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Invalid audio file type: " + contentType +
                    ". Allowed types: " + AUDIO_TYPES);
        }

        // Check file size (50MB max as per requirements)
        long maxSize = 50 * 1024 * 1024; // 50MB in bytes
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("Audio file size exceeds maximum allowed size of 50MB");
        }
    }

    private String generateS3Key(String prefix, String subPath, String originalFilename) {
        String filename = generateUniqueFilename(originalFilename);
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("%s/%s/%s/%s", prefix, subPath, datePath, filename);
    }

    private String generateUniqueFilename(String originalFilename) {
        String extension = getFileExtension(originalFilename);
        return UUID.randomUUID().toString() + (extension.isEmpty() ? "" : "." + extension);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}
