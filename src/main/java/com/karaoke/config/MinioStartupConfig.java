package com.karaoke.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class MinioStartupConfig {

    private final S3Client s3Client;
    private final KaraokeStorageProperties storageProperties;

    @Bean
    public ApplicationRunner initializeKaraokeBuckets() {
        return args -> {
            try {
                log.info("Initializing Karaoke MinIO buckets...");
                
                // Create recordings bucket
                createBucketIfNotExists(storageProperties.getBucketForRecordings());
                
                // Create reference tracks bucket
                createBucketIfNotExists(storageProperties.getBucketForReferenceTracks());
                
                log.info("Karaoke MinIO initialization completed successfully");
            } catch (Exception e) {
                log.error("Karaoke MinIO initialization failed", e);
                throw new RuntimeException("Failed to initialize MinIO buckets", e);
            }
        };
    }

    private void createBucketIfNotExists(String bucketName) {
        try {
            HeadBucketRequest headRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            
            try {
                s3Client.headBucket(headRequest);
                log.info("Bucket '{}' already exists", bucketName);
            } catch (NoSuchBucketException e) {
                log.info("Creating bucket: {}", bucketName);
                CreateBucketRequest createRequest = CreateBucketRequest.builder()
                        .bucket(bucketName)
                        .build();
                s3Client.createBucket(createRequest);
                log.info("Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize bucket: {}", bucketName, e);
            throw e;
        }
    }
}