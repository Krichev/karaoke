package com.karaoke.controller;

import com.karaoke.dto.SongDTO;
import com.karaoke.dto.SongResponseDTO;
import com.karaoke.model.Song;
import com.karaoke.service.SongService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/songs")
public class SongController {
    
    private final SongService songService;
    
    public SongController(SongService songService) {
        this.songService = songService;
    }
    
    /**
     * GET /api/songs - List all songs with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSongs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<SongDTO> songsPage = songService.getAllSongs(PageRequest.of(page, size));
        
        Map<String, Object> response = new HashMap<>();
        response.put("content", songsPage.getContent());
        response.put("totalElements", songsPage.getTotalElements());
        response.put("totalPages", songsPage.getTotalPages());
        response.put("currentPage", songsPage.getNumber());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * GET /api/songs/{id} - Get song by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<SongDTO> getSongById(@PathVariable String id) {
        Song song = songService.getSongById(id);
        return ResponseEntity.ok(SongDTO.fromEntity(song));
    }
    
    /**
     * POST /api/admin/songs - Add new song (Admin endpoint)
     */
    @PostMapping(value = "/admin/songs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SongResponseDTO> addSong(
        @RequestParam("title") String title,
        @RequestParam("artist") String artist,
        @RequestParam("duration") Integer duration,
        @RequestParam(value = "genre", required = false) String genre,
        @RequestParam("difficultyLevel") Song.DifficultyLevel difficultyLevel,
        @RequestParam("referenceAudio") MultipartFile referenceAudio
    ) {
        // Validate file
        if (referenceAudio.isEmpty()) {
            throw new IllegalArgumentException("Reference audio file is required");
        }
        
        Song song = songService.addSong(title, artist, duration, genre, difficultyLevel, referenceAudio);
        
        SongResponseDTO response = new SongResponseDTO(
            song.getId(),
            song.getTitle(),
            song.getArtist(),
            "Song added successfully, reference data processing initiated",
            song.getReferenceProcessingStatus().name()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
