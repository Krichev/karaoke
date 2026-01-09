package com.karaoke.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericScoringResponse {
    private Double pitchScore;
    private Double rhythmScore;
    private Double voiceScore;
    private Double overallScore;
    private String detailedMetrics;
}
