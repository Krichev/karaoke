package com.karaoke.model;

public enum ProcessingStatus {
    PENDING,      // Queued, waiting to be processed
    PROCESSING,   // Currently being processed
    COMPLETED,    // Successfully completed
    FAILED        // Processing failed with error
}