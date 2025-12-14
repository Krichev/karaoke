package com.karaoke.controller;

import com.karaoke.dto.PerformanceResponseDTO;
import com.karaoke.dto.PerformanceScoreDTO;
import com.karaoke.dto.PerformanceStatusDTO;
import com.karaoke.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "Performances", description = "Performance recording and scoring API")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @Operation(
        summary = "Upload performance recording",
        description = "Upload a user's karaoke performance recording for a specific song. The recording will be processed asynchronously and scored against the reference track using pitch accuracy, rhythm timing, and voice similarity algorithms."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "202", description = "Performance accepted and queued for processing"),
        @ApiResponse(responseCode = "400", description = "Invalid audio file or song not ready", content = @Content),
        @ApiResponse(responseCode = "404", description = "Song not found", content = @Content)
    })
    @PostMapping("/songs/{songId}/performances")
    public ResponseEntity<PerformanceResponseDTO> uploadPerformance(
            @Parameter(description = "Song ID to perform", example = "abc123-def456")
            @PathVariable String songId,
            @Parameter(description = "User identifier", example = "user_12345")
            @RequestParam("userId") String userId,
            @Parameter(description = "Audio recording file (MP3, WAV, AIFF, AU)")
            @RequestParam("audioFile") MultipartFile audioFile) {
        
        PerformanceResponseDTO response = performanceService.uploadPerformance(songId, userId, audioFile);
        return ResponseEntity.accepted().body(response);
    }

    @Operation(
        summary = "Check processing status",
        description = "Get the current processing status and progress of a performance recording"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Performance not found", content = @Content)
    })
    @GetMapping("/performances/{performanceId}/status")
    public ResponseEntity<PerformanceStatusDTO> getPerformanceStatus(
            @Parameter(description = "Performance ID", example = "perf_xyz789")
            @PathVariable String performanceId) {
        PerformanceStatusDTO status = performanceService.getPerformanceStatus(performanceId);
        return ResponseEntity.ok(status);
    }

    @Operation(
        summary = "Get performance scores",
        description = "Retrieve detailed scoring results including overall score, pitch accuracy (semitone deviations), rhythm timing (onset offsets), voice similarity (MFCC analysis), and comprehensive metrics breakdown"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Scores retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Performance not found", content = @Content),
        @ApiResponse(responseCode = "400", description = "Performance processing not yet completed", content = @Content)
    })
    @GetMapping("/performances/{performanceId}/scores")
    public ResponseEntity<PerformanceScoreDTO> getPerformanceScores(
            @Parameter(description = "Performance ID", example = "perf_xyz789")
            @PathVariable String performanceId) {
        PerformanceScoreDTO scores = performanceService.getPerformanceScores(performanceId);
        return ResponseEntity.ok(scores);
    }
}
