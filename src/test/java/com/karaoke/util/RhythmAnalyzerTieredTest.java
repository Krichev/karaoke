package com.karaoke.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karaoke.dto.RhythmPatternDTO;
import com.karaoke.dto.RhythmScoringResultDTO;
import com.karaoke.dto.ToleranceTiers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RhythmAnalyzerTieredTest {

    private RhythmAnalyzer rhythmAnalyzer;
    
    @Mock
    private SoundAnalyzer soundAnalyzer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rhythmAnalyzer = new RhythmAnalyzer(new ObjectMapper(), soundAnalyzer);
    }

    @Test
    void scoreRhythmPatternTiered_Interpolation() {
        RhythmPatternDTO reference = RhythmPatternDTO.builder()
                .onsetTimesMs(List.of(0.0, 500.0, 1000.0, 1500.0))
                .intervalsMs(List.of(500.0, 500.0, 500.0))
                .totalBeats(4)
                .build();

        // MEDIUM, strictness 50 -> 100, 200, 300
        ToleranceTiers tiers = ToleranceTiers.builder()
                .perfectThresholdMs(100.0)
                .goodThresholdMs(200.0)
                .okThresholdMs(300.0)
                .difficulty("MEDIUM")
                .toleranceStrictness(50)
                .build();

        // Case 1: Perfect (0ms error) -> 100 score
        // Case 2: Good boundary (100ms error) -> 90 score
        // Case 3: OK boundary (200ms error) -> 70 score
        // Case 4: Miss boundary (300ms error) -> 30 score
        List<Double> userOnsets = List.of(0.0, 600.0, 1200.0, 1800.0);

        RhythmScoringResultDTO result = rhythmAnalyzer.scoreRhythmPatternTiered(reference, userOnsets, tiers, null);

        assertEquals("TIERED_V1", result.getScoringModel());
        assertEquals(4, result.getPerBeatScores().size());
        
        // Beat 0: 0ms error -> PERFECT (100)
        assertEquals(100.0, result.getPerBeatScores().get(0));
        assertEquals("PERFECT", result.getPerBeatTiers().get(0));

        // Beat 1: 100ms error -> PERFECT boundary (90)
        assertEquals(90.0, result.getPerBeatScores().get(1));
        assertEquals("PERFECT", result.getPerBeatTiers().get(1));

        // Beat 2: 200ms error -> GOOD boundary (70)
        assertEquals(70.0, result.getPerBeatScores().get(2));
        assertEquals("GOOD", result.getPerBeatTiers().get(2));

        // Beat 3: 300ms error -> OK boundary (30)
        assertEquals(30.0, result.getPerBeatScores().get(3));
        assertEquals("OK", result.getPerBeatTiers().get(3));
    }

    @Test
    void scoreRhythmPatternTiered_Counts() {
        RhythmPatternDTO reference = RhythmPatternDTO.builder()
                .onsetTimesMs(List.of(0.0, 500.0, 1000.0, 1500.0))
                .intervalsMs(List.of(500.0, 500.0, 500.0))
                .totalBeats(4)
                .build();

        ToleranceTiers tiers = ToleranceTiers.builder()
                .perfectThresholdMs(100.0)
                .goodThresholdMs(200.0)
                .okThresholdMs(300.0)
                .build();

        List<Double> userOnsets = List.of(0.0, 550.0, 1150.0, 1450.0); 
        // 0ms, 50ms, 150ms, 50ms errors
        // P, P, G, P

        RhythmScoringResultDTO result = rhythmAnalyzer.scoreRhythmPatternTiered(reference, userOnsets, tiers, null);

        assertEquals(3, result.getPerfectBeats());
        assertEquals(1, result.getGoodBeats());
        assertEquals(0, result.getOkBeats());
        assertEquals(0, result.getMissedBeats());
    }

    @Test
    void backwardCompatibility_FlatModel() {
        RhythmPatternDTO reference = RhythmPatternDTO.builder()
                .onsetTimesMs(List.of(0.0, 500.0))
                .intervalsMs(List.of(500.0))
                .totalBeats(2)
                .build();

        RhythmScoringResultDTO result = rhythmAnalyzer.scoreRhythmPattern(reference, List.of(0.0, 500.0), 100.0, null);

        assertEquals("FLAT", result.getScoringModel());
        assertNull(result.getToleranceTiers());
        assertNull(result.getPerBeatTiers());
    }
}
