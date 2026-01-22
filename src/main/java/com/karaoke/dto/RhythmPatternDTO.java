package com.karaoke.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RhythmPatternDTO {
    
    @Builder.Default
    private Integer version = 1;
    
    /** Onset times in milliseconds, normalized so first onset = 0 */
    private List<Double> onsetTimesMs;
    
    /** Intervals between consecutive onsets in milliseconds */
    private List<Double> intervalsMs;
    
    /** Estimated BPM based on average interval */
    private Integer estimatedBpm;
    
    /** Time signature (e.g., "4/4", "3/4") */
    private String timeSignature;
    
    /** Total number of detected beats/onsets */
    private Integer totalBeats;
    
    /** Original start time that was trimmed (silence removal) */
    private Double trimmedStartMs;
    
    /** End time of last sound in original audio */
    private Double trimmedEndMs;
    
    /** Original audio duration before trimming */
    private Double originalDurationMs;
    
    /** Silence threshold used for detection (dB) */
    @Builder.Default
    private Double silenceThresholdDb = -40.0;
    
    /** Minimum interval between onsets (debounce) in ms */
    @Builder.Default
    private Double minOnsetIntervalMs = 100.0;

    /**
     * Spectral fingerprint for each detected beat
     * Used for sound similarity comparison
     */
    private List<SoundFingerprint> beatFingerprints;
    
    /**
     * Whether sound similarity scoring is enabled for this pattern
     */
    @Builder.Default
    private Boolean soundSimilarityEnabled = false;
    
    /**
     * Weight for timing score (0-1), default 0.7
     */
    @Builder.Default
    private Double timingWeight = 0.7;
    
    /**
     * Weight for sound similarity score (0-1), default 0.3
     */
    @Builder.Default
    private Double soundWeight = 0.3;
    
    /**
     * Reference audio path for re-analysis if needed
     */
    private String referenceAudioPath;
}
