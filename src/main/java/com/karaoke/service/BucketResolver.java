package com.karaoke.service;

import com.karaoke.config.StorageProperties;
import com.karaoke.model.enums.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BucketResolver {

    private final StorageProperties storageProperties;

    public String getBucket(MediaType mediaType) {
        return storageProperties.getBucketForMediaType(mediaType);
    }
}
