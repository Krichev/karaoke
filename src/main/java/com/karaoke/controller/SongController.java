package com.karaoke.controller;

import com.karaoke.dto.SongDTO;
import com.karaoke.dto.SongResponseDTO;
import com.karaoke.model.Song;
import com.karaoke.service.SongService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Songs", description = "Song management API")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @Operation(
        summary = "List all songs",
        description = "Retrieve a paginated list of all available karaoke songs with their metadata"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved song list"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    @GetMapping
    public ResponseEntity<Map<String, Object>> listSongs(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Number of items per page", example = "20")
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

    @Operation(
        summary = "Get song by ID",
        description = "Retrieve detailed information about a specific song including processing status"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Song found"),
        @ApiResponse(responseCode = "404", description = "Song not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ResponseEntity<SongDTO> getSongById(
        @Parameter(description = "Unique song identifier", example = "abc123-def456")
        @PathVariable String id
    ) {
        Song song = songService.getSongById(id);
        return ResponseEntity.ok(SongDTO.fromEntity(song));
    }

    @Operation(
        summary = "Add new song (Admin)",
        description = "Upload a new karaoke song with reference audio track. The reference audio will be processed asynchronously to extract pitch and rhythm data."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Song created successfully, processing initiated"),
        @ApiResponse(responseCode = "400", description = "Invalid input data or audio file", content = @Content)
    })
    @PostMapping(value = "/admin/songs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SongResponseDTO> addSong(
        @Parameter(description = "Song title", example = "Bohemian Rhapsody")
        @RequestParam("title") String title,
        @Parameter(description = "Artist name", example = "Queen")
        @RequestParam("artist") String artist,
        @Parameter(description = "Duration in seconds (10-600)", example = "355")
        @RequestParam("duration") Integer duration,
        @Parameter(description = "Music genre", example = "Rock")
        @RequestParam(value = "genre", required = false) String genre,
        @Parameter(description = "Difficulty level")
        @RequestParam("difficultyLevel") Song.DifficultyLevel difficultyLevel,
        @Parameter(description = "Reference audio file (MP3, WAV, etc.)")
        @RequestParam("referenceAudio") MultipartFile referenceAudio
    ) {
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
