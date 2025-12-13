package com.karaoke.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karaoke.model.Performance;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceScoreDTO {
    private String performanceId;
    private String songTitle;
    private String songArtist;
    private Double totalScore;
    private Double pitchScore;
    private Double rhythmScore;
    private Double voiceQualityScore;
    private Map<String, Object> detailedMetrics;
    
    public static PerformanceScoreDTO fromEntity(Performance performance, ObjectMapper objectMapper) {
        try {
            Map<String, Object> metrics = null;
            
            if (performance.getPerformanceScore() != null && 
                performance.getPerformanceScore().getDetailedMetrics() != null) {
                metrics = objectMapper.readValue(
                    performance.getPerformanceScore().getDetailedMetrics(),
                    new TypeReference<Map<String, Object>>() {}
                );
            }
            
            return new PerformanceScoreDTO(
                performance.getId(),
                performance.getSong().getTitle(),
                performance.getSong().getArtist(),
                performance.getTotalScore(),
                performance.getPerformanceScore().getPitchScore(),
                performance.getPerformanceScore().getRhythmScore(),
                performance.getPerformanceScore().getVoiceQualityScore(),
                metrics
            );
        } catch (Exception e) {
            throw new RuntimeException("Error converting performance to DTO", e);
        }
    }
}