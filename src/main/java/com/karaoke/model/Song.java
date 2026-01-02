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
@Table(name = "songs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Song {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;
    
    @Column(nullable = false, length = 200)
    private String title;
    
    @Column(nullable = false, length = 200)
    private String artist;
    
    @Column(nullable = false)
    private Integer duration; // in seconds
    
    @Column(length = 50)
    private String genre;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false, columnDefinition = "difficulty_level_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DifficultyLevel difficultyLevel = DifficultyLevel.MEDIUM;

    @Column(name = "reference_audio_path", nullable = false, columnDefinition = "TEXT")
    private String referenceAudioPath;
    
    @Column(name = "reference_pitch_data", columnDefinition = "TEXT")
    private String referencePitchData; // JSON string

    @Column(name = "reference_rhythm_data", columnDefinition = "TEXT")
    private String referenceRhythmData; // JSON string

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_processing_status", nullable = false, columnDefinition = "processing_status_enum")
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ProcessingStatus referenceProcessingStatus = ProcessingStatus.PENDING;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum DifficultyLevel {
        EASY, MEDIUM, HARD
    }
}
