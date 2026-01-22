package com.karaoke.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karaoke.dto.RhythmPatternDTO;
import com.karaoke.dto.RhythmScoringResultDTO;
import com.karaoke.dto.SoundComparisonDetail;
import com.karaoke.dto.SoundFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RhythmAnalyzer {

    private final ObjectMapper objectMapper;
    private final SoundAnalyzer soundAnalyzer;

    /**
     * Extract a complete rhythm pattern from audio file with silence trimming
     * This is used when creating a rhythm question
     */
    public RhythmPatternDTO extractRhythmPattern(String audioPath, Double silenceThresholdDb, Double minOnsetIntervalMs) {
        double threshold = silenceThresholdDb != null ? silenceThresholdDb : -40.0;
        double minInterval = minOnsetIntervalMs != null ? minOnsetIntervalMs : 100.0;

        log.info("🎵 Extracting rhythm pattern from: {}", audioPath);

        try {
            // 1. Get original duration
            double originalDurationMs = getAudioDurationMs(audioPath);

            // 2. Extract raw onset times
            List<Double> rawOnsets = extractOnsetsWithSilenceDetection(audioPath, threshold, minInterval);

            if (rawOnsets.size() < 2) {
                log.warn("⚠️ Insufficient onsets detected: {}", rawOnsets.size());
                return RhythmPatternDTO.builder()
                        .version(1)
                        .onsetTimesMs(rawOnsets)
                        .intervalsMs(List.of())
                        .totalBeats(rawOnsets.size())
                        .originalDurationMs(originalDurationMs)
                        .silenceThresholdDb(threshold)
                        .minOnsetIntervalMs(minInterval)
                        .build();
            }

            // 3. Normalize - first onset becomes 0
            double firstOnset = rawOnsets.get(0);
            double lastOnset = rawOnsets.get(rawOnsets.size() - 1);

            List<Double> normalizedOnsets = rawOnsets.stream()
                    .map(t -> t - firstOnset)
                    .collect(Collectors.toList());

            // 4. Calculate intervals
            List<Double> intervals = new ArrayList<>();
            for (int i = 1; i < normalizedOnsets.size(); i++) {
                intervals.add(normalizedOnsets.get(i) - normalizedOnsets.get(i - 1));
            }

            // 5. Estimate BPM from average interval
            double avgIntervalMs = intervals.stream().mapToDouble(d -> d).average().orElse(500);
            int estimatedBpm = (int) Math.round(60000.0 / avgIntervalMs);

            // 6. Detect time signature (simplified heuristic)
            String timeSignature = detectTimeSignature(intervals, estimatedBpm);

            log.info("✅ Pattern extracted: {} beats, ~{} BPM, {}",
                    rawOnsets.size(), estimatedBpm, timeSignature);

            return RhythmPatternDTO.builder()
                    .version(1)
                    .onsetTimesMs(normalizedOnsets)
                    .intervalsMs(intervals)
                    .estimatedBpm(estimatedBpm)
                    .timeSignature(timeSignature)
                    .totalBeats(rawOnsets.size())
                    .trimmedStartMs(firstOnset)
                    .trimmedEndMs(lastOnset)
                    .originalDurationMs(originalDurationMs)
                    .silenceThresholdDb(threshold)
                    .minOnsetIntervalMs(minInterval)
                    .build();

        } catch (Exception e) {
            log.error("❌ Error extracting rhythm pattern: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract rhythm pattern", e);
        }
    }

    /**
     * Extract rhythm pattern with sound fingerprints for similarity scoring
     */
    public RhythmPatternDTO extractRhythmPatternWithFingerprints(
            String audioPath,
            Double silenceThresholdDb,
            Double minOnsetIntervalMs,
            boolean enableSoundSimilarity) {

        // First extract basic pattern
        RhythmPatternDTO pattern = extractRhythmPattern(audioPath, silenceThresholdDb, minOnsetIntervalMs);

        // Add fingerprints if sound similarity is enabled
        if (enableSoundSimilarity && pattern.getTotalBeats() > 0) {
            // Convert normalized onsets back to absolute times for fingerprint extraction
            List<Double> absoluteOnsets = pattern.getOnsetTimesMs().stream()
                    .map(t -> t + pattern.getTrimmedStartMs())
                    .collect(Collectors.toList());

            List<SoundFingerprint> fingerprints = soundAnalyzer.extractFingerprints(audioPath, absoluteOnsets);
            pattern.setBeatFingerprints(fingerprints);
            pattern.setSoundSimilarityEnabled(true);
            pattern.setReferenceAudioPath(audioPath);
        }

        return pattern;
    }

    /**
     * Extract onsets with improved silence detection and debouncing
     */
    private List<Double> extractOnsetsWithSilenceDetection(String audioPath, double silenceThresholdDb, double minIntervalMs) {
        List<Double> onsets = new ArrayList<>();

        try {
            File audioFile = new File(audioPath);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();

            byte[] audioBytes = audioStream.readAllBytes();
            float sampleRate = format.getSampleRate();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int channels = format.getChannels();

            // Convert to samples
            double[] samples = new double[audioBytes.length / (bytesPerSample * channels)];
            for (int i = 0; i < samples.length; i++) {
                int offset = i * bytesPerSample * channels;
                if (bytesPerSample == 2) {
                    short value = (short) ((audioBytes[offset + 1] << 8) | (audioBytes[offset] & 0xFF));
                    samples[i] = value / 32768.0;
                } else {
                    samples[i] = (audioBytes[offset] - 128) / 128.0;
                }
            }

            // Calculate RMS energy in windows (20ms)
            int windowSize = (int) (sampleRate * 0.02);
            int hopSize = windowSize / 4; // 75% overlap for precision

            List<Double> energies = new ArrayList<>();
            List<Double> times = new ArrayList<>();

            for (int i = 0; i < samples.length - windowSize; i += hopSize) {
                double sum = 0;
                for (int j = 0; j < windowSize; j++) {
                    sum += samples[i + j] * samples[i + j];
                }
                double rms = Math.sqrt(sum / windowSize);
                double db = 20 * Math.log10(Math.max(rms, 1e-10));
                energies.add(db);
                times.add((i * 1000.0) / sampleRate); // ms
            }

            // Find dynamic threshold based on signal statistics
            double maxEnergy = energies.stream().mapToDouble(d -> d).max().orElse(0);
            double dynamicThreshold = Math.max(silenceThresholdDb, maxEnergy - 20); // 20dB below peak

            // Detect onsets (rising edges above threshold)
            boolean inSound = false;
            double lastOnsetTime = -minIntervalMs; // Initialize to allow first onset

            for (int i = 1; i < energies.size(); i++) {
                double prev = energies.get(i - 1);
                double curr = energies.get(i);
                double time = times.get(i);

                // Rising edge detection with debounce
                if (!inSound && curr > dynamicThreshold && curr > prev + 3) { // 3dB rise
                    if (time - lastOnsetTime >= minIntervalMs) {
                        onsets.add(time);
                        lastOnsetTime = time;
                    }
                    inSound = true;
                } else if (inSound && curr < dynamicThreshold - 6) { // 6dB hysteresis
                    inSound = false;
                }
            }

            log.info("🥁 Detected {} onsets with threshold {} dB", onsets.size(), dynamicThreshold);

        } catch (Exception e) {
            log.error("❌ Error in onset detection: {}", e.getMessage(), e);
        }

        return onsets;
    }

    /**
     * Score user rhythm against reference pattern with human-calibrated tolerance
     */
    public RhythmScoringResultDTO scoreRhythmPattern(
            RhythmPatternDTO referencePattern,
            List<Double> userOnsetTimesMs,
            Double customToleranceMs,
            Integer minimumScoreRequired) {

        log.info("🎯 Scoring rhythm: {} ref beats, {} user beats",
                referencePattern.getTotalBeats(), userOnsetTimesMs.size());

        List<Double> refOnsets = referencePattern.getOnsetTimesMs();
        int minBeats = Math.min(refOnsets.size(), userOnsetTimesMs.size());

        if (minBeats < 2) {
            return buildFailedResult("Insufficient beats to score", minimumScoreRequired);
        }

        // Normalize user onsets (first = 0)
        double userFirstOnset = userOnsetTimesMs.get(0);
        List<Double> normalizedUserOnsets = userOnsetTimesMs.stream()
                .map(t -> t - userFirstOnset)
                .collect(Collectors.toList());

        // Calculate tolerance based on reference pattern
        double avgInterval = referencePattern.getIntervalsMs().stream()
                .mapToDouble(d -> d)
                .average()
                .orElse(500);

        // Tolerance: min of custom, 150ms (avg reaction time), or interval/3
        double tolerance = customToleranceMs != null ? customToleranceMs :
                Math.min(150.0, avgInterval / 3.0);
        double maxTolerance = avgInterval / 2.0; // Beyond this = missed beat

        List<Double> perBeatScores = new ArrayList<>();
        List<Double> timingErrors = new ArrayList<>();
        List<Double> absoluteErrors = new ArrayList<>();
        int perfectBeats = 0;
        int goodBeats = 0;
        int missedBeats = 0;

        for (int i = 0; i < minBeats; i++) {
            double refTime = refOnsets.get(i);
            double userTime = normalizedUserOnsets.get(i);
            double error = userTime - refTime; // Negative = early, positive = late
            double absError = Math.abs(error);

            timingErrors.add(error);
            absoluteErrors.add(absError);

            // Calculate score using exponential decay
            // score = 100 * e^(-error/tolerance)
            double beatScore;
            if (absError >= maxTolerance) {
                beatScore = 0.0;
                missedBeats++;
            } else {
                beatScore = 100.0 * Math.exp(-absError / tolerance);
                if (absError < 50) {
                    perfectBeats++;
                } else if (absError < 150) {
                    goodBeats++;
                }
            }
            perBeatScores.add(Math.round(beatScore * 10.0) / 10.0);
        }

        // Penalize for beat count mismatch
        int extraBeats = Math.abs(refOnsets.size() - userOnsetTimesMs.size());
        double beatCountPenalty = extraBeats * 5.0; // -5 points per extra/missing beat

        // Calculate overall score
        double avgBeatScore = perBeatScores.stream().mapToDouble(d -> d).average().orElse(0);
        double overallScore = Math.max(0, avgBeatScore - beatCountPenalty);

        // Calculate consistency (variance in user intervals)
        double consistencyScore = calculateUserConsistency(normalizedUserOnsets, referencePattern.getIntervalsMs());

        // Generate feedback
        String feedback = generateFeedback(overallScore, perfectBeats, goodBeats, missedBeats, minBeats);

        boolean passed = minimumScoreRequired == null || overallScore >= minimumScoreRequired;

        return RhythmScoringResultDTO.builder()
                .overallScore(Math.round(overallScore * 10.0) / 10.0)
                .perBeatScores(perBeatScores)
                .timingErrorsMs(timingErrors)
                .absoluteErrorsMs(absoluteErrors)
                .perfectBeats(perfectBeats)
                .goodBeats(goodBeats)
                .missedBeats(missedBeats)
                .averageErrorMs(Math.round(absoluteErrors.stream().mapToDouble(d -> d).average().orElse(0) * 10.0) / 10.0)
                .maxErrorMs(absoluteErrors.stream().mapToDouble(d -> d).max().orElse(0))
                .consistencyScore(consistencyScore)
                .feedback(feedback)
                .passed(passed)
                .minimumScoreRequired(minimumScoreRequired)
                .build();
    }

    /**
     * Score rhythm with optional sound similarity evaluation
     */
    public RhythmScoringResultDTO scoreRhythmWithSoundSimilarity(
            RhythmPatternDTO referencePattern,
            List<Double> userOnsetTimesMs,
            String userAudioPath,
            boolean enableSoundSimilarity,
            Double customToleranceMs,
            Double customTimingWeight,
            Double customSoundWeight,
            Integer minimumScoreRequired) {

        log.info("🎵 Scoring rhythm: {} ref beats, {} user beats, soundSimilarity={}",
                referencePattern.getTotalBeats(), userOnsetTimesMs.size(), enableSoundSimilarity);

        // Get timing-only score first
        RhythmScoringResultDTO result = scoreRhythmPattern(
                referencePattern, userOnsetTimesMs, customToleranceMs, minimumScoreRequired);

        // Apply weights
        double timingWeight = customTimingWeight != null ? customTimingWeight :
                (referencePattern.getTimingWeight() != null ? referencePattern.getTimingWeight() : 0.7);
        double soundWeight = customSoundWeight != null ? customSoundWeight :
                (referencePattern.getSoundWeight() != null ? referencePattern.getSoundWeight() : 0.3);

        result.setTimingWeight(timingWeight);
        result.setSoundWeight(soundWeight);
        result.setSoundSimilarityEnabled(enableSoundSimilarity);

        // If sound similarity not enabled, just return timing score
        if (!enableSoundSimilarity || userAudioPath == null ||
                referencePattern.getBeatFingerprints() == null ||
                referencePattern.getBeatFingerprints().isEmpty()) {

            result.setCombinedScore(result.getOverallScore());
            result.setSoundSimilarityScore(null);
            return result;
        }

        // Extract user audio fingerprints
        List<Double> absoluteUserOnsets = userOnsetTimesMs; // Already normalized
        List<SoundFingerprint> userFingerprints = soundAnalyzer.extractFingerprints(userAudioPath, absoluteUserOnsets);

        // Compare fingerprints
        List<SoundComparisonDetail> soundDetails = new ArrayList<>();
        List<Double> perBeatSoundScores = new ArrayList<>();
        int goodSoundMatches = 0;
        double totalBrightnessDiff = 0;

        int minBeats = Math.min(
                Math.min(referencePattern.getBeatFingerprints().size(), userFingerprints.size()),
                Math.min(referencePattern.getOnsetTimesMs().size(), userOnsetTimesMs.size())
        );

        for (int i = 0; i < minBeats; i++) {
            SoundFingerprint refFp = referencePattern.getBeatFingerprints().get(i);
            SoundFingerprint userFp = userFingerprints.get(i);

            SoundComparisonDetail detail = soundAnalyzer.compareFingerprints(refFp, userFp, i);
            soundDetails.add(detail);
            perBeatSoundScores.add(detail.getOverallSoundScore());

            if (detail.getOverallSoundScore() >= 70) {
                goodSoundMatches++;
            }

            if (detail.getSpectralCentroidRef() != null && detail.getSpectralCentroidUser() != null) {
                totalBrightnessDiff += Math.abs(detail.getSpectralCentroidRef() - detail.getSpectralCentroidUser());
            }
        }

        // Handle missing beats
        for (int i = minBeats; i < referencePattern.getTotalBeats(); i++) {
            soundDetails.add(SoundComparisonDetail.createMissed(i));
            perBeatSoundScores.add(0.0);
        }

        // Calculate overall sound similarity score
        double soundSimilarityScore = perBeatSoundScores.stream()
                .mapToDouble(d -> d)
                .average()
                .orElse(0.0);

        // Calculate combined score
        double combinedScore = (result.getOverallScore() * timingWeight) + (soundSimilarityScore * soundWeight);

        // Update pass/fail based on combined score
        boolean passed = minimumScoreRequired == null || combinedScore >= minimumScoreRequired;

        // Generate sound feedback
        String soundFeedback = generateSoundFeedback(soundSimilarityScore, goodSoundMatches, minBeats);

        // Update result
        result.setSoundSimilarityScore(Math.round(soundSimilarityScore * 10) / 10.0);
        result.setPerBeatSoundScores(perBeatSoundScores);
        result.setSoundDetails(soundDetails);
        result.setGoodSoundMatches(goodSoundMatches);
        result.setAverageBrightnessDiff(minBeats > 0 ? totalBrightnessDiff / minBeats : 0);
        result.setSoundFeedback(soundFeedback);
        result.setCombinedScore(Math.round(combinedScore * 10) / 10.0);
        result.setPassed(passed);

        // Update overall feedback to include sound
        String combinedFeedback = result.getFeedback() + " " + soundFeedback;
        result.setFeedback(combinedFeedback);

        log.info("✅ Scoring complete: timing={}, sound={}, combined={}",
                result.getOverallScore(), soundSimilarityScore, combinedScore);

        return result;
    }

    private String generateSoundFeedback(double soundScore, int goodMatches, int totalBeats) {
        if (soundScore >= 85) {
            return "🔊 Excellent sound quality!";
        } else if (soundScore >= 70) {
            return "🔊 Good sound match.";
        } else if (soundScore >= 50) {
            return "🔊 Sound could be clearer.";
        } else {
            return "🔊 Try to match the reference sound more closely.";
        }
    }

    private double calculateUserConsistency(List<Double> userOnsets, List<Double> refIntervals) {
        if (userOnsets.size() < 2) return 0.0;

        List<Double> userIntervals = new ArrayList<>();
        for (int i = 1; i < userOnsets.size(); i++) {
            userIntervals.add(userOnsets.get(i) - userOnsets.get(i - 1));
        }

        // Compare user interval variance to reference
        double userVariance = calculateVariance(userIntervals);
        double refVariance = calculateVariance(refIntervals);

        // Score based on relative consistency (lower variance = more consistent)
        double avgInterval = userIntervals.stream().mapToDouble(d -> d).average().orElse(500);
        double normalizedVariance = Math.sqrt(userVariance) / avgInterval;

        return Math.max(0, Math.min(100, 100 * (1 - normalizedVariance * 2)));
    }

    private double calculateVariance(List<Double> values) {
        if (values.size() < 2) return 0;
        double mean = values.stream().mapToDouble(d -> d).average().orElse(0);
        return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
    }

    private String detectTimeSignature(List<Double> intervals, int bpm) {
        if (intervals.isEmpty()) return "4/4";

        // Group intervals to detect beats per measure
        // This is a simplified heuristic
        double avgInterval = intervals.stream().mapToDouble(d -> d).average().orElse(500);

        // Look for accent patterns (longer intervals often indicate measure boundaries)
        // For now, default to 4/4
        return "4/4";
    }

    private String generateFeedback(double score, int perfect, int good, int missed, int total) {
        if (score >= 90) return "🎉 Perfect rhythm! Outstanding timing!";
        if (score >= 75) return "👏 Great rhythm! Very good timing.";
        if (score >= 60) return "👍 Good effort! Keep practicing the timing.";
        if (score >= 40) return "💪 Getting there! Focus on listening to the beat.";
        return "🎯 Keep practicing! Try tapping along with the pattern first.";
    }

    private RhythmScoringResultDTO buildFailedResult(String reason, Integer minScore) {
        return RhythmScoringResultDTO.builder()
                .overallScore(0.0)
                .perBeatScores(List.of())
                .timingErrorsMs(List.of())
                .absoluteErrorsMs(List.of())
                .perfectBeats(0)
                .goodBeats(0)
                .missedBeats(0)
                .averageErrorMs(0.0)
                .maxErrorMs(0.0)
                .consistencyScore(0.0)
                .feedback("❌ " + reason)
                .passed(false)
                .minimumScoreRequired(minScore)
                .build();
    }

    private double getAudioDurationMs(String audioPath) {
        try {
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(audioPath));
            AudioFormat format = audioStream.getFormat();
            long frames = audioStream.getFrameLength();
            double seconds = frames / format.getFrameRate();
            audioStream.close();
            return seconds * 1000.0;
        } catch (Exception e) {
            log.error("Error getting audio duration: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Extract onset times (beat/hit times) from audio file
     * Uses amplitude threshold detection
     */
    public List<Double> extractOnsets(String audioPath) {
        List<Double> onsets = new ArrayList<>();

        try {
            File audioFile = new File(audioPath);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioStream.getFormat();

            byte[] audioBytes = audioStream.readAllBytes();
            float sampleRate = format.getSampleRate();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int channels = format.getChannels();

            // Convert to samples
            double[] samples = new double[audioBytes.length / (bytesPerSample * channels)];
            for (int i = 0; i < samples.length; i++) {
                int offset = i * bytesPerSample * channels;
                if (bytesPerSample == 2) {
                    short value = (short) ((audioBytes[offset + 1] << 8) | (audioBytes[offset] & 0xFF));
                    samples[i] = value / 32768.0;
                } else {
                    samples[i] = (audioBytes[offset] - 128) / 128.0;
                }
            }

            // Calculate RMS energy in windows
            int windowSize = (int) (sampleRate * 0.02); // 20ms windows
            int hopSize = windowSize / 2;
            List<Double> energies = new ArrayList<>();

            for (int i = 0; i < samples.length - windowSize; i += hopSize) {
                double sum = 0;
                for (int j = 0; j < windowSize; j++) {
                    sum += samples[i + j] * samples[i + j];
                }
                energies.add(Math.sqrt(sum / windowSize));
            }

            // Find onsets (local maxima above threshold)
            double threshold = energies.stream().mapToDouble(d -> d).average().orElse(0) * 1.5;
            boolean inOnset = false;

            for (int i = 1; i < energies.size() - 1; i++) {
                double curr = energies.get(i);
                if (curr > threshold && curr > energies.get(i - 1) && !inOnset) {
                    double timeSeconds = (i * hopSize) / sampleRate;
                    onsets.add(timeSeconds);
                    inOnset = true;
                } else if (curr < threshold * 0.5) {
                    inOnset = false;
                }
            }

            log.info("🥁 Extracted {} onsets from {}", onsets.size(), audioPath);

        } catch (Exception e) {
            log.error("❌ Error extracting onsets: {}", e.getMessage(), e);
        }

        return onsets;
    }

    /**
     * Analyze rhythm consistency (how regular are the beats)
     */
    public double analyzeConsistency(List<Double> onsets, Integer targetBpm, String timeSignature) {
        if (onsets.size() < 2) {
            return 0.0;
        }

        // Calculate inter-onset intervals
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < onsets.size(); i++) {
            intervals.add(onsets.get(i) - onsets.get(i - 1));
        }

        // Calculate expected interval from BPM
        double expectedInterval = targetBpm != null ? 60.0 / targetBpm :
                intervals.stream().mapToDouble(d -> d).average().orElse(0.5);

        // Score based on variance from expected
        double sumError = 0;
        for (double interval : intervals) {
            double error = Math.abs(interval - expectedInterval) / expectedInterval;
            sumError += Math.min(error, 1.0); // Cap error at 100%
        }

        double avgError = sumError / intervals.size();
        double score = Math.max(0, 100 * (1 - avgError));

        return score;
    }

    /**
     * Analyze rhythm creativity/variety
     */
    public double analyzeCreativity(List<Double> onsets) {
        if (onsets.size() < 4) {
            return 50.0; // Neutral score for short patterns
        }

        // Calculate intervals
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < onsets.size(); i++) {
            intervals.add(onsets.get(i) - onsets.get(i - 1));
        }

        // Count unique rhythm patterns (quantized to 16th notes)
        java.util.Set<Integer> uniquePatterns = new java.util.HashSet<>();
        double minInterval = intervals.stream().mapToDouble(d -> d).min().orElse(0.1);

        for (double interval : intervals) {
            int quantized = (int) Math.round(interval / minInterval);
            uniquePatterns.add(quantized);
        }

        // More variety = higher creativity score (max 100)
        double variety = (double) uniquePatterns.size() / intervals.size();
        return Math.min(100, variety * 150);
    }

    /**
     * Compare two rhythm patterns
     */
    public double compareRhythms(List<Double> userOnsets, List<Double> refOnsets) {
        if (userOnsets.isEmpty() || refOnsets.isEmpty()) {
            return 0.0;
        }

        // Normalize to relative timing
        List<Double> userIntervals = getIntervals(userOnsets);
        List<Double> refIntervals = getIntervals(refOnsets);

        if (userIntervals.isEmpty() || refIntervals.isEmpty()) {
            return 0.0;
        }

        // Dynamic Time Warping for rhythm comparison
        int n = userIntervals.size();
        int m = refIntervals.size();
        double[][] dtw = new double[n + 1][m + 1];

        for (int i = 0; i <= n; i++) dtw[i][0] = Double.MAX_VALUE;
        for (int j = 0; j <= m; j++) dtw[0][j] = Double.MAX_VALUE;
        dtw[0][0] = 0;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                double cost = Math.abs(userIntervals.get(i - 1) - refIntervals.get(j - 1));
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }

        double avgInterval = refIntervals.stream().mapToDouble(d -> d).average().orElse(0.5);
        double normalizedDistance = dtw[n][m] / (Math.max(n, m) * avgInterval);

        // Convert to score (0-100)
        return Math.max(0, 100 * (1 - normalizedDistance));
    }

    /**
     * Compare intensity/amplitude patterns
     */
    public double compareIntensity(String userPath, String refPath) {
        // Simplified intensity comparison
        // In production, use proper RMS comparison
        return 70.0; // Placeholder
    }

    public String generateRhythmMetrics(List<Double> onsets, double consistency, double creativity) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("totalBeats", onsets.size());
            root.put("consistencyScore", Math.round(consistency * 100) / 100.0);
            root.put("creativityScore", Math.round(creativity * 100) / 100.0);

            if (onsets.size() > 1) {
                List<Double> intervals = getIntervals(onsets);
                double avgInterval = intervals.stream().mapToDouble(d -> d).average().orElse(0);
                double estimatedBpm = 60.0 / avgInterval;
                root.put("estimatedBpm", Math.round(estimatedBpm));
                root.put("averageIntervalMs", Math.round(avgInterval * 1000));
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    public String generateComparisonMetrics(
            List<Double> userOnsets, List<Double> refOnsets,
            double rhythmScore, double intensityScore) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("userBeats", userOnsets.size());
            root.put("referenceBeats", refOnsets.size());
            root.put("beatCountDifference", Math.abs(userOnsets.size() - refOnsets.size()));
            root.put("rhythmMatchScore", Math.round(rhythmScore * 100) / 100.0);
            root.put("intensityMatchScore", Math.round(intensityScore * 100) / 100.0);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Double> getIntervals(List<Double> onsets) {
        List<Double> intervals = new ArrayList<>();
        for (int i = 1; i < onsets.size(); i++) {
            intervals.add(onsets.get(i) - onsets.get(i - 1));
        }
        return intervals;
    }
}
