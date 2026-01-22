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
public class RhythmScoringResultDTO {
    
    /** Overall rhythm accuracy score 0-100 */
    private Double overallScore;
    
    /** Individual score for each beat (0-100) */
    private List<Double> perBeatScores;
    
    /** Timing error in ms for each beat (negative = early, positive = late) */
    private List<Double> timingErrorsMs;
    
    /** Absolute timing errors */
    private List<Double> absoluteErrorsMs;
    
    /** Number of beats with < 50ms error */
    private Integer perfectBeats;
    
    /** Number of beats with 50-150ms error */
    private Integer goodBeats;
    
    /** Number of beats with > interval/2 error (missed) */
    private Integer missedBeats;
    
    /** Average timing error across all beats */
    private Double averageErrorMs;
    
    /** Maximum single beat error */
    private Double maxErrorMs;
    
    /** Consistency score - how regular user's rhythm was */
    private Double consistencyScore;
    
    /** Human-readable feedback */
    private String feedback;
    
    /** Did user pass the minimum accuracy threshold */
    private Boolean passed;
    
    /** The minimum score required to pass */
    private Integer minimumScoreRequired;

    // ===== SOUND SIMILARITY FIELDS =====
    
    /** Overall sound similarity score (0-100) */
    private Double soundSimilarityScore;
    
    /** Sound similarity score for each beat */
    private List<Double> perBeatSoundScores;
    
    /** Detailed sound comparison for each beat */
    private List<SoundComparisonDetail> soundDetails;
    
    /** Whether sound similarity was evaluated */
    private Boolean soundSimilarityEnabled;
    
    /** Weight applied to timing score */
    private Double timingWeight;
    
    /** Weight applied to sound similarity score */
    private Double soundWeight;
    
    /** Combined weighted score (timing + sound) */
    private Double combinedScore;
    
    /** Number of beats with good sound match (>70%) */
    private Integer goodSoundMatches;
    
    /** Average brightness difference from reference */
    private Double averageBrightnessDiff;
    
    /** Sound quality feedback */
    private String soundFeedback;
}
