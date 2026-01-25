package com.karaoke.config;

import com.karaoke.model.enums.MediaType;
import com.karaoke.model.enums.StorageEnvironment;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String type;
    private StorageEnvironment environment = StorageEnvironment.DEV;
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String region;
        private String bucketName;
        private Buckets buckets = new Buckets();

        @Data
        public static class Buckets {
            private String images;
            private String audio;
            private String videos;
            private String documents;
        }
    }

    public String getBucketForMediaType(MediaType mediaType) {
        if (s3.buckets == null) return s3.bucketName;
        
        if (mediaType == null) return s3.buckets.audio; // Default for karaoke

        switch (mediaType) {
            case IMAGE: return s3.buckets.images;
            case AUDIO: return s3.buckets.audio;
            case VIDEO: return s3.buckets.videos;
            case DOCUMENT: return s3.buckets.documents;
            default: return s3.buckets.audio;
        }
    }
}
