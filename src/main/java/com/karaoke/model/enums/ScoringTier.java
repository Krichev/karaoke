package com.karaoke.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents the scoring tiers for rhythm accuracy based on human reaction time.
 */
@Getter
@RequiredArgsConstructor
public enum ScoringTier {
    PERFECT("PERFECT"),
    GOOD("GOOD"),
    OK("OK"),
    MISS("MISS");

    private final String displayName;

    public boolean isPass() {
        return this != MISS;
    }
}
