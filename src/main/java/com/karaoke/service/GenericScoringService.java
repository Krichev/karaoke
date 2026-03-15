package com.karaoke.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karaoke.dto.GenericScoringRequest;
import com.karaoke.dto.GenericScoringResponse;
import com.karaoke.dto.RhythmPatternDTO;
import com.karaoke.dto.RhythmScoringResultDTO;
import com.karaoke.model.NoteEvent;
import com.karaoke.exception.AudioDownloadException;
import com.karaoke.util.AudioDownloader;
import com.karaoke.util.AudioProcessor;
import com.karaoke.util.RhythmAnalyzer;
import com.karaoke.util.ScoringEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenericScoringService {

    private final AudioProcessor audioProcessor;
    private final ScoringEngine scoringEngine;
    private final RhythmAnalyzer rhythmAnalyzer;
    private final AudioDownloader audioDownloader;
    private final ToleranceTierCalculator toleranceTierCalculator;

    public GenericScoringResponse scoreAudio(GenericScoringRequest request) {
        log.info("🎵 Processing audio: type={}", request.getChallengeType());

        try {
            String challengeType = request.getChallengeType();

            switch (challengeType) {
                case "RHYTHM_CREATION":
                    return scoreRhythmCreation(request);
                case "RHYTHM_REPEAT":
                    return scoreRhythmRepeat(request);
                case "SOUND_MATCH":
                    return scoreSoundMatch(request);
                case "SINGING":
                    return scoreSinging(request);
                default:
                    return scoreSinging(request); // Default to full scoring
            }
        } catch (AudioDownloadException e) {
            log.error("❌ Audio download failed: {}", e.getMessage());
            return GenericScoringResponse.builder()
                    .pitchScore(0.0).rhythmScore(0.0).voiceScore(0.0).overallScore(0.0)
                    .detailedMetrics("{\"error\": \"download_failed\", \"detail\": \"" + e.getMessage() + "\"}")
                    .build();
        } catch (Exception e) {
            log.error("❌ Scoring error: {}", e.getMessage(), e);
            return GenericScoringResponse.builder()
                    .pitchScore(0.0)
                    .rhythmScore(0.0)
                    .voiceScore(0.0)
                    .overallScore(0.0)
                    .detailedMetrics("{\"error\": \"processing_failed\", \"detail\": \"" + e.getMessage() + "\"}")
                    .build();
        }
    }

    public GenericScoringResponse scoreRhythmOnly(GenericScoringRequest request) {
        return scoreRhythmRepeat(request);
    }

    private byte[] resolveUserAudio(GenericScoringRequest request) {
        return audioDownloader.resolveAudio(
                request.getUserAudioUrl(),
                request.getUserAudioPath(),
                "user audio"
        );
    }

    private byte[] resolveReferenceAudio(GenericScoringRequest request) {
        return audioDownloader.resolveAudio(
                request.getReferenceAudioUrl(),
                request.getReferenceAudioPath(),
                "reference audio"
        );
    }

    private GenericScoringResponse scoreRhythmCreation(GenericScoringRequest request) {
        log.info("🥁 Scoring rhythm creation");

        byte[] userAudioBytes = resolveUserAudio(request);

        // Extract rhythm events from user audio
        List<Double> userOnsets = rhythmAnalyzer.extractOnsets(userAudioBytes);

        // Analyze rhythm consistency and creativity
        double consistencyScore = rhythmAnalyzer.analyzeConsistency(
                userOnsets, request.getRhythmBpm(), request.getTimeSignature());
        double creativityScore = rhythmAnalyzer.analyzeCreativity(userOnsets);

        // Rhythm creation: 70% consistency, 30% creativity
        double rhythmScore = (consistencyScore * 0.7) + (creativityScore * 0.3);

        String metrics = rhythmAnalyzer.generateRhythmMetrics(
                userOnsets, consistencyScore, creativityScore);

        return GenericScoringResponse.builder()
                .pitchScore(0.0)
                .rhythmScore(rhythmScore)
                .voiceScore(0.0)
                .overallScore(rhythmScore)
                .detailedMetrics(metrics)
                .build();
    }

    private GenericScoringResponse scoreRhythmRepeat(GenericScoringRequest request) {
        log.info("🎯 Scoring rhythm repeat with reaction-time tiered model");

        try {
            byte[] userAudioBytes = resolveUserAudio(request);
            byte[] refAudioBytes = resolveReferenceAudio(request);

            // Extract patterns from both audio files
            RhythmPatternDTO refPattern = rhythmAnalyzer.extractRhythmPattern(
                    refAudioBytes, -40.0, 100.0);
            RhythmPatternDTO userPattern = rhythmAnalyzer.extractRhythmPattern(
                    userAudioBytes, -40.0, 100.0);

            // Compute tolerance tiers
            com.karaoke.dto.ToleranceTiers tiers = toleranceTierCalculator.compute(
                    request.getDifficulty(), request.getToleranceStrictness());

            // Score using tiered model
            RhythmScoringResultDTO scoringResult = rhythmAnalyzer.scoreRhythmPatternTiered(
                    refPattern,
                    userPattern.getOnsetTimesMs(),
                    tiers,
                    null);

            // Build detailed metrics JSON
            String metrics = buildRhythmMetrics(refPattern, userPattern, scoringResult);

            return GenericScoringResponse.builder()
                    .pitchScore(0.0)
                    .rhythmScore(scoringResult.getOverallScore())
                    .voiceScore(0.0)
                    .overallScore(scoringResult.getOverallScore())
                    .detailedMetrics(metrics)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error in rhythm scoring: {}", e.getMessage(), e);
            throw e; // Rethrow to be caught by main scoreAudio try-catch
        }
    }

    private String buildRhythmMetrics(RhythmPatternDTO ref, RhythmPatternDTO user, RhythmScoringResultDTO result) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode root = mapper.createObjectNode();

            // Reference pattern info
            ObjectNode refInfo = root.putObject("referencePattern");
            refInfo.put("totalBeats", ref.getTotalBeats());
            refInfo.put("estimatedBpm", ref.getEstimatedBpm());
            refInfo.put("timeSignature", ref.getTimeSignature());

            // User pattern info
            ObjectNode userInfo = root.putObject("userPattern");
            userInfo.put("totalBeats", user.getTotalBeats());
            userInfo.put("estimatedBpm", user.getEstimatedBpm());

            // Scoring details
            ObjectNode scoring = root.putObject("scoring");
            scoring.put("overallScore", result.getOverallScore());
            scoring.put("perfectBeats", result.getPerfectBeats());
            scoring.put("goodBeats", result.getGoodBeats());
            scoring.put("okBeats", result.getOkBeats());
            scoring.put("missedBeats", result.getMissedBeats());
            scoring.put("scoringModel", result.getScoringModel());
            scoring.put("averageErrorMs", result.getAverageErrorMs());
            scoring.put("maxErrorMs", result.getMaxErrorMs());
            scoring.put("consistencyScore", result.getConsistencyScore());
            scoring.put("feedback", result.getFeedback());

            if (result.getToleranceTiers() != null) {
                ObjectNode tiers = scoring.putObject("toleranceTiers");
                tiers.put("perfectMs", result.getToleranceTiers().getPerfectThresholdMs());
                tiers.put("goodMs", result.getToleranceTiers().getGoodThresholdMs());
                tiers.put("okMs", result.getToleranceTiers().getOkThresholdMs());
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private GenericScoringResponse scoreSoundMatch(GenericScoringRequest request) {
        log.info("🔊 Scoring sound match");

        byte[] userAudioBytes = resolveUserAudio(request);
        byte[] refAudioBytes = resolveReferenceAudio(request);

        // Extract note events for pitch comparison
        List<NoteEvent> userNotes = audioProcessor.extractNoteEvents(userAudioBytes);
        List<NoteEvent> refNotes = audioProcessor.extractNoteEvents(refAudioBytes);

        // Extract MFCCs for timbre comparison
        List<double[]> userMFCCs = audioProcessor.extractMFCCs(userAudioBytes);
        List<double[]> refMFCCs = audioProcessor.extractMFCCs(refAudioBytes);

        // Calculate scores
        double pitchScore = scoringEngine.calculatePitchScoreSemitones(userNotes, refNotes);
        double voiceScore = scoringEngine.calculateVoiceSimilarityMFCC(userMFCCs, refMFCCs);

        // Sound match: 50% pitch, 40% timbre, 10% rhythm
        double rhythmScore = scoringEngine.calculateRhythmScoreOnsets(userNotes, refNotes);
        double overallScore = (pitchScore * 0.5) + (voiceScore * 0.4) + (rhythmScore * 0.1);

        String metrics = scoringEngine.generateEnhancedMetrics(
                userNotes, refNotes, userMFCCs, refMFCCs,
                pitchScore, rhythmScore, voiceScore);

        return GenericScoringResponse.builder()
                .pitchScore(pitchScore)
                .rhythmScore(rhythmScore)
                .voiceScore(voiceScore)
                .overallScore(overallScore)
                .detailedMetrics(metrics)
                .build();
    }

    private GenericScoringResponse scoreSinging(GenericScoringRequest request) {
        log.info("🎤 Scoring singing performance");

        byte[] userAudioBytes = resolveUserAudio(request);
        byte[] refAudioBytes = resolveReferenceAudio(request);

        // Full karaoke scoring
        List<NoteEvent> userNotes = audioProcessor.extractNoteEvents(userAudioBytes);
        List<NoteEvent> refNotes = audioProcessor.extractNoteEvents(refAudioBytes);
        List<double[]> userMFCCs = audioProcessor.extractMFCCs(userAudioBytes);
        List<double[]> refMFCCs = audioProcessor.extractMFCCs(refAudioBytes);

        double pitchScore = scoringEngine.calculatePitchScoreSemitones(userNotes, refNotes);
        double rhythmScore = scoringEngine.calculateRhythmScoreOnsets(userNotes, refNotes);
        double voiceScore = scoringEngine.calculateVoiceSimilarityMFCC(userMFCCs, refMFCCs);

        // Singing: 50% pitch, 30% rhythm, 20% voice
        double overallScore = (pitchScore * 0.5) + (rhythmScore * 0.3) + (voiceScore * 0.2);

        String metrics = scoringEngine.generateEnhancedMetrics(
                userNotes, refNotes, userMFCCs, refMFCCs,
                pitchScore, rhythmScore, voiceScore);

        return GenericScoringResponse.builder()
                .pitchScore(pitchScore)
                .rhythmScore(rhythmScore)
                .voiceScore(voiceScore)
                .overallScore(overallScore)
                .detailedMetrics(metrics)
                .build();
    }
}
