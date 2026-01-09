package com.karaoke.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RhythmAnalyzer {

    private final ObjectMapper objectMapper;

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
