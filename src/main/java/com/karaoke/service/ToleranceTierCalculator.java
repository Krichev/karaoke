package com.karaoke.service;

import com.karaoke.dto.ToleranceTiers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Calculates tiered tolerance windows based on difficulty and strictness.
 * Grounded in human reaction time research:
 * - Elite: ~80-100ms
 * - High: 150-200ms
 * - Average: 200-250ms
 */
@Component
@Slf4j
public class ToleranceTierCalculator {

    private static final String DEFAULT_DIFFICULTY = "MEDIUM";
    private static final int DEFAULT_STRICTNESS = 60;

    /**
     * Computes tiered thresholds based on difficulty and strictness factor.
     * 
     * @param difficulty EASY, MEDIUM, or HARD
     * @param toleranceStrictness 0-100 (60 is default)
     * @return Calculated thresholds
     */
    public ToleranceTiers compute(String difficulty, Integer toleranceStrictness) {
        String diff = difficulty != null ? difficulty.toUpperCase() : DEFAULT_DIFFICULTY;
        int strictness = toleranceStrictness != null ? toleranceStrictness : DEFAULT_STRICTNESS;

        // Clamp strictness
        if (strictness < 0 || strictness > 100) {
            log.warn("Tolerance strictness {} out of range (0-100), clamping.", strictness);
            strictness = Math.max(0, Math.min(100, strictness));
        }

        // Base thresholds table
        double perfectBase;
        double goodBase;
        double okBase;

        switch (diff) {
            case "EASY":
                perfectBase = 150.0;
                goodBase = 250.0;
                okBase = 400.0;
                break;
            case "HARD":
                perfectBase = 80.0;
                goodBase = 150.0;
                okBase = 250.0;
                break;
            case "MEDIUM":
            default:
                if (!"MEDIUM".equals(diff)) {
                    log.warn("Unknown difficulty level: {}. Defaulting to MEDIUM.", difficulty);
                    diff = "MEDIUM";
                }
                perfectBase = 100.0;
                goodBase = 200.0;
                okBase = 300.0;
                break;
        }

        // Multiplier formula: adjustedThreshold = baseThreshold × (1 - (toleranceStrictness - 50) / 200)
        // At 50, multiplier = 1.0
        // At 60, multiplier = 0.95
        // At 80, multiplier = 0.85
        // At 100, multiplier = 0.75
        // Multiplier is clamped to [0.75, 1.2]
        double multiplier = 1.0 - (double) (strictness - 50) / 200.0;
        multiplier = Math.max(0.75, Math.min(1.2, multiplier));

        return ToleranceTiers.builder()
                .perfectThresholdMs(perfectBase * multiplier)
                .goodThresholdMs(goodBase * multiplier)
                .okThresholdMs(okBase * multiplier)
                .difficulty(diff)
                .toleranceStrictness(strictness)
                .build();
    }
}
