package com.karaoke.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed comparison of a single beat's sound characteristics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoundComparisonDetail {
    
    private Integer beatIndex;
    
    /** MFCC cosine similarity (0-100) */
    private Double mfccSimilarity;
    
    /** Reference spectral centroid in Hz */
    private Double spectralCentroidRef;
    
    /** User's spectral centroid in Hz */
    private Double spectralCentroidUser;
    
    /** Brightness match score (0-100) */
    private Double brightnessMatch;
    
    /** Energy/loudness match score (0-100) */
    private Double energyMatch;
    
    /** Overall sound similarity for this beat (0-100) */
    private Double overallSoundScore;
    
    /** Quality assessment of user's sound */
    private String userQuality;
    
    /** Quality assessment of reference sound */
    private String referenceQuality;
    
    /** Human-readable feedback for this beat */
    private String feedback;
    
    public static SoundComparisonDetail createMissed(int beatIndex) {
        return SoundComparisonDetail.builder()
                .beatIndex(beatIndex)
                .mfccSimilarity(0.0)
                .brightnessMatch(0.0)
                .energyMatch(0.0)
                .overallSoundScore(0.0)
                .feedback("Beat missed - no sound detected")
                .build();
    }
}
