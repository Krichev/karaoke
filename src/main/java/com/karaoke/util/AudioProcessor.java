package com.karaoke.util;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import be.tarsos.dsp.onsets.OnsetHandler;
import be.tarsos.dsp.onsets.PercussionOnsetDetector;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;
import com.karaoke.model.NoteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
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
     * Extract note events with onset times, pitch, and duration
     */
    /**
     * Extract MFCC vectors from audio file.
     * Returns a list where each element is a double[] containing the MFCC coefficients for one frame.
     */
    public List<double[]> extractMFCCs(String audioFilePath) {
        List<double[]> mfccVectors = new ArrayList<>();

        try {
            File audioFile = new File(audioFilePath);

            // Recommended settings for MFCC
            int bufferSize = 2048;
            int overlap = 1024; // 50% overlap is standard for MFCC

            AudioDispatcher dispatcher = AudioDispatcherFactory.fromFile(audioFile, bufferSize, overlap);

            // 13 coefficients is standard, 40 mel filters, typical frequency range 300 Hz to ~half sample rate
            MFCC mfccProcessor = new MFCC(bufferSize, SAMPLE_RATE, 13, 40, 300, SAMPLE_RATE / 2.0f);

            dispatcher.addAudioProcessor(mfccProcessor);

            dispatcher.addAudioProcessor(new be.tarsos.dsp.AudioProcessor() {
                @Override
                public boolean process(AudioEvent audioEvent) {
                    float[] mfcc = mfccProcessor.getMFCC();
                    if (mfcc != null) {
                        double[] mfccDouble = new double[mfcc.length];
                        for (int i = 0; i < mfcc.length; i++) {
                            mfccDouble[i] = mfcc[i];
                        }
                        mfccVectors.add(mfccDouble);
                    }
                    return true;
                }

                @Override
                public void processingFinished() {
                    // No extra action needed
                }
            });

            dispatcher.run();

            log.info("Extracted {} MFCC vectors ({} coefficients each) from {}",
                    mfccVectors.size(),
                    mfccVectors.isEmpty() ? 0 : mfccVectors.get(0).length,
                    audioFilePath);

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
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(audioFilePath));
            javax.sound.sampled.AudioFormat format = audioInputStream.getFormat();
            long frames = audioInputStream.getFrameLength();
            double durationInSeconds = (frames + 0.0) / format.getFrameRate();
            audioInputStream.close();
            return durationInSeconds;
        } catch (Exception e) {
            log.error("Error calculating audio duration for {}", audioFilePath, e);
            return 0.0;
        }
    }


    /**
     * Extract note events with onset detection, pitch tracking, and duration estimation
     * This provides richer data than raw pitch values for accurate scoring
     */
    public List<NoteEvent> extractNoteEvents(String audioFilePath) {
        List<NoteEvent> noteEvents = new ArrayList<>();

        try {
            File audioFile = new File(audioFilePath);

            // First pass: detect onsets (when notes start)
            List<Double> onsetTimes = new ArrayList<>();
            AudioDispatcher onsetDispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);

            OnsetHandler onsetHandler = (time, salience) -> {
                onsetTimes.add(time * 1000.0); // Convert seconds to milliseconds
            };

            // Use percussion onset detector with moderate sensitivity
            PercussionOnsetDetector onsetDetector = new PercussionOnsetDetector(
                    SAMPLE_RATE,
                    BUFFER_SIZE,
                    onsetHandler,
                    20,  // Sensitivity threshold
                    10   // Time threshold
            );

            onsetDispatcher.addAudioProcessor(onsetDetector);
            onsetDispatcher.run();

            log.info("Detected {} onsets in {}", onsetTimes.size(), audioFilePath);

            // Second pass: extract pitch values with timestamps
            List<PitchTimestamp> pitchData = new ArrayList<>();
            AudioDispatcher pitchDispatcher = AudioDispatcherFactory.fromFile(audioFile, BUFFER_SIZE, OVERLAP);

            PitchDetectionHandler pitchHandler = (result, audioEvent) -> {
                if (result.getPitch() != -1) {
                    double timeMs = audioEvent.getTimeStamp() * 1000.0;
                    float pitch = result.getPitch();
                    float probability = result.getProbability();
                    pitchData.add(new PitchTimestamp(timeMs, pitch, probability));
                }
            };

            PitchProcessor pitchProcessor = new PitchProcessor(
                    PitchProcessor.PitchEstimationAlgorithm.YIN,
                    SAMPLE_RATE,
                    BUFFER_SIZE,
                    pitchHandler
            );

            pitchDispatcher.addAudioProcessor(pitchProcessor);
            pitchDispatcher.run();

            log.info("Extracted {} pitch samples from {}", pitchData.size(), audioFilePath);

            // Combine onsets and pitch data to create note events
            if (onsetTimes.isEmpty() || pitchData.isEmpty()) {
                log.warn("Insufficient data for note extraction: {} onsets, {} pitch samples",
                        onsetTimes.size(), pitchData.size());
                return noteEvents;
            }

            // Create note events by matching onsets with pitch data
            for (int i = 0; i < onsetTimes.size(); i++) {
                double onsetTime = onsetTimes.get(i);

                // Find pitch values near this onset
                List<PitchTimestamp> nearbyPitches = pitchData.stream()
                        .filter(p -> Math.abs(p.timeMs - onsetTime) < 100) // Within 100ms
                        .collect(java.util.stream.Collectors.toList());

                if (!nearbyPitches.isEmpty()) {
                    // Average pitch values for this note
                    double avgPitch = nearbyPitches.stream()
                            .mapToDouble(p -> p.pitch)
                            .average()
                            .orElse(0.0);

                    double avgAmplitude = nearbyPitches.stream()
                            .mapToDouble(p -> p.probability)
                            .average()
                            .orElse(0.0);

                    // Estimate duration until next onset or end
                    double duration;
                    if (i < onsetTimes.size() - 1) {
                        duration = onsetTimes.get(i + 1) - onsetTime;
                    } else {
                        // Last note - estimate from remaining pitch data
                        duration = pitchData.get(pitchData.size() - 1).timeMs - onsetTime;
                    }

                    // Filter out very short notes (< 50ms) as they're likely noise
                    if (duration >= 50 && avgPitch > 0) {
                        noteEvents.add(new NoteEvent(onsetTime, avgPitch, duration, avgAmplitude));
                    }
                }
            }

            // If onset detection failed, fall back to pitch-only segmentation
            if (noteEvents.isEmpty() && !pitchData.isEmpty()) {
                log.info("Onset detection yielded no notes, falling back to pitch segmentation");
                noteEvents = segmentPitchDataIntoNotes(pitchData);
            }

            log.info("Created {} note events from {}", noteEvents.size(), audioFilePath);

        } catch (Exception e) {
            log.error("Error extracting note events from {}", audioFilePath, e);
            throw new RuntimeException("Failed to extract note events", e);
        }

        return noteEvents;
    }

    /**
     * Fallback method: segment continuous pitch data into discrete notes
     * Used when onset detection doesn't work well
     */
    private List<NoteEvent> segmentPitchDataIntoNotes(List<PitchTimestamp> pitchData) {
        List<NoteEvent> notes = new ArrayList<>();

        if (pitchData.isEmpty()) {
            return notes;
        }

        double currentOnset = pitchData.get(0).timeMs;
        double currentPitch = pitchData.get(0).pitch;
        double currentAmplitude = pitchData.get(0).probability;
        int sampleCount = 1;

        double pitchChangeTolerance = 50.0; // Hz - significant pitch change threshold

        for (int i = 1; i < pitchData.size(); i++) {
            PitchTimestamp current = pitchData.get(i);

            // Check if pitch changed significantly (new note)
            if (Math.abs(current.pitch - currentPitch) > pitchChangeTolerance) {
                // Create note from accumulated samples
                double duration = current.timeMs - currentOnset;
                if (duration >= 50) { // Minimum 50ms note duration
                    notes.add(new NoteEvent(
                            currentOnset,
                            currentPitch,
                            duration,
                            currentAmplitude / sampleCount
                    ));
                }

                // Start new note
                currentOnset = current.timeMs;
                currentPitch = current.pitch;
                currentAmplitude = current.probability;
                sampleCount = 1;
            } else {
                // Accumulate for current note
                currentPitch = (currentPitch * sampleCount + current.pitch) / (sampleCount + 1);
                currentAmplitude += current.probability;
                sampleCount++;
            }
        }

        // Add final note
        PitchTimestamp last = pitchData.get(pitchData.size() - 1);
        double duration = last.timeMs - currentOnset;
        if (duration >= 50) {
            notes.add(new NoteEvent(
                    currentOnset,
                    currentPitch,
                    duration,
                    currentAmplitude / sampleCount
            ));
        }

        return notes;
    }

    /**
     * Helper class to store pitch with timestamp
     */
    private static class PitchTimestamp {
        double timeMs;
        float pitch;
        float probability;

        PitchTimestamp(double timeMs, float pitch, float probability) {
            this.timeMs = timeMs;
            this.pitch = pitch;
            this.probability = probability;
        }
    }
}
