package com.karaoke.dto;

import com.karaoke.model.Song;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SongDTO {
    private String id;
    private String title;
    private String artist;
    private Integer duration;
    private String genre;
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
