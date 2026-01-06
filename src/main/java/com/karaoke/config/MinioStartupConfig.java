package com.karaoke.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/**
 * MinIO startup configuration
 * Ensures bucket is created on application startup
 */
@Configuration
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
@RequiredArgsConstructor
@Slf4j
public class MinioStartupConfig {

    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket-name}")
    private String bucketName;

    @Bean
    public ApplicationRunner initializeMinio() {
        return args -> {
            try {
                log.info("Initializing MinIO bucket: {}", bucketName);
                createBucketIfNotExists();
                log.info("MinIO initialization completed successfully");
            } catch (Exception e) {
                log.warn("MinIO initialization failed, but application will continue: {}", e.getMessage());
            }
        };
    }

    private void createBucketIfNotExists() {
        try {
            // Check if bucket exists
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            try {
                s3Client.headBucket(headBucketRequest);
                log.info("Bucket '{}' already exists", bucketName);
            } catch (Exception e) {
                // Bucket doesn't exist, create it
                log.info("Bucket '{}' doesn't exist, creating...", bucketName);
                createBucket();
            }

        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket", e);
            throw new RuntimeException("Failed to initialize MinIO bucket", e);
        }
    }

    private void createBucket() {
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();

        s3Client.createBucket(createBucketRequest);
        log.info("Created bucket: {}", bucketName);
    }
}
