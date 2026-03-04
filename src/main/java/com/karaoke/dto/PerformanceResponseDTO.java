package com.karaoke.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceResponseDTO {
    private Long performanceId;
    private String status;
    private String message;
}