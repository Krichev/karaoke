package com.karaoke.util;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;
import be.tarsos.dsp.util.fft.FFT;
import com.karaoke.dto.SoundComparisonDetail;
import com.karaoke.dto.SoundFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SoundAnalyzer {
    
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 2048;
    private static final int MFCC_COEFFICIENTS = 13;
    private static final double SEGMENT_DURATION_MS = 150; // Extract 150ms around each onset
    
    /**
     * Extract sound fingerprints for each onset in the audio
     */
    public List<SoundFingerprint> extractFingerprints(String audioPath, List<Double> onsetTimesMs) {
        List<SoundFingerprint> fingerprints = new ArrayList<>();
        
        try {
            File audioFile = new File(audioPath);
            AudioInputStream fullStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = fullStream.getFormat();
            byte[] fullAudioBytes = fullStream.readAllBytes();
            fullStream.close();
            
            float sampleRate = format.getSampleRate();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int channels = format.getChannels();
            int bytesPerFrame = bytesPerSample * channels;
            
            for (Double onsetMs : onsetTimesMs) {
                try {
                    // Extract segment around onset
                    int startSample = (int) ((onsetMs / 1000.0) * sampleRate);
                    int segmentSamples = (int) ((SEGMENT_DURATION_MS / 1000.0) * sampleRate);
                    
                    // Ensure we don't go out of bounds
                    int startByte = Math.max(0, startSample * bytesPerFrame);
                    int endByte = Math.min(fullAudioBytes.length, (startSample + segmentSamples) * bytesPerFrame);
                    
                    if (endByte <= startByte) {
                        fingerprints.add(createEmptyFingerprint());
                        continue;
                    }
                    
                    byte[] segmentBytes = Arrays.copyOfRange(fullAudioBytes, startByte, endByte);
                    
                    // Convert to samples
                    double[] samples = bytesToSamples(segmentBytes, bytesPerSample, channels);
                    
                    // Extract features
                    SoundFingerprint fingerprint = extractFingerprintFromSamples(samples, sampleRate);
                    fingerprints.add(fingerprint);
                    
                } catch (Exception e) {
                    log.warn("Error extracting fingerprint for onset at {}ms: {}", onsetMs, e.getMessage());
                    fingerprints.add(createEmptyFingerprint());
                }
            }
            
            log.info("🔊 Extracted {} sound fingerprints", fingerprints.size());
            
        } catch (Exception e) {
            log.error("❌ Error extracting fingerprints: {}", e.getMessage(), e);
        }
        
        return fingerprints;
    }
    
    /**
     * Extract fingerprint from audio samples
     */
    private SoundFingerprint extractFingerprintFromSamples(double[] samples, float sampleRate) {
        // Calculate MFCC
        double[] mfcc = calculateMFCC(samples, sampleRate);
        
        // Calculate spectral centroid
        double spectralCentroid = calculateSpectralCentroid(samples, sampleRate);
        
        // Calculate spectral rolloff
        double spectralRolloff = calculateSpectralRolloff(samples, sampleRate);
        
        // Calculate zero crossing rate
        double zcr = calculateZeroCrossingRate(samples);
        
        // Calculate RMS energy
        double rmsEnergy = calculateRMSEnergy(samples);
        
        // Calculate spectral flatness
        double spectralFlatness = calculateSpectralFlatness(samples);
        
        // Estimate transient duration
        double transientDuration = estimateTransientDuration(samples, sampleRate);
        
        return SoundFingerprint.builder()
                .mfcc(mfcc)
                .spectralCentroid(spectralCentroid)
                .spectralRolloff(spectralRolloff)
                .zeroCrossingRate(zcr)
                .rmsEnergy(rmsEnergy)
                .spectralFlatness(spectralFlatness)
                .transientDurationMs(transientDuration)
                .build();
    }
    
    /**
     * Calculate MFCC coefficients
     */
    private double[] calculateMFCC(double[] samples, float sampleRate) {
        double[] mfccResult = new double[MFCC_COEFFICIENTS];
        
        try {
            // Use simple DCT-based MFCC approximation
            int fftSize = Math.min(samples.length, BUFFER_SIZE);
            double[] magnitude = new double[fftSize / 2];
            
            // Simple FFT magnitude calculation
            for (int k = 0; k < fftSize / 2; k++) {
                double real = 0, imag = 0;
                for (int n = 0; n < fftSize && n < samples.length; n++) {
                    double angle = 2 * Math.PI * k * n / fftSize;
                    real += samples[n] * Math.cos(angle);
                    imag -= samples[n] * Math.sin(angle);
                }
                magnitude[k] = Math.sqrt(real * real + imag * imag);
            }
            
            // Apply mel filterbank and DCT (simplified)
            int numFilters = 26;
            double[] melEnergies = new double[numFilters];
            
            for (int i = 0; i < numFilters; i++) {
                double melLow = 2595 * Math.log10(1 + (i * sampleRate / 2 / numFilters) / 700);
                double melHigh = 2595 * Math.log10(1 + ((i + 1) * sampleRate / 2 / numFilters) / 700);
                
                int binLow = (int) (melLow * fftSize / sampleRate);
                int binHigh = (int) (melHigh * fftSize / sampleRate);
                
                for (int j = Math.max(0, binLow); j < Math.min(magnitude.length, binHigh); j++) {
                    melEnergies[i] += magnitude[j] * magnitude[j];
                }
                melEnergies[i] = Math.log(melEnergies[i] + 1e-10);
            }
            
            // DCT to get MFCCs
            for (int i = 0; i < MFCC_COEFFICIENTS; i++) {
                for (int j = 0; j < numFilters; j++) {
                    mfccResult[i] += melEnergies[j] * Math.cos(Math.PI * i * (j + 0.5) / numFilters);
                }
            }
            
        } catch (Exception e) {
            log.warn("MFCC calculation error: {}", e.getMessage());
        }
        
        return mfccResult;
    }
    
    /**
     * Calculate spectral centroid (center of mass of spectrum)
     */
    private double calculateSpectralCentroid(double[] samples, float sampleRate) {
        int fftSize = Math.min(samples.length, BUFFER_SIZE);
        double[] magnitude = new double[fftSize / 2];
        
        // FFT magnitude
        for (int k = 0; k < fftSize / 2; k++) {
            double real = 0, imag = 0;
            for (int n = 0; n < fftSize && n < samples.length; n++) {
                double angle = 2 * Math.PI * k * n / fftSize;
                real += samples[n] * Math.cos(angle);
                imag -= samples[n] * Math.sin(angle);
            }
            magnitude[k] = Math.sqrt(real * real + imag * imag);
        }
        
        // Centroid calculation
        double weightedSum = 0;
        double totalMagnitude = 0;
        
        for (int k = 0; k < magnitude.length; k++) {
            double freq = k * sampleRate / fftSize;
            weightedSum += freq * magnitude[k];
            totalMagnitude += magnitude[k];
        }
        
        return totalMagnitude > 0 ? weightedSum / totalMagnitude : 0;
    }
    
    /**
     * Calculate spectral rolloff (frequency below which 85% of energy is contained)
     */
    private double calculateSpectralRolloff(double[] samples, float sampleRate) {
        int fftSize = Math.min(samples.length, BUFFER_SIZE);
        double[] magnitude = new double[fftSize / 2];
        
        for (int k = 0; k < fftSize / 2; k++) {
            double real = 0, imag = 0;
            for (int n = 0; n < fftSize && n < samples.length; n++) {
                double angle = 2 * Math.PI * k * n / fftSize;
                real += samples[n] * Math.cos(angle);
                imag -= samples[n] * Math.sin(angle);
            }
            magnitude[k] = real * real + imag * imag; // Power spectrum
        }
        
        double totalEnergy = Arrays.stream(magnitude).sum();
        double threshold = 0.85 * totalEnergy;
        double cumulative = 0;
        
        for (int k = 0; k < magnitude.length; k++) {
            cumulative += magnitude[k];
            if (cumulative >= threshold) {
                return k * sampleRate / fftSize;
            }
        }
        
        return sampleRate / 2;
    }
    
    /**
     * Calculate zero crossing rate
     */
    private double calculateZeroCrossingRate(double[] samples) {
        if (samples.length < 2) return 0;
        
        int crossings = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) ||
                (samples[i] < 0 && samples[i - 1] >= 0)) {
                crossings++;
            }
        }
        
        return (double) crossings / samples.length;
    }
    
    /**
     * Calculate RMS energy (normalized)
     */
    private double calculateRMSEnergy(double[] samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
    
    /**
     * Calculate spectral flatness (Wiener entropy)
     */
    private double calculateSpectralFlatness(double[] samples) {
        int fftSize = Math.min(samples.length, BUFFER_SIZE);
        double[] magnitude = new double[fftSize / 2];
        
        for (int k = 0; k < fftSize / 2; k++) {
            double real = 0, imag = 0;
            for (int n = 0; n < fftSize && n < samples.length; n++) {
                double angle = 2 * Math.PI * k * n / fftSize;
                real += samples[n] * Math.cos(angle);
                imag -= samples[n] * Math.sin(angle);
            }
            magnitude[k] = Math.sqrt(real * real + imag * imag) + 1e-10;
        }
        
        // Geometric mean / Arithmetic mean
        double logSum = 0;
        double sum = 0;
        for (double m : magnitude) {
            logSum += Math.log(m);
            sum += m;
        }
        
        double geometricMean = Math.exp(logSum / magnitude.length);
        double arithmeticMean = sum / magnitude.length;
        
        return geometricMean / arithmeticMean;
    }
    
    /**
     * Estimate transient duration (time for amplitude to decay to 10%)
     */
    private double estimateTransientDuration(double[] samples, float sampleRate) {
        double maxAmp = 0;
        int maxIndex = 0;
        
        for (int i = 0; i < samples.length; i++) {
            if (Math.abs(samples[i]) > maxAmp) {
                maxAmp = Math.abs(samples[i]);
                maxIndex = i;
            }
        }
        
        double threshold = maxAmp * 0.1;
        int decayIndex = maxIndex;
        
        for (int i = maxIndex; i < samples.length; i++) {
            if (Math.abs(samples[i]) < threshold) {
                decayIndex = i;
                break;
            }
        }
        
        return (decayIndex - maxIndex) * 1000.0 / sampleRate;
    }
    
    /**
     * Compare two sound fingerprints
     */
    public SoundComparisonDetail compareFingerprints(
            SoundFingerprint reference, 
            SoundFingerprint user, 
            int beatIndex) {
        
        if (reference == null || user == null) {
            return SoundComparisonDetail.createMissed(beatIndex);
        }
        
        // MFCC cosine similarity
        double mfccSimilarity = calculateCosineSimilarity(reference.getMfcc(), user.getMfcc()) * 100;
        
        // Brightness match (spectral centroid comparison)
        double centroidRatio = Math.min(reference.getSpectralCentroid(), user.getSpectralCentroid()) /
                              Math.max(reference.getSpectralCentroid(), user.getSpectralCentroid() + 1);
        double brightnessMatch = centroidRatio * 100;
        
        // Energy match
        double energyRatio = Math.min(reference.getRmsEnergy(), user.getRmsEnergy()) /
                            Math.max(reference.getRmsEnergy(), user.getRmsEnergy() + 0.001);
        double energyMatch = energyRatio * 100;
        
        // Overall sound score: 60% MFCC, 25% brightness, 15% energy
        double overallSoundScore = (mfccSimilarity * 0.6) + (brightnessMatch * 0.25) + (energyMatch * 0.15);
        
        // Generate feedback
        String feedback = generateSoundFeedback(reference, user, overallSoundScore);
        
        return SoundComparisonDetail.builder()
                .beatIndex(beatIndex)
                .mfccSimilarity(Math.round(mfccSimilarity * 10) / 10.0)
                .spectralCentroidRef(Math.round(reference.getSpectralCentroid()))
                .spectralCentroidUser(Math.round(user.getSpectralCentroid()))
                .brightnessMatch(Math.round(brightnessMatch * 10) / 10.0)
                .energyMatch(Math.round(energyMatch * 10) / 10.0)
                .overallSoundScore(Math.round(overallSoundScore * 10) / 10.0)
                .userQuality(user.getQualityAssessment())
                .referenceQuality(reference.getQualityAssessment())
                .feedback(feedback)
                .build();
    }
    
    private double calculateCosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        if (denominator == 0) return 0;
        
        // Convert from [-1, 1] to [0, 1]
        return (dotProduct / denominator + 1) / 2;
    }
    
    private String generateSoundFeedback(SoundFingerprint ref, SoundFingerprint user, double score) {
        if (score >= 85) {
            return "Excellent sound match! 👏";
        }
        
        String refQuality = ref.getQualityAssessment();
        String userQuality = user.getQualityAssessment();
        
        if (userQuality.equals("MUFFLED") && !refQuality.equals("MUFFLED")) {
            return "Try a crisper, clearer clap";
        }
        if (userQuality.equals("SHARP") && refQuality.equals("CLEAR")) {
            return "Good! Slightly softer might match better";
        }
        if (user.getRmsEnergy() < ref.getRmsEnergy() * 0.5) {
            return "Try clapping a bit louder";
        }
        if (user.getRmsEnergy() > ref.getRmsEnergy() * 1.5) {
            return "Try clapping a bit softer";
        }
        if (score >= 70) {
            return "Good sound quality";
        }
        
        return "Try to match the reference sound more closely";
    }
    
    private double[] bytesToSamples(byte[] bytes, int bytesPerSample, int channels) {
        int numSamples = bytes.length / (bytesPerSample * channels);
        double[] samples = new double[numSamples];
        
        for (int i = 0; i < numSamples; i++) {
            int offset = i * bytesPerSample * channels;
            if (bytesPerSample == 2) {
                short value = (short) ((bytes[offset + 1] << 8) | (bytes[offset] & 0xFF));
                samples[i] = value / 32768.0;
            } else {
                samples[i] = (bytes[offset] - 128) / 128.0;
            }
        }
        
        return samples;
    }
    
    private SoundFingerprint createEmptyFingerprint() {
        return SoundFingerprint.builder()
                .mfcc(new double[MFCC_COEFFICIENTS])
                .spectralCentroid(0)
                .spectralRolloff(0)
                .zeroCrossingRate(0)
                .rmsEnergy(0)
                .spectralFlatness(0)
                .transientDurationMs(0)
                .build();
    }
}
