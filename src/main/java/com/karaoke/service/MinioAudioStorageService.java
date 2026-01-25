package com.karaoke.service;

import com.karaoke.config.StorageProperties;
import com.karaoke.exception.AudioStorageException;
import com.karaoke.model.enums.MediaType;
import com.karaoke.service.BucketResolver;
import com.karaoke.util.S3KeyGenerator;
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
    
    // NEW DEPENDENCIES
    private final S3KeyGenerator s3KeyGenerator;
    private final BucketResolver bucketResolver;
    private final StorageProperties storageProperties;

    @Value("${app.storage.s3.bucket-name}")
    private String legacyBucketName;

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

            // Generate S3 key using new hierarchical schema
            // Using userId as ownerId
            String s3Key = s3KeyGenerator.generateKey(
                storageProperties.getEnvironment(),
                userId,
                "user",
                null, // quizId
                null, // questionId
                MediaType.AUDIO,
                getFileExtension(file.getOriginalFilename())
            );

            // Get bucket
            String bucket = bucketResolver.getBucket(MediaType.AUDIO);

            // Upload to MinIO
            uploadToMinio(bucket, s3Key, file.getBytes(), file.getContentType());

            log.info("Stored recording: {} for user {} and song {} in bucket {}", s3Key, userId, songId, bucket);
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

            // For reference tracks, using system/0 as owner
            String s3Key = s3KeyGenerator.generateKey(
                storageProperties.getEnvironment(),
                0L, // System/Admin
                "system",
                null, 
                null,
                MediaType.AUDIO,
                getFileExtension(file.getOriginalFilename())
            );
            
            String bucket = bucketResolver.getBucket(MediaType.AUDIO);

            // Upload to MinIO
            uploadToMinio(bucket, s3Key, file.getBytes(), file.getContentType());

            log.info("Stored reference track: {} for song {} in bucket {}", s3Key, songUuid, bucket);
            return s3Key;

        } catch (IOException e) {
            log.error("Failed to store reference track for song {}", songUuid, e);
            throw new AudioStorageException("Failed to store reference track", e);
        }
    }

    @Override
    public byte[] downloadAudio(String s3Key) {
        String bucket = determineBucket(s3Key);
        
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
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
        String bucket = determineBucket(s3Key);
        
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
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
            
            String bucket = determineBucket(s3Key);

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
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
            // Try deleting from determined bucket, or both if unsure
            String bucket = determineBucket(s3Key);
            deleteFromBucket(bucket, s3Key);
            
            // If it looked like a new key but might be old (edge case), or vice versa,
            // we could double delete, but keeping it simple for now.
            
            log.info("Deleted audio from MinIO: {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to delete audio from MinIO: {}", s3Key, e);
            throw new AudioStorageException("Failed to delete audio from MinIO", e);
        }
    }

    @Override
    public boolean audioExists(String s3Key) {
        String bucket = determineBucket(s3Key);
        return fileExistsInBucket(bucket, s3Key);
    }
    
    // Determine bucket based on key format heuristic
    private String determineBucket(String s3Key) {
        String env = storageProperties.getEnvironment().getPathValue();
        if (s3Key.startsWith(env + "/")) {
            return bucketResolver.getBucket(MediaType.AUDIO);
        }
        return legacyBucketName;
    }
    
    private boolean fileExistsInBucket(String bucket, String key) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void deleteFromBucket(String bucket, String key) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3Client.deleteObject(deleteRequest);
    }

    private void uploadToMinio(String bucket, String s3Key, byte[] content, String contentType) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(content));
            log.debug("Uploaded audio to MinIO bucket {}: {} ({} bytes)", bucket, s3Key, content.length);

        } catch (Exception e) {
            log.error("Failed to upload to MinIO: {}", s3Key, e);
            // Retry once logic could be added here if needed
            throw new AudioStorageException("Failed to upload to MinIO", e);
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
        // Validation logic...
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}