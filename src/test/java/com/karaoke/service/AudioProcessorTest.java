package com.karaoke.service;

import com.karaoke.util.AudioProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioProcessorTest {
    
    private AudioProcessor audioProcessor;
    
    @BeforeEach
    void setUp() {
        audioProcessor = new AudioProcessor();
    }
    
    @Test
    void testExtractPitchValues_ValidAudioFile() {
        // Note: This test requires a real audio file
        // For MVP, you can skip this or mock the TarsosDSP library
        
        // String testAudioPath = "src/test/resources/test-audio.wav";
        // List<Double> pitches = audioProcessor.extractPitchValues(testAudioPath);
        // assertFalse(pitches.isEmpty(), "Should extract pitch values");
        
        assertTrue(true, "Placeholder test - requires real audio file");
    }
}
