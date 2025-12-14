package com.karaoke.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karaoke.model.Performance;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Detailed performance scoring results")
public class PerformanceScoreDTO {
    @Schema(description = "Performance identifier", example = "perf_xyz789")
    private String performanceId;
    
    @Schema(description = "Song title", example = "Bohemian Rhapsody")
    private String songTitle;
    
    @Schema(description = "Artist name", example = "Queen")
    private String songArtist;
    
    @Schema(description = "Overall weighted score (0-100)", example = "85.7")
    private Double totalScore;
    
    @Schema(description = "Pitch accuracy score (0-100) - measures semitone deviations", example = "88.5")
    private Double pitchScore;
    
    @Schema(description = "Rhythm timing score (0-100) - measures onset timing", example = "82.3")
    private Double rhythmScore;
    
    @Schema(description = "Voice similarity score (0-100) - MFCC analysis", example = "78.9")
    private Double voiceQualityScore;
    
    @Schema(description = "Detailed metrics breakdown including pitch deviations, timing offsets, and spectral analysis")
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