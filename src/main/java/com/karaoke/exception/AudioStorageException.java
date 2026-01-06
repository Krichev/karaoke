package com.karaoke.exception;

/**
 * Exception thrown when audio storage operations fail
 */
public class AudioStorageException extends RuntimeException {

    public AudioStorageException(String message) {
        super(message);
    }

    public AudioStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
