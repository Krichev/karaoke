package com.karaoke.exception;

import lombok.Getter;

@Getter
public class AudioDownloadException extends RuntimeException {
    private final String url;
    private final int statusCode;

    public AudioDownloadException(String message, String url, int statusCode) {
        super(message);
        this.url = url;
        this.statusCode = statusCode;
    }

    public AudioDownloadException(String message, String url, int statusCode, Throwable cause) {
        super(message, cause);
        this.url = url;
        this.statusCode = statusCode;
    }
}
