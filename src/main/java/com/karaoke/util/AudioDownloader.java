package com.karaoke.util;

import com.karaoke.exception.AudioDownloadException;
import com.karaoke.service.AudioStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Slf4j
public class AudioDownloader {

    private final AudioStorageService audioStorageService;
    private final HttpClient httpClient;
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB

    public AudioDownloader(AudioStorageService audioStorageService) {
        this.audioStorageService = audioStorageService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Download audio from a presigned URL via HTTP GET.
     * @throws AudioDownloadException on failure
     */
    public byte[] downloadFromUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or blank");
        }

        String sanitizedUrl = sanitizeUrlForLogging(url);
        log.info("📥 Downloading audio from URL: {}", sanitizedUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                log.error("❌ Failed to download audio from URL: {}. Status code: {}", sanitizedUrl, statusCode);
                throw new AudioDownloadException("Failed to download audio. HTTP " + statusCode, sanitizedUrl, statusCode);
            }

            byte[] body = response.body();
            if (body == null || body.length == 0) {
                log.error("❌ Downloaded audio is empty from URL: {}", sanitizedUrl);
                throw new AudioDownloadException("Downloaded audio is empty", sanitizedUrl, statusCode);
            }

            if (body.length > MAX_FILE_SIZE) {
                log.error("❌ Audio file exceeds maximum size (100MB): {} bytes from URL: {}", body.length, sanitizedUrl);
                throw new AudioDownloadException("Audio file exceeds maximum size", sanitizedUrl, statusCode);
            }

            log.info("✅ Successfully downloaded {} bytes from URL: {}", body.length, sanitizedUrl);
            return body;

        } catch (IOException e) {
            log.error("❌ I/O error while downloading audio from URL: {}", sanitizedUrl, e);
            throw new AudioDownloadException("I/O error during download: " + e.getMessage(), sanitizedUrl, 0, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Interrupted while downloading audio from URL: {}", sanitizedUrl, e);
            throw new AudioDownloadException("Download interrupted: " + e.getMessage(), sanitizedUrl, 0, e);
        } catch (Exception e) {
            log.error("❌ Unexpected error while downloading audio from URL: {}", sanitizedUrl, e);
            throw new AudioDownloadException("Unexpected error during download: " + e.getMessage(), sanitizedUrl, 0, e);
        }
    }

    /**
     * Download audio from karaoke's own MinIO (internal use).
     */
    public byte[] downloadFromMinio(String s3Key) {
        return audioStorageService.downloadAudio(s3Key);
    }

    /**
     * Resolve audio bytes — URL takes priority over path.
     * @param url  presigned URL (nullable)
     * @param path S3 key for karaoke-internal MinIO (nullable)
     * @param label human-readable label for logging (e.g., "user audio", "reference audio")
     * @return audio bytes
     * @throws IllegalArgumentException if both null
     */
    public byte[] resolveAudio(String url, String path, String label) {
        if (url != null && !url.isBlank()) {
            log.info("📥 Resolving {} via presigned URL", label);
            return downloadFromUrl(url);
        } else if (path != null && !path.isBlank()) {
            log.info("📥 Resolving {} from MinIO path: {}", label, path);
            return downloadFromMinio(path);
        }
        throw new IllegalArgumentException("No audio source (URL or path) provided for " + label);
    }

    private String sanitizeUrlForLogging(String url) {
        if (url == null) return null;
        int queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) + "?[REDACTED]" : url;
    }
}
