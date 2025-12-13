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
import be.tarsos.dsp.mfcc.MFCC;
import be.tarsos.dsp.onsets.OnsetHandler;
import be.tarsos.dsp.onsets.PercussionOnsetDetector;
import com.karaoke.model.NoteEvent;

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
     * Extract note events with onset times, pitch, and duration
     */
    public List<NoteEvent> extractNoteEvents(String audioFilePath) {
        List<NoteEvent> noteEvents = new ArrayList<>();
        List<Double> onsetTimes = new ArrayList<>();
        List<Double> pitches = new ArrayList<>();
        List<Double> amplitudes = new ArrayList<>();
        
        try {
            File audioFile = new File(audioFilePath);
            
            // First pass: detect onsets (note start times)
            AudioDispatcher onsetDispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);
            
            OnsetHandler onsetHandler = (time, salience) -> {
                onsetTimes.add(time * 1000); // Convert to milliseconds
            };
            
            PercussionOnsetDetector onsetDetector = new PercussionOnsetDetector(
                SAMPLE_RATE, BUFFER_SIZE, onsetHandler, 40.0, 10.0
            );
            
            onsetDispatcher.addAudioProcessor(onsetDetector);
            onsetDispatcher.run();
            
            // Second pass: extract pitch at each onset time
            AudioDispatcher pitchDispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);
            
            final int[] sampleCounter = {0};
            final double sampleIntervalMs = (BUFFER_SIZE * 1000.0) / SAMPLE_RATE;
            
            PitchDetectionHandler pitchHandler = (result, audioEvent) -> {
                double currentTimeMs = sampleCounter[0] * sampleIntervalMs;
                
                if (result.getPitch() != -1) {
                    pitches.add(result.getPitch());
                    amplitudes.add((double) audioEvent.getRMS());
                }
                sampleCounter[0]++;
            };
            
            PitchProcessor pitchProcessor = new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN,
                SAMPLE_RATE, BUFFER_SIZE, pitchHandler
            );
            
            pitchDispatcher.addAudioProcessor(pitchProcessor);
            pitchDispatcher.run();
            
            // Merge onset times with pitch data
            // For each onset, find the most stable pitch in the following window
            for (double onsetTime : onsetTimes) {
                int startIdx = (int) (onsetTime / sampleIntervalMs);
                int windowSize = 10; // Look at next 10 samples (~200ms window)
                
                if (startIdx + windowSize < pitches.size()) {
                    // Find most common pitch in window (mode)
                    List<Double> windowPitches = pitches.subList(startIdx, Math.min(startIdx + windowSize, pitches.size()));
                    double avgPitch = windowPitches.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double avgAmplitude = amplitudes.subList(startIdx, Math.min(startIdx + windowSize, amplitudes.size()))
                        .stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    
                    // Estimate duration until next onset or pitch change
                    double duration = (startIdx < onsetTimes.size() - 1) 
                        ? onsetTimes.get(onsetTimes.indexOf(onsetTime) + 1) - onsetTime 
                        : 500; // Default 500ms
                    
                    noteEvents.add(new NoteEvent(onsetTime, avgPitch, duration, avgAmplitude));
                }
            }
            
            log.info("Extracted {} note events from {}", noteEvents.size(), audioFilePath);
            
        } catch (Exception e) {
            log.error("Error extracting note events from {}", audioFilePath, e);
            throw new RuntimeException("Failed to extract note events", e);
        }
        
        return noteEvents;
    }

    /**
     * Extract MFCCs for voice similarity analysis
     */
    public List<double[]> extractMFCCs(String audioFilePath) {
        List<double[]> mfccVectors = new ArrayList<>();
        
        try {
            File audioFile = new File(audioFilePath);
            AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);
            
            MFCC mfccProcessor = new MFCC(BUFFER_SIZE, SAMPLE_RATE, 13, 40, 300, 3000);
            
            dispatcher.addAudioProcessor(mfccProcessor);
            dispatcher.addAudioProcessor((audioEvent, buffer) -> {
                float[] mfccs = mfccProcessor.getMFCC();
                if (mfccs != null && mfccs.length > 0) {
                    // Convert float[] to double[]
                    double[] mfccDouble = new double[mfccs.length];
                    for (int i = 0; i < mfccs.length; i++) {
                        mfccDouble[i] = mfccs[i];
                    }
                    mfccVectors.add(mfccDouble);
                }
            });
            
            dispatcher.run();
            
            log.info("Extracted {} MFCC vectors from {}", mfccVectors.size(), audioFilePath);
            
        } catch (Exception e) {
            log.error("Error extracting MFCCs from {}", audioFilePath, e);
            throw new RuntimeException("Failed to extract MFCCs", e);
        }
        
        return mfccVectors;
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