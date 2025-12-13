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
}
