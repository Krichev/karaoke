package com.karaoke.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SongResponseDTO {
    private String id;
    private String title;
    private String artist;
    private String message;
    private String processingStatus;
}
