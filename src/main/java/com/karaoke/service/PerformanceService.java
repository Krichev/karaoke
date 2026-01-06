package com.karaoke.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karaoke.dto.PerformanceResponseDTO;
import com.karaoke.dto.PerformanceScoreDTO;
import com.karaoke.dto.PerformanceStatusDTO;
import com.karaoke.exception.ResourceNotFoundException;
import com.karaoke.model.NoteEvent;
import com.karaoke.model.Performance;
import com.karaoke.model.PerformanceScore;
import com.karaoke.model.ProcessingStatus;
import com.karaoke.model.Song;
import com.karaoke.repository.PerformanceRepository;
import com.karaoke.util.AudioProcessor;
import com.karaoke.util.ScoringEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PerformanceService {
    
    private final PerformanceRepository performanceRepository;
    private final SongService songService;
    private final AudioStorageService audioStorageService;
    private final AudioProcessor audioProcessor;
    private final ScoringEngine scoringEngine;
    private final ObjectMapper objectMapper;

    public PerformanceService(
        PerformanceRepository performanceRepository,
        SongService songService,
        AudioStorageService audioStorageService,
        AudioProcessor audioProcessor,
        ScoringEngine scoringEngine,
        ObjectMapper objectMapper
    ) {
        this.performanceRepository = performanceRepository;
        this.songService = songService;
        this.audioStorageService = audioStorageService;
        this.audioProcessor = audioProcessor;
        this.scoringEngine = scoringEngine;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Upload and queue performance for processing
     */
    @Transactional
    public PerformanceResponseDTO uploadPerformance(
        String songId,
        Long userId,
        MultipartFile audioFile
    ) {
        try {
            Song song = songService.getSongById(songId);
            
            // Validate song has processed reference data
            if (song.getReferenceProcessingStatus() != ProcessingStatus.COMPLETED) {
                throw new IllegalStateException("Song reference data not yet processed");
            }
            
            // Create performance record
            Performance performance = new Performance();
            performance.setSong(song);
            performance.setUserId(userId);
            performance.setProcessingStatus(ProcessingStatus.PENDING);
            performance.setProcessingProgress(0);
            performance.setProcessingMessage("Uploading...");
            
            // Save to get ID
            performance = performanceRepository.save(performance);
            
            // Store audio file (stores S3 key)
            String s3Key = audioStorageService.storeRecording(audioFile, userId, songId);
            performance.setAudioFilePath(s3Key);
            performance.setProcessingMessage("Queued for processing");
            performance = performanceRepository.save(performance);
            
            // Process asynchronously
            processPerformance(performance.getId());
            
            log.info("Uploaded performance {} for user {} on song {}", 
                     performance.getId(), userId, songId);
            
            return new PerformanceResponseDTO(
                performance.getId(),
                ProcessingStatus.PROCESSING.name(),
                "Performance uploaded successfully, processing in progress"
            );
            
        } catch (Exception e) {
            log.error("Error uploading performance", e);
            throw new RuntimeException("Failed to upload performance", e);
        }
    }
    
    /**
     * Get performance status
     */
    public PerformanceStatusDTO getPerformanceStatus(String performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Performance not found: " + performanceId));
        
        return PerformanceStatusDTO.fromEntity(performance);
    }
    
    /**
     * Get performance scores
     */
    public PerformanceScoreDTO getPerformanceScores(String performanceId) {
        try {
            Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance not found: " + performanceId));
            
            if (performance.getProcessingStatus() != ProcessingStatus.COMPLETED) {
                throw new IllegalStateException("Performance processing not yet completed");
            }
            
            return PerformanceScoreDTO.fromEntity(performance, objectMapper);
            
        } catch (Exception e) {
            log.error("Error retrieving performance scores for {}", performanceId, e);
            throw new RuntimeException("Failed to retrieve scores", e);
        }
    }
    
    /**
     * Process performance asynchronously
     */
    @Async("audioProcessingExecutor")
    @Transactional
    public void processPerformance(String performanceId) {
        Performance performance = null;
        
        try {
            performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Performance not found"));
            
            Song song = performance.getSong();
            
            // Update status
            performance.setProcessingStatus(ProcessingStatus.PROCESSING);
            performance.setProcessingProgress(10);
            performance.setProcessingMessage("Analyzing audio...");
            performanceRepository.save(performance);
            
            // Download user recording from MinIO
            byte[] userAudioBytes = audioStorageService.downloadAudio(performance.getAudioFilePath());

            // Extract note events from user recording
            log.info("Extracting note events for performance {}", performanceId);
            List<NoteEvent> userNotes = audioProcessor.extractNoteEvents(userAudioBytes);

            performance.setProcessingProgress(30);
            performance.setProcessingMessage("Extracting voice features...");
            performanceRepository.save(performance);

            // Extract MFCCs for voice similarity
            List<double[]> userMFCCs = audioProcessor.extractMFCCs(userAudioBytes);

            performance.setProcessingProgress(50);
            performance.setProcessingMessage("Loading reference data...");
            performanceRepository.save(performance);

            // Get reference note events (need to parse from referencePitchData or process reference audio)
            // For now, convert pitch data to note events
            List<NoteEvent> referenceNotes = convertPitchDataToNoteEvents(song.getReferencePitchData());

            performance.setProcessingProgress(60);
            performance.setProcessingMessage("Processing reference voice features...");
            performanceRepository.save(performance);

            // Download reference audio from MinIO and extract MFCCs
            byte[] referenceAudioBytes = audioStorageService.downloadAudio(song.getReferenceAudioPath());
            List<double[]> referenceMFCCs = audioProcessor.extractMFCCs(referenceAudioBytes);

            performance.setProcessingProgress(70);
            performance.setProcessingMessage("Calculating scores...");
            performanceRepository.save(performance);

            // Calculate enhanced scores
            double pitchScore = scoringEngine.calculatePitchScoreSemitones(userNotes, referenceNotes);
            double rhythmScore = scoringEngine.calculateRhythmScoreOnsets(userNotes, referenceNotes);
            double voiceQualityScore = scoringEngine.calculateVoiceSimilarityMFCC(userMFCCs, referenceMFCCs);
            double overallScore = (pitchScore * 0.5) + (rhythmScore * 0.3) + (voiceQualityScore * 0.2);

            // Generate detailed metrics
            String detailedMetrics = scoringEngine.generateEnhancedMetrics(
                userNotes, referenceNotes, userMFCCs, referenceMFCCs, 
                pitchScore, rhythmScore, voiceQualityScore
            );
            
            performance.setProcessingProgress(80);
            performance.setProcessingMessage("Finalizing...");
            performanceRepository.save(performance);
            
            // Create performance score
            PerformanceScore score = new PerformanceScore();
            score.setPerformance(performance);
            score.setPitchScore(pitchScore);
            score.setRhythmScore(rhythmScore);
            score.setVoiceQualityScore(voiceQualityScore);
            score.setDetailedMetrics(detailedMetrics);
            
            performance.setPerformanceScore(score);
            performance.setTotalScore(overallScore);
            performance.setProcessingStatus(ProcessingStatus.COMPLETED);
            performance.setProcessingProgress(100);
            performance.setProcessingMessage("Processing completed successfully");
            performanceRepository.save(performance);

            log.info("Completed processing performance {} with overall score: {}", 
                     performanceId, overallScore);
            
        } catch (Exception e) {
            log.error("Error processing performance {}", performanceId, e);
            
            if (performance != null) {
                performance.setProcessingStatus(ProcessingStatus.FAILED);
                performance.setProcessingMessage("Processing failed: " + e.getMessage());
                performanceRepository.save(performance);
            }
        }
    }

    /**
     * Convert legacy pitch data JSON to NoteEvent list
     * Temporary solution until we update Song processing to store NoteEvents
     */
    private List<NoteEvent> convertPitchDataToNoteEvents(String pitchDataJson) {
        try {
            List<Double> pitches = objectMapper.readValue(
                pitchDataJson,
                new TypeReference<List<Double>>() {}
            );
            
            List<NoteEvent> notes = new ArrayList<>();
            double timeMs = 0;
            double intervalMs = 100; // Assume 100ms between samples
            
            for (Double pitch : pitches) {
                notes.add(new NoteEvent(timeMs, pitch, intervalMs, 0.5));
                timeMs += intervalMs;
            }
            
            return notes;
            
        } catch (Exception e) {
            log.error("Error converting pitch data to note events", e);
            return new ArrayList<>();
        }
    }
}