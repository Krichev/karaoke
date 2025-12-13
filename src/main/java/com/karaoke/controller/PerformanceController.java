package com.karaoke.controller;

import com.karaoke.dto.PerformanceResponseDTO;
import com.karaoke.dto.PerformanceScoreDTO;
import com.karaoke.dto.PerformanceStatusDTO;
import com.karaoke.service.PerformanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    /**
     * POST /api/songs/{songId}/performances - Upload Performance Recording
     */
    @PostMapping("/songs/{songId}/performances")
    public ResponseEntity<PerformanceResponseDTO> uploadPerformance(
            @PathVariable String songId,
            @RequestParam("userId") String userId,
            @RequestParam("audioFile") MultipartFile audioFile) {
        
        PerformanceResponseDTO response = performanceService.uploadPerformance(songId, userId, audioFile);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * GET /api/performances/{performanceId}/status - Check Processing Status
     */
    @GetMapping("/performances/{performanceId}/status")
    public ResponseEntity<PerformanceStatusDTO> getPerformanceStatus(@PathVariable String performanceId) {
        PerformanceStatusDTO status = performanceService.getPerformanceStatus(performanceId);
        return ResponseEntity.ok(status);
    }

    /**
     * GET /api/performances/{performanceId}/scores - Get Performance Scores
     */
    @GetMapping("/performances/{performanceId}/scores")
    public ResponseEntity<PerformanceScoreDTO> getPerformanceScores(@PathVariable String performanceId) {
        PerformanceScoreDTO scores = performanceService.getPerformanceScores(performanceId);
        return ResponseEntity.ok(scores);
    }
}
