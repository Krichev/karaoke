package com.karaoke.dto;

import com.karaoke.model.Performance;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PerformanceStatusDTO {
    private String performanceId;
    private String status;
    private Integer progress;
    private String message;
    
    public static PerformanceStatusDTO fromEntity(Performance performance) {
        return new PerformanceStatusDTO(
            performance.getId(),
            performance.getProcessingStatus().name(),
            performance.getProcessingProgress(),
            performance.getProcessingMessage()
        );
    }
}
