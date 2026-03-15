package com.karaoke.dto;

import com.karaoke.model.enums.ScoringTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Holds calculated tolerance thresholds for tiered rhythm scoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToleranceTiers {
    private double perfectThresholdMs;
    private double goodThresholdMs;
    private double okThresholdMs;
    private String difficulty;
    private int toleranceStrictness;

    /**
     * Determines the scoring tier for a given absolute error in milliseconds.
     */
    public ScoringTier tierFor(double absErrorMs) {
        if (absErrorMs <= perfectThresholdMs) {
            return ScoringTier.PERFECT;
        } else if (absErrorMs <= goodThresholdMs) {
            return ScoringTier.GOOD;
        } else if (absErrorMs <= okThresholdMs) {
            return ScoringTier.OK;
        } else {
            return ScoringTier.MISS;
        }
    }
}
