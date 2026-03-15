package com.karaoke.service;

import com.karaoke.dto.ToleranceTiers;
import com.karaoke.model.enums.ScoringTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToleranceTierCalculatorTest {

    private ToleranceTierCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ToleranceTierCalculator();
    }

    @Test
    void compute_DefaultValues() {
        // Default: MEDIUM, strictness 60
        // Base MEDIUM: PERFECT=100, GOOD=200, OK=300
        // Multiplier at 60: 1 - (60-50)/200 = 0.95
        // Expected: PERFECT=95, GOOD=190, OK=285
        ToleranceTiers tiers = calculator.compute(null, null);

        assertEquals("MEDIUM", tiers.getDifficulty());
        assertEquals(60, tiers.getToleranceStrictness());
        assertEquals(95.0, tiers.getPerfectThresholdMs(), 0.001);
        assertEquals(190.0, tiers.getGoodThresholdMs(), 0.001);
        assertEquals(285.0, tiers.getOkThresholdMs(), 0.001);
    }

    @Test
    void compute_EasyDifficulty() {
        // EASY: PERFECT=150, GOOD=250, OK=400
        // Strictness 50: Multiplier = 1.0
        ToleranceTiers tiers = calculator.compute("EASY", 50);

        assertEquals(150.0, tiers.getPerfectThresholdMs(), 0.001);
        assertEquals(250.0, tiers.getGoodThresholdMs(), 0.001);
        assertEquals(400.0, tiers.getOkThresholdMs(), 0.001);
    }

    @Test
    void compute_HardDifficulty() {
        // HARD: PERFECT=80, GOOD=150, OK=250
        // Strictness 50: Multiplier = 1.0
        ToleranceTiers tiers = calculator.compute("HARD", 50);

        assertEquals(80.0, tiers.getPerfectThresholdMs(), 0.001);
        assertEquals(150.0, tiers.getGoodThresholdMs(), 0.001);
        assertEquals(250.0, tiers.getOkThresholdMs(), 0.001);
    }

    @Test
    void compute_StrictnessExtremes() {
        // MEDIUM: 100, 200, 300
        
        // Strictness 100: Multiplier 0.75
        ToleranceTiers tight = calculator.compute("MEDIUM", 100);
        assertEquals(75.0, tight.getPerfectThresholdMs(), 0.001);

        // Strictness 0: Multiplier 1.0 - (0-50)/200 = 1.25, clamped to 1.2
        ToleranceTiers loose = calculator.compute("MEDIUM", 0);
        assertEquals(120.0, loose.getPerfectThresholdMs(), 0.001);
    }

    @Test
    void compute_InvalidInputs() {
        // Unknown difficulty -> MEDIUM
        ToleranceTiers tiers = calculator.compute("EXTREME", 60);
        assertEquals("MEDIUM", tiers.getDifficulty());
        
        // Out of range strictness -> clamped
        ToleranceTiers clamped = calculator.compute("MEDIUM", 150);
        assertEquals(100, clamped.getToleranceStrictness());
    }

    @Test
    void tierFor_BoundaryTesting() {
        ToleranceTiers tiers = calculator.compute("MEDIUM", 50); // 100, 200, 300
        
        assertEquals(ScoringTier.PERFECT, tiers.tierFor(0));
        assertEquals(ScoringTier.PERFECT, tiers.tierFor(100));
        assertEquals(ScoringTier.GOOD, tiers.tierFor(100.1));
        assertEquals(ScoringTier.GOOD, tiers.tierFor(200));
        assertEquals(ScoringTier.OK, tiers.tierFor(200.1));
        assertEquals(ScoringTier.OK, tiers.tierFor(300));
        assertEquals(ScoringTier.MISS, tiers.tierFor(300.1));
    }
}
