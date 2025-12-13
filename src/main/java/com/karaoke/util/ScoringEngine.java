package com.karaoke.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karaoke.model.NoteEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ScoringEngine {
    
    private final ObjectMapper objectMapper;
    
    public ScoringEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Calculate pitch accuracy score using semitone deviations (0-100)
     * Industry standard: measures how closely singer matches melody notes
     */
    public double calculatePitchScoreSemitones(List<NoteEvent> userNotes, List<NoteEvent> referenceNotes) {
        if (userNotes.isEmpty() || referenceNotes.isEmpty()) {
            return 0.0;
        }
        
        // Align sequences using simple time-based matching
        List<Double> semitoneDeviations = new ArrayList<>();
        int perfectNotes = 0;
        double toleranceSemitones = 0.5; // ±0.5 semitones = within tolerance
        
        int minLength = Math.min(userNotes.size(), referenceNotes.size());
        
        for (int i = 0; i < minLength; i++) {
            NoteEvent userNote = userNotes.get(i);
            NoteEvent refNote = referenceNotes.get(i);
            
            double semitonesDiff = Math.abs(userNote.semitonesDifferenceFrom(refNote));
            semitoneDeviations.add(semitonesDiff);
            
            if (semitonesDiff <= toleranceSemitones) {
                perfectNotes++;
            }
        }
        
        double avgDeviation = semitoneDeviations.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Score formula: 100 - (average semitone deviation * 20)
        // 1 semitone off = -20 points, 2 semitones = -40 points, etc.
        double rawScore = 100 - (avgDeviation * 20);
        
        // Boost for accuracy: add bonus for percentage of perfect notes
        double accuracyBonus = ((double) perfectNotes / minLength) * 20;
        
        double finalScore = rawScore + accuracyBonus;
        
        return Math.min(100.0, Math.max(0.0, finalScore));
    }

    /**
     * Calculate rhythm/timing score based on onset times (0-100)
     * Measures if notes are hit on beat
     */
    public double calculateRhythmScoreOnsets(List<NoteEvent> userNotes, List<NoteEvent> referenceNotes) {
        if (userNotes.isEmpty() || referenceNotes.isEmpty()) {
            return 0.0;
        }
        
        List<Double> timingOffsets = new ArrayList<>();
        int onTimeNotes = 0;
        int earlyNotes = 0;
        int lateNotes = 0;
        double toleranceMs = 100.0; // ±100ms tolerance
        
        int minLength = Math.min(userNotes.size(), referenceNotes.size());
        
        for (int i = 0; i < minLength; i++) {
            NoteEvent userNote = userNotes.get(i);
            NoteEvent refNote = referenceNotes.get(i);
            
            double offsetMs = userNote.timingOffsetMs(refNote);
            timingOffsets.add(Math.abs(offsetMs));
            
            if (Math.abs(offsetMs) <= toleranceMs) {
                onTimeNotes++;
            } else if (offsetMs < 0) {
                earlyNotes++;
            } else {
                lateNotes++;
            }
        }
        
        double avgOffsetMs = timingOffsets.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Score formula: 100 - (average offset / 10)
        // 100ms off = -10 points, 500ms = -50 points
        double timingScore = 100 - (avgOffsetMs / 10);
        
        // Bonus for on-time percentage
        double onTimeBonus = ((double) onTimeNotes / minLength) * 30;
        
        double finalScore = (timingScore * 0.7) + onTimeBonus;
        
        return Math.min(100.0, Math.max(0.0, finalScore));
    }

    /**
     * Calculate voice similarity using MFCC comparison (0-100)
     * Measures how similar the timbre/voice quality is to reference
     */
    public double calculateVoiceSimilarityMFCC(List<double[]> userMFCCs, List<double[]> refMFCCs) {
        if (userMFCCs.isEmpty() || refMFCCs.isEmpty()) {
            return 0.0;
        }
        
        List<Double> similarities = new ArrayList<>();
        int minLength = Math.min(userMFCCs.size(), refMFCCs.size());
        
        for (int i = 0; i < minLength; i++) {
            double[] userVec = userMFCCs.get(i);
            double[] refVec = refMFCCs.get(i);
            
            // Calculate cosine similarity between MFCC vectors
            double similarity = cosineSimilarity(userVec, refVec);
            similarities.add(similarity);
        }
        
        double avgSimilarity = similarities.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Convert similarity [-1, 1] to score [0, 100]
        // Cosine similarity: 1 = identical, 0 = orthogonal, -1 = opposite
        double score = ((avgSimilarity + 1) / 2) * 100;
        
        return Math.min(100.0, Math.max(0.0, score));
    }

    /**
     * Helper: Calculate cosine similarity between two vectors
     */
    private double cosineSimilarity(double[] vecA, double[] vecB) {
        if (vecA.length != vecB.length) {
            return 0.0;
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vecA.length; i++) {
            dotProduct += vecA[i] * vecB[i];
            normA += vecA[i] * vecA[i];
            normB += vecB[i] * vecB[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Generate enhanced detailed metrics with all scoring components
     */
    public String generateEnhancedMetrics(
        List<NoteEvent> userNotes,
        List<NoteEvent> refNotes,
        List<double[]> userMFCCs,
        List<double[]> refMFCCs,
        double pitchScore,
        double rhythmScore,
        double voiceScore
    ) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            
            // Pitch accuracy metrics
            ObjectNode pitchMetrics = objectMapper.createObjectNode();
            
            int minLength = Math.min(userNotes.size(), refNotes.size());
            int perfectNotes = 0;
            double totalSemitones = 0.0;
            double maxDeviation = 0.0;
            
            for (int i = 0; i < minLength; i++) {
                double semitones = Math.abs(userNotes.get(i).semitonesDifferenceFrom(refNotes.get(i)));
                totalSemitones += semitones;
                maxDeviation = Math.max(maxDeviation, semitones);
                if (semitones <= 0.5) perfectNotes++;
            }
            
            pitchMetrics.put("averageSemitoneDeviation", Math.round(totalSemitones / minLength * 100.0) / 100.0);
            pitchMetrics.put("notesHitCorrectly", perfectNotes);
            pitchMetrics.put("totalNotes", minLength);
            pitchMetrics.put("accuracyPercentage", Math.round((double) perfectNotes / minLength * 10000.0) / 100.0);
            pitchMetrics.put("maxDeviation", Math.round(maxDeviation * 100.0) / 100.0);
            pitchMetrics.put("perfectNotesCount", perfectNotes);
            
            root.set("pitchAccuracy", pitchMetrics);
            
            // Rhythm timing metrics
            ObjectNode rhythmMetrics = objectMapper.createObjectNode();
            
            int onTimeNotes = 0;
            int earlyNotes = 0;
            int lateNotes = 0;
            double totalOffset = 0.0;
            double maxOffset = 0.0;
            
            for (int i = 0; i < minLength; i++) {
                double offset = userNotes.get(i).timingOffsetMs(refNotes.get(i));
                double absOffset = Math.abs(offset);
                totalOffset += absOffset;
                maxOffset = Math.max(maxOffset, absOffset);
                
                if (absOffset <= 100) onTimeNotes++;
                else if (offset < 0) earlyNotes++;
                else lateNotes++;
            }
            
            rhythmMetrics.put("averageTimingOffsetMs", Math.round(totalOffset / minLength * 100.0) / 100.0);
            rhythmMetrics.put("onTimeNotesCount", onTimeNotes);
            rhythmMetrics.put("earlyNotesCount", earlyNotes);
            rhythmMetrics.put("lateNotesCount", lateNotes);
            rhythmMetrics.put("maxTimingErrorMs", Math.round(maxOffset * 100.0) / 100.0);
            
            root.set("rhythmTiming", rhythmMetrics);
            
            // Voice similarity metrics
            ObjectNode voiceMetrics = objectMapper.createObjectNode();
            
            int mfccLength = Math.min(userMFCCs.size(), refMFCCs.size());
            double totalSimilarity = 0.0;
            
            for (int i = 0; i < mfccLength; i++) {
                totalSimilarity += cosineSimilarity(userMFCCs.get(i), refMFCCs.get(i));
            }
            
            double avgSimilarity = mfccLength > 0 ? totalSimilarity / mfccLength : 0.0;
            double spectralDistance = 1.0 - ((avgSimilarity + 1) / 2);
            
            voiceMetrics.put("mfccSimilarityScore", Math.round(voiceScore * 100.0) / 100.0);
            voiceMetrics.put("spectralDistance", Math.round(spectralDistance * 1000.0) / 1000.0);
            voiceMetrics.put("timbreMatchPercentage", Math.round(voiceScore * 100.0) / 100.0);
            
            root.set("voiceSimilarity", voiceMetrics);
            
            // Overall score
            double overallScore = (pitchScore * 0.5) + (rhythmScore * 0.3) + (voiceScore * 0.2);
            root.put("overallScore", Math.round(overallScore * 100.0) / 100.0);
            
            return objectMapper.writeValueAsString(root);
            
        } catch (Exception e) {
            log.error("Error generating enhanced metrics", e);
            return "{}";
        }
    }
}