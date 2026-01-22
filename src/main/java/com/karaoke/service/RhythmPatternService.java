package com.karaoke.service;

import com.karaoke.controller.RhythmController.RhythmScoreRequest;
import com.karaoke.controller.RhythmController.RhythmScoreWithAudioRequest;
import com.karaoke.dto.RhythmPatternDTO;
import com.karaoke.dto.RhythmScoringResultDTO;
import com.karaoke.util.RhythmAnalyzer;
import com.karaoke.util.SoundAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RhythmPatternService {

    private final RhythmAnalyzer rhythmAnalyzer;
    private final SoundAnalyzer soundAnalyzer;

    public RhythmPatternDTO extractPatternFromUpload(
            MultipartFile audioFile,
            Double silenceThresholdDb,
            Double minOnsetIntervalMs) {
        
        Path tempFile = null;
        try {
            // Save to temp file for processing
            String extension = getFileExtension(audioFile.getOriginalFilename());
            tempFile = Files.createTempFile("rhythm_", extension);
            audioFile.transferTo(tempFile.toFile());
            
            log.info("📁 Saved upload to temp file: {}", tempFile);
            
            // Extract pattern
            RhythmPatternDTO pattern = rhythmAnalyzer.extractRhythmPattern(
                    tempFile.toString(),
                    silenceThresholdDb,
                    minOnsetIntervalMs);
            
            log.info("✅ Extracted pattern with {} beats", pattern.getTotalBeats());
            
            return pattern;
            
        } catch (Exception e) {
            log.error("❌ Error extracting rhythm pattern: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract rhythm pattern", e);
        } finally {
            // Cleanup temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {}
            }
        }
    }

    public RhythmPatternDTO extractPatternFromPath(String audioPath, Double silenceThresholdDb, Double minOnsetIntervalMs) {
        return rhythmAnalyzer.extractRhythmPattern(audioPath, silenceThresholdDb, minOnsetIntervalMs);
    }

    public RhythmScoringResultDTO scoreRhythmSubmission(RhythmScoreRequest request) {
        return rhythmAnalyzer.scoreRhythmPattern(
                request.getReferencePattern(),
                request.getUserOnsetTimesMs(),
                request.getToleranceMs(),
                request.getMinimumScoreRequired());
    }

    public RhythmPatternDTO extractPatternWithFingerprints(
            MultipartFile audioFile,
            Double silenceThresholdDb,
            Double minOnsetIntervalMs,
            Boolean enableSoundSimilarity) {

        Path tempFile = null;
        try {
            String extension = getFileExtension(audioFile.getOriginalFilename());
            tempFile = Files.createTempFile("rhythm_", extension);
            audioFile.transferTo(tempFile.toFile());

            RhythmPatternDTO pattern = rhythmAnalyzer.extractRhythmPatternWithFingerprints(
                    tempFile.toString(),
                    silenceThresholdDb,
                    minOnsetIntervalMs,
                    enableSoundSimilarity != null && enableSoundSimilarity);

            return pattern;

        } catch (Exception e) {
            log.error("❌ Error extracting pattern with fingerprints: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract rhythm pattern", e);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    public RhythmScoringResultDTO scoreRhythmWithSoundSimilarity(RhythmScoreWithAudioRequest request) {
        return rhythmAnalyzer.scoreRhythmWithSoundSimilarity(
                request.getReferencePattern(),
                request.getUserOnsetTimesMs(),
                request.getUserAudioPath(),
                request.getEnableSoundSimilarity() != null && request.getEnableSoundSimilarity(),
                request.getToleranceMs(),
                request.getTimingWeight(),
                request.getSoundWeight(),
                request.getMinimumScoreRequired());
    }

    public RhythmScoringResultDTO scoreAudioFile(
            MultipartFile audioFile,
            Long questionId,
            Boolean enableSoundSimilarity,
            Double toleranceMs) {

        Path tempFile = null;
        try {
            // Save uploaded file temporarily
            String extension = getFileExtension(audioFile.getOriginalFilename());
            tempFile = Files.createTempFile("user_rhythm_", extension);
            audioFile.transferTo(tempFile.toFile());

            // Get question's reference pattern (would need to fetch from database)
            // For now, assume pattern is passed in or fetched elsewhere
            // This is a placeholder - you'd need to integrate with your question repository

            // Extract user onsets
            RhythmPatternDTO userPattern = rhythmAnalyzer.extractRhythmPattern(
                    tempFile.toString(), -40.0, 100.0);

            // Score would need reference pattern from question
            // This is a simplified implementation
            RhythmScoringResultDTO result = RhythmScoringResultDTO.builder()
                    .overallScore(0.0)
                    .feedback("Pattern comparison requires reference - implement question lookup")
                    .build();

            return result;

        } catch (Exception e) {
            log.error("❌ Error scoring audio file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to score audio", e);
        } finally {
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) return ".wav";
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : ".wav";
    }
}
