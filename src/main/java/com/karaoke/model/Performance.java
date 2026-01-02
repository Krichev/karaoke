package com.karaoke.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "performances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Performance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(name = "user_id", nullable = false)
    private Long userId;  // Changed from String to Long to match Challenger service

    @Column(name = "audio_file_path", nullable = false, columnDefinition = "TEXT")
    private String audioFilePath;
    
    @OneToOne(mappedBy = "performance", cascade = CascadeType.ALL, orphanRemoval = true)
    private PerformanceScore performanceScore;

    @Column(name = "total_score")
    private Double totalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, columnDefinition = "processing_status_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "processing_progress")
    private Integer processingProgress = 0; // 0-100

    @Column(name = "processing_message", length = 500)
    private String processingMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}