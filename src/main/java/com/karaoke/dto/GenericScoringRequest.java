package com.karaoke.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericScoringRequest {
    private String userAudioPath;
    private String referenceAudioPath;
    private String userAudioUrl;
    private String referenceAudioUrl;
    private String challengeType; // RHYTHM_CREATION, RHYTHM_REPEAT, SOUND_MATCH, SINGING
    private Integer rhythmBpm;
    private String timeSignature;
    private String difficulty;
    private Integer toleranceStrictness;
}
