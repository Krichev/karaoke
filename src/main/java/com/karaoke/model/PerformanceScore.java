package com.karaoke.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "performance_scores")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceScore {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id", nullable = false, unique = true)
    private Performance performance;

    @Column(name = "pitch_score", nullable = false)
    private Double pitchScore;

    @Column(name = "rhythm_score", nullable = false)
    private Double rhythmScore;

    @Column(name = "voice_quality_score", nullable = false)
    private Double voiceQualityScore;

    @Column(name = "detailed_metrics", columnDefinition = "TEXT")
    private String detailedMetrics; // JSON string with detailed analysis
}
