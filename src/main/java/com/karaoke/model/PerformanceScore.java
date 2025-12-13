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
    @JoinColumn(name = "performance_id", nullable = false)
    private Performance performance;
    
    @Column(nullable = false)
    private Double pitchScore;
    
    @Column(nullable = false)
    private Double rhythmScore;
    
    @Column(nullable = false)
    private Double voiceQualityScore;
    
    @Column(columnDefinition = "TEXT")
    private String detailedMetrics; // JSON string with detailed analysis
}
