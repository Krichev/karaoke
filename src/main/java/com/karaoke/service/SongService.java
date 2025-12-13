package com.karaoke.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.karaoke.dto.SongDTO;
import com.karaoke.exception.ResourceNotFoundException;
import com.karaoke.model.ProcessingStatus;
import com.karaoke.model.Song;
import com.karaoke.repository.SongRepository;
import com.karaoke.util.AudioProcessor;
import com.karaoke.util.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
public class SongService {
    
    private final SongRepository songRepository;
    private final FileStorageService fileStorageService;
    private final AudioProcessor audioProcessor;
    private final ObjectMapper objectMapper;
    
    public SongService(
        SongRepository songRepository,
        FileStorageService fileStorageService,
        AudioProcessor audioProcessor,
        ObjectMapper objectMapper
    ) {
        this.songRepository = songRepository;
        this.fileStorageService = fileStorageService;
        this.audioProcessor = audioProcessor;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get all songs with pagination
     */
    public Page<SongDTO> getAllSongs(Pageable pageable) {
        return songRepository.findAll(pageable)
            .map(SongDTO::fromEntity);
    }
    
    /**
     * Get song by ID
     */
    public Song getSongById(String songId) {
        return songRepository.findById(songId)
            .orElseThrow(() -> new ResourceNotFoundException("Song not found: " + songId));
    }
    
    /**
     * Add new song with reference audio
     */
    @Transactional
    public Song addSong(
        String title,
        String artist,
        Integer duration,
        String genre,
        Song.DifficultyLevel difficultyLevel,
        MultipartFile referenceAudio
    ) {
        // Validate inputs
        if (referenceAudio == null || referenceAudio.isEmpty()) {
            throw new IllegalArgumentException("Reference audio file is required");
        }
        if (duration < 10 || duration > 600) {
            throw new IllegalArgumentException("Duration must be between 10 and 600 seconds");
        }
        String contentType = referenceAudio.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            throw new IllegalArgumentException("File must be an audio file");
        }

        try {
            Song song = new Song();
            song.setTitle(title);
            song.setArtist(artist);
            song.setDuration(duration);
            song.setGenre(genre);
            song.setDifficultyLevel(difficultyLevel);
            song.setReferenceProcessingStatus(ProcessingStatus.PENDING);
            
            // Save initially to get ID
            song = songRepository.save(song);
            
            // Store reference audio
            String audioPath = fileStorageService.storeReferenceTrack(referenceAudio, song.getId());
            song.setReferenceAudioPath(audioPath);
            song = songRepository.save(song);
            
            // Process reference audio asynchronously
            processReferenceSong(song.getId());
            
            log.info("Added new song: {} by {}", title, artist);
            return song;
            
        } catch (Exception e) {
            log.error("Error adding song", e);
            throw new RuntimeException("Failed to add song", e);
        }
    }
    
    /**
     * Process reference audio asynchronously
     */
    @Async("audioProcessingExecutor")
    @Transactional
    public void processReferenceSong(String songId) {
        try {
            Song song = getSongById(songId);
            song.setReferenceProcessingStatus(ProcessingStatus.PROCESSING);
            songRepository.save(song);
            
            // Extract pitch data
            List<Double> pitchValues = audioProcessor.extractPitchValues(song.getReferenceAudioPath());
            String pitchDataJson = objectMapper.writeValueAsString(pitchValues);
            
            // For MVP, rhythm data is simplified
            String rhythmDataJson = generateSimpleRhythmData(pitchValues, song.getDuration());
            
            song.setReferencePitchData(pitchDataJson);
            song.setReferenceRhythmData(rhythmDataJson);
            song.setReferenceProcessingStatus(ProcessingStatus.COMPLETED);
            songRepository.save(song);
            
            log.info("Completed processing reference audio for song: {}", songId);
            
        } catch (Exception e) {
            log.error("Error processing reference audio for song: {}", songId, e);
            Song song = songRepository.findById(songId).orElse(null);
            if (song != null) {
                song.setReferenceProcessingStatus(ProcessingStatus.FAILED);
                songRepository.save(song);
            }
        }
    }

    private String generateSimpleRhythmData(List<Double> pitchValues, int durationSeconds) {
        try {
            ObjectNode rhythmData = objectMapper.createObjectNode();
            rhythmData.put("totalNotes", pitchValues.size());
            rhythmData.put("avgNoteDuration", 
                pitchValues.isEmpty() ? 0 : (double) durationSeconds / pitchValues.size());
            rhythmData.put("estimatedTempo", 
                pitchValues.isEmpty() ? 0 : (pitchValues.size() * 60.0) / durationSeconds);
            return objectMapper.writeValueAsString(rhythmData);
        } catch (Exception e) {
            log.error("Error generating rhythm data", e);
            return "{}";
        }
    }
}
