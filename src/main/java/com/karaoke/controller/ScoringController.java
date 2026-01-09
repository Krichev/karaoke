package com.karaoke.controller;

import com.karaoke.dto.GenericScoringRequest;
import com.karaoke.dto.GenericScoringResponse;
import com.karaoke.service.GenericScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scoring")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Generic Scoring", description = "Audio scoring for external services")
public class ScoringController {

    private final GenericScoringService scoringService;

    @PostMapping("/analyze")
    @Operation(summary = "Analyze and score audio",
            description = "Score user audio against optional reference based on challenge type")
    public ResponseEntity<GenericScoringResponse> analyzeAudio(
            @RequestBody GenericScoringRequest request) {

        log.info("🎵 Scoring request: type={}", request.getChallengeType());

        GenericScoringResponse response = scoringService.scoreAudio(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/rhythm-only")
    @Operation(summary = "Analyze rhythm pattern",
            description = "Score rhythm accuracy without pitch or voice analysis")
    public ResponseEntity<GenericScoringResponse> analyzeRhythm(
            @RequestBody GenericScoringRequest request) {

        log.info("🥁 Rhythm-only scoring request");

        GenericScoringResponse response = scoringService.scoreRhythmOnly(request);

        return ResponseEntity.ok(response);
    }
}
