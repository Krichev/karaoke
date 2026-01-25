package com.karaoke.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.storage")
@Data
public class KaraokeStorageProperties {
    
    private StorageEnvironment environment = StorageEnvironment.DEV;
    private S3Properties s3 = new S3Properties();
    private MinioProperties minio = new MinioProperties();
    
    @Data
    public static class S3Properties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";
        private BucketProperties buckets = new BucketProperties();
    }
    
    @Data
    public static class BucketProperties {
        private String recordings = "karaoke-recordings";
        private String referenceTracks = "karaoke-reference-tracks";
    }
    
    @Data
    public static class MinioProperties {
        private int presignedUrlDuration = 60;
    }
    
    public String getBucketForRecordings() {
        return s3.getBuckets().getRecordings();
    }
    
    public String getBucketForReferenceTracks() {
        return s3.getBuckets().getReferenceTracks();
    }
}
