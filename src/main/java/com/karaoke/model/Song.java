package com.karaoke.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;
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
    @Column(nullable = false)
    private DifficultyLevel difficultyLevel = DifficultyLevel.MEDIUM;
    
    @Column(nullable = false)
    private String referenceAudioPath;
    
    @Column(columnDefinition = "TEXT")
    private String referencePitchData; // JSON string
    
    @Column(columnDefinition = "TEXT")
    private String referenceRhythmData; // JSON string
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus referenceProcessingStatus = ProcessingStatus.PENDING;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @UpdateTimestamp // Use @UpdateTimestamp for automatic update on entity modification
    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum DifficultyLevel {
        EASY, MEDIUM, HARD
    }
}
