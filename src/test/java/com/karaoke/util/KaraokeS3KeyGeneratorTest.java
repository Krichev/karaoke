package com.karaoke.util;

import com.karaoke.config.StorageEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class KaraokeS3KeyGeneratorTest {

    private KaraokeS3KeyGenerator keyGenerator;

    @BeforeEach
    void setUp() {
        keyGenerator = new KaraokeS3KeyGenerator();
    }

    @Test
    void testGenerateRecordingKey_CorrectFormat() {
        String key = keyGenerator.generateRecordingKey(
            StorageEnvironment.PROD,
            12345L,
            "song-uuid-123",
            "perf-uuid-456",
            "wav"
        );
        
        assertTrue(key.startsWith("prod/"));
        assertTrue(key.contains("/user/12345/"));
        assertTrue(key.contains("/song/song-uuid-123/"));
        assertTrue(key.contains("/perf/perf-uuid-456/"));
        assertTrue(key.endsWith(".wav"));
        
        // Verify hash prefix is 2 hex chars
        String[] parts = key.split("/");
        assertEquals(2, parts[1].length());
        assertTrue(parts[1].matches("[0-9a-f]{2}"));
    }

    @Test
    void testGenerateReferenceTrackKey_CorrectFormat() {
        String key = keyGenerator.generateReferenceTrackKey(
            StorageEnvironment.DEV,
            "abc-def-ghi",
            "mp3"
        );
        
        assertTrue(key.startsWith("dev/"));
        assertTrue(key.contains("/system/songs/abc-def-ghi/"));
        assertTrue(key.contains("/reference/"));
        assertTrue(key.endsWith(".mp3"));
    }

    @Test
    void testHashPrefixDistribution() {
        // Verify even distribution across 256 buckets
        Map<String, Integer> distribution = new HashMap<>();
        for (long i = 0; i < 10000; i++) {
            String prefix = keyGenerator.computeHashPrefix(i);
            distribution.merge(prefix, 1, Integer::sum);
        }
        
        // Each prefix should have roughly 10000/256 ≈ 39 occurrences
        // Allow 50% variance
        distribution.values().forEach(count -> {
            assertTrue(count > 20 && count < 60, 
                "Distribution should be relatively even");
        });
    }
}
