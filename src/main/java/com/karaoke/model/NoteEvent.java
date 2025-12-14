package com.karaoke.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class NoteEvent implements Serializable {

    /**
     * Time when the note starts, in milliseconds from the beginning of the audio
     */
    private double onsetTimeMs;

    /**
     * Pitch/frequency of the note in Hertz (Hz)
     * Common values: 220-880 Hz for typical singing range
     * -1 or 0 indicates silence or unvoiced segment
     */
    private double pitch;

    /**
     * Duration of the note in milliseconds
     */
    private double durationMs;

    /**
     * Amplitude/volume of the note (0.0 to 1.0)
     * Represents relative strength of the note
     */
    private double amplitude;

    public NoteEvent() {
    }

    public NoteEvent(double onsetTimeMs, double pitch, double durationMs, double amplitude) {
        this.onsetTimeMs = onsetTimeMs;
        this.pitch = pitch;
        this.durationMs = durationMs;
        this.amplitude = amplitude;
    }

    /**
     * Convert pitch to MIDI note number for easier comparison
     * Formula: 69 + 12 * log2(frequency/440)
     */
    public int toMidiNote() {
        if (pitch <= 0) {
            return -1; // Silence
        }
        return (int) Math.round(69 + 12 * (Math.log(pitch / 440.0) / Math.log(2)));
    }

    /**
     * Calculate pitch difference in semitones from another note
     */
    public double semitonesDifferenceFrom(NoteEvent other) {
        if (this.pitch <= 0 || other.pitch <= 0) {
            return Double.MAX_VALUE; // Can't compare silence
        }
        return 12 * (Math.log(this.pitch / other.pitch) / Math.log(2));
    }

    /**
     * Check if this note overlaps with another in time
     */
    public boolean overlapsInTime(NoteEvent other) {
        double thisEnd = this.onsetTimeMs + this.durationMs;
        double otherEnd = other.onsetTimeMs + other.durationMs;

        return this.onsetTimeMs < otherEnd && other.onsetTimeMs < thisEnd;
    }

    /**
     * Calculate timing offset from another note in milliseconds
     * Positive value means this note starts AFTER the other note
     * Negative value means this note starts BEFORE the other note
     */
    public double timingOffsetMs(NoteEvent other) {
        return this.onsetTimeMs - other.onsetTimeMs;
    }
}