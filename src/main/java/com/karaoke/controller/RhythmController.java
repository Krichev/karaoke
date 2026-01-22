package com.karaoke.controller;

import com.karaoke.dto.RhythmPatternDTO;
import com.karaoke.dto.RhythmScoringResultDTO;
import com.karaoke.service.RhythmPatternService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/rhythm")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Rhythm Pattern", description = "Rhythm pattern extraction and scoring for tap/clap challenges")
public class RhythmController {

    private final RhythmPatternService rhythmPatternService;

    @PostMapping("/extract-pattern")
    @Operation(summary = "Extract rhythm pattern from audio",
            description = "Analyze audio file to extract onset times, creating a rhythm pattern for question creation")
    public ResponseEntity<RhythmPatternDTO> extractPattern(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "silenceThresholdDb", required = false, defaultValue = "-40") Double silenceThresholdDb,
            @RequestParam(value = "minOnsetIntervalMs", required = false, defaultValue = "100") Double minOnsetIntervalMs) {
        
        log.info("🎵 Extracting rhythm pattern from uploaded audio");
        
        RhythmPatternDTO pattern = rhythmPatternService.extractPatternFromUpload(
                audioFile, silenceThresholdDb, minOnsetIntervalMs);
        
        return ResponseEntity.ok(pattern);
    }

    @PostMapping("/score")
    @Operation(summary = "Score user rhythm against reference pattern",
            description = "Compare user's tap/clap timing against the reference pattern")
    public ResponseEntity<RhythmScoringResultDTO> scoreRhythm(
            @RequestBody RhythmScoreRequest request) {
        
        log.info("🎯 Scoring rhythm submission");
        
        RhythmScoringResultDTO result = rhythmPatternService.scoreRhythmSubmission(request);
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/score-with-audio")
    @Operation(summary = "Score rhythm with optional sound similarity",
            description = "Compare user's rhythm and optionally sound quality against reference")
    public ResponseEntity<RhythmScoringResultDTO> scoreRhythmWithAudio(
            @RequestBody RhythmScoreWithAudioRequest request) {

        log.info("🎵 Scoring rhythm with sound similarity: enabled={}", request.getEnableSoundSimilarity());

        RhythmScoringResultDTO result = rhythmPatternService.scoreRhythmWithSoundSimilarity(request);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/extract-pattern-with-fingerprints")
    @Operation(summary = "Extract rhythm pattern with sound fingerprints",
            description = "Analyze audio to extract onsets and spectral fingerprints for each beat")
    public ResponseEntity<RhythmPatternDTO> extractPatternWithFingerprints(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam(value = "silenceThresholdDb", required = false, defaultValue = "-40") Double silenceThresholdDb,
            @RequestParam(value = "minOnsetIntervalMs", required = false, defaultValue = "100") Double minOnsetIntervalMs,
            @RequestParam(value = "enableSoundSimilarity", required = false, defaultValue = "true") Boolean enableSoundSimilarity) {

        log.info("🎵 Extracting rhythm pattern with fingerprints: soundSimilarity={}", enableSoundSimilarity);

        RhythmPatternDTO pattern = rhythmPatternService.extractPatternWithFingerprints(
                audioFile, silenceThresholdDb, minOnsetIntervalMs, enableSoundSimilarity);

        return ResponseEntity.ok(pattern);
    }

    @PostMapping("/score-audio-file")
    @Operation(summary = "Score uploaded audio file",
            description = "Upload user's recorded audio and score against reference pattern")
    public ResponseEntity<RhythmScoringResultDTO> scoreAudioFile(
            @RequestParam("audioFile") MultipartFile audioFile,
            @RequestParam("questionId") Long questionId,
            @RequestParam(value = "enableSoundSimilarity", required = false, defaultValue = "false") Boolean enableSoundSimilarity,
            @RequestParam(value = "toleranceMs", required = false, defaultValue = "150") Double toleranceMs) {

        log.info("🎵 Scoring audio file for question {}", questionId);

        RhythmScoringResultDTO result = rhythmPatternService.scoreAudioFile(
                audioFile, questionId, enableSoundSimilarity, toleranceMs);

        return ResponseEntity.ok(result);
    }
    
    @Data
    public static class RhythmScoreRequest {
        private RhythmPatternDTO referencePattern;
        private List<Double> userOnsetTimesMs;
        private Double toleranceMs;
        private Integer minimumScoreRequired;
    }

    @Data
    public static class RhythmScoreWithAudioRequest {
        private RhythmPatternDTO referencePattern;
        private List<Double> userOnsetTimesMs;
        private String userAudioPath;
        private Boolean enableSoundSimilarity;
        private Double toleranceMs;
        private Double timingWeight;
        private Double soundWeight;
        private Integer minimumScoreRequired;
    }
}
