package com.karaoke.dto;

import com.karaoke.model.Song;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Song information data transfer object")
public class SongDTO {
    @Schema(description = "Unique song identifier", example = "abc123-def456")
    private String id;
    
    @Schema(description = "Song title", example = "Bohemian Rhapsody")
    private String title;
    
    @Schema(description = "Artist name", example = "Queen")
    private String artist;
    
    @Schema(description = "Duration in seconds", example = "355")
    private Integer duration;
    
    @Schema(description = "Music genre", example = "Rock")
    private String genre;
    
    @Schema(description = "Difficulty level", example = "HARD")
    private String difficultyLevel;
    
    public static SongDTO fromEntity(Song song) {
        return new SongDTO(
            song.getId(),
            song.getTitle(),
            song.getArtist(),
            song.getDuration(),
            song.getGenre(),
            song.getDifficultyLevel().name()
        );
    }
}
