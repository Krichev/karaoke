package com.karaoke.util;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AudioProcessor {
    
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 2048;
    private static final int OVERLAP = 0;
    
    /**
     * Extract pitch values from audio file
     */
    public List<Double> extractPitchValues(String audioFilePath) {
        List<Double> pitchValues = new ArrayList<>();
        
        try {
            File audioFile = new File(audioFilePath);
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);
            
            PitchDetectionHandler pitchHandler = (result, audioEvent) -> {
                if (result.getPitch() != -1) {
                    double pitch = result.getPitch();
                    pitchValues.add(pitch);
                }
            };
            
            PitchProcessor pitchProcessor = new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN,
                SAMPLE_RATE,
                BUFFER_SIZE,
                pitchHandler
            );
            
            dispatcher.addAudioProcessor(pitchProcessor);
            dispatcher.run();
            
            log.info("Extracted {} pitch values from {}", pitchValues.size(), audioFilePath);
            
        } catch (Exception e) {
            log.error("Error extracting pitch values from {}", audioFilePath, e);
            throw new RuntimeException("Failed to extract pitch values", e);
        }
        
        return pitchValues;
    }
    
    /**
     * Calculate audio duration in seconds
     */
    public double getAudioDuration(String audioFilePath) {
        try {
            File audioFile = new File(audioFilePath);
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);
            
            final double[] duration = {0.0};
            dispatcher.addAudioProcessor((audioEvent) -> {
                duration[0] = audioEvent.getTimeStamp();
                return true;
            });
            
            dispatcher.run();
            return duration[0];
            
        } catch (Exception e) {
            log.error("Error calculating audio duration for {}", audioFilePath, e);
            return 0.0;
        }
    }
}
