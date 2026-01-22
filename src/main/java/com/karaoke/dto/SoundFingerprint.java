package com.karaoke.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spectral fingerprint of a single sound/beat
 * Used to compare timbral characteristics between reference and user audio
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoundFingerprint {
    
    /**
     * Mel-frequency cepstral coefficients (typically 13)
     * Captures the timbral "color" of the sound
     */
    private double[] mfcc;
    
    /**
     * Spectral centroid in Hz
     * Higher = brighter/sharper sound, Lower = duller/muffled sound
     * Typical clap: 2000-4000 Hz
     */
    private double spectralCentroid;
    
    /**
     * Spectral rolloff frequency in Hz
     * Frequency below which 85% of spectral energy is contained
     */
    private double spectralRolloff;
    
    /**
     * Zero crossing rate (0-1 normalized)
     * Higher = more percussive/noisy, Lower = more tonal
     */
    private double zeroCrossingRate;
    
    /**
     * RMS energy (amplitude)
     * Normalized 0-1
     */
    private double rmsEnergy;
    
    /**
     * Spectral flatness (0-1)
     * 1 = noise-like (white noise), 0 = tonal
     * Good claps typically 0.3-0.7
     */
    private double spectralFlatness;
    
    /**
     * Duration of the transient in milliseconds
     */
    private double transientDurationMs;
    
    /**
     * Quality assessment based on spectral features
     */
    public String getQualityAssessment() {
        if (spectralCentroid > 3500 && zeroCrossingRate > 0.3) {
            return "SHARP";
        } else if (spectralCentroid < 1500 || zeroCrossingRate < 0.15) {
            return "MUFFLED";
        } else {
            return "CLEAR";
        }
    }
}
