package com.karaoke.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ScoringEngine {
    
    private final ObjectMapper objectMapper;
    
    public ScoringEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Calculate pitch accuracy score (0-100)
     * Compares user's pitch contour with reference track
     */
    public double calculatePitchScore(List<Double> userPitches, List<Double> referencePitches) {
        if (userPitches.isEmpty() || referencePitches.isEmpty()) {
            return 0.0;
        }
        
        // Align sequences (take minimum length)
        int minLength = Math.min(userPitches.size(), referencePitches.size());
        
        double totalDeviation = 0.0;
        int correctNotes = 0;
        double threshold = 50.0; // Hz tolerance for correct note
        
        for (int i = 0; i < minLength; i++) {
            double userPitch = userPitches.get(i);
            double refPitch = referencePitches.get(i);
            
            double deviation = Math.abs(userPitch - refPitch);
            totalDeviation += deviation;
            
            // Count notes within acceptable threshold
            if (deviation <= threshold) {
                correctNotes++;
            }
        }
        
        double averageDeviation = totalDeviation / minLength;
        double accuracy = (double) correctNotes / minLength;
        
        // Score formula: 70% accuracy + 30% deviation penalty
        // Lower deviation = better score
        double deviationScore = Math.max(0, 30 - (averageDeviation / 10));
        double accuracyScore = accuracy * 70;
        double score = accuracyScore + deviationScore;
        
        return Math.min(100.0, Math.max(0.0, score));
    }
    
    /**
     * Calculate rhythm/timing score (0-100)
     * Simplified for MVP - measures tempo consistency
     */
    public double calculateRhythmScore(List<Double> userPitches, List<Double> referencePitches) {
        if (userPitches.isEmpty() || referencePitches.isEmpty()) {
            return 0.0;
        }
        
        // Calculate tempo consistency based on pitch sequence length alignment
        int userLength = userPitches.size();
        int refLength = referencePitches.size();
        
        double lengthRatio = (double) Math.min(userLength, refLength) 
                           / Math.max(userLength, refLength);
        
        // Penalize timing offset
        double timingScore = lengthRatio * 100;
        
        // Additional check: consistency of pitch changes (simplified rhythm)
        double consistencyScore = calculatePitchChangeConsistency(userPitches, referencePitches);
        
        // Weighted combination
        double score = (timingScore * 0.6) + (consistencyScore * 0.4);
        
        return Math.min(100.0, Math.max(0.0, score));
    }
    
    /**
     * Calculate voice quality score (0-100)
     * Measures pitch stability and vocal control
     */
    public double calculateVoiceQualityScore(List<Double> userPitches) {
        if (userPitches.isEmpty()) {
            return 0.0;
        }
        
        // Calculate pitch stability (lower variance = better quality)
        double mean = userPitches.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        double variance = userPitches.stream()
            .mapToDouble(p -> Math.pow(p - mean, 2))
            .average()
            .orElse(0.0);
        
        double stdDev = Math.sqrt(variance);
        
        // Score based on stability (lower std dev = higher score)
        // Typical good vocal has stdDev around 50-100 Hz
        double stabilityScore = Math.max(0, 100 - (stdDev / 10));
        
        // Check for pitch jumps (smoothness)
        double smoothnessScore = calculateSmoothness(userPitches);
        
        // Weighted combination
        double score = (stabilityScore * 0.6) + (smoothnessScore * 0.4);
        
        return Math.min(100.0, Math.max(0.0, score));
    }
    
    /**
     * Calculate overall score with weighted components
     */
    public double calculateOverallScore(double pitchScore, double rhythmScore, double voiceQualityScore) {
        // Weights: Pitch 50%, Rhythm 30%, Voice Quality 20%
        return (pitchScore * 0.5) + (rhythmScore * 0.3) + (voiceQualityScore * 0.2);
    }
    
    /**
     * Generate detailed metrics as JSON string
     */
    public String generateDetailedMetrics(
        List<Double> userPitches, 
        List<Double> referencePitches,
        double pitchScore,
        double rhythmScore
    ) {
        try {
            ObjectNode metrics = objectMapper.createObjectNode();
            
            int minLength = Math.min(userPitches.size(), referencePitches.size());
            int correctNotes = 0;
            double totalDeviation = 0.0;
            double threshold = 50.0;
            
            for (int i = 0; i < minLength; i++) {
                double deviation = Math.abs(userPitches.get(i) - referencePitches.get(i));
                totalDeviation += deviation;
                if (deviation <= threshold) {
                    correctNotes++;
                }
            }
            
            double avgDeviation = minLength > 0 ? totalDeviation / minLength : 0.0;
            
            metrics.put("averagePitchDeviation", Math.round(avgDeviation * 100.0) / 100.0);
            metrics.put("pitchAccuracyPercentage", Math.round(pitchScore * 100.0) / 100.0);
            metrics.put("notesHitCorrectly", correctNotes);
            metrics.put("totalNotes", minLength);
            metrics.put("timingOffsetMs", Math.abs(userPitches.size() - referencePitches.size()) * 50);
            metrics.put("userPitchCount", userPitches.size());
            metrics.put("referencePitchCount", referencePitches.size());
            
            return objectMapper.writeValueAsString(metrics);
            
        } catch (Exception e) {
            log.error("Error generating detailed metrics", e);
            return "{}";
        }
    }
    
    // Helper methods
    
    private double calculatePitchChangeConsistency(List<Double> userPitches, List<Double> referencePitches) {
        if (userPitches.size() < 2 || referencePitches.size() < 2) {
            return 50.0; // Default middle score
        }
        
        int matches = 0;
        int comparisons = Math.min(userPitches.size() - 1, referencePitches.size() - 1);
        
        for (int i = 0; i < comparisons; i++) {
            double userChange = userPitches.get(i + 1) - userPitches.get(i);
            double refChange = referencePitches.get(i + 1) - referencePitches.get(i);
            
            // Check if changes are in same direction
            if ((userChange > 0 && refChange > 0) || (userChange < 0 && refChange < 0) || 
                (Math.abs(userChange) < 10 && Math.abs(refChange) < 10)) {
                matches++;
            }
        }
        
        return comparisons > 0 ? (double) matches / comparisons * 100 : 50.0;
    }
    
    private double calculateSmoothness(List<Double> pitches) {
        if (pitches.size() < 2) {
            return 100.0;
        }
        
        double totalJump = 0.0;
        for (int i = 0; i < pitches.size() - 1; i++) {
            double jump = Math.abs(pitches.get(i + 1) - pitches.get(i));
            totalJump += jump;
        }
        
        double avgJump = totalJump / (pitches.size() - 1);
        
        // Smooth vocals have small jumps (< 50 Hz average)
        // Large jumps indicate rough or unstable vocals
        double smoothnessScore = Math.max(0, 100 - (avgJump / 5));
        
        return Math.min(100.0, smoothnessScore);
    }
}
