-- ============================================================================
-- Karaoke Service - Initial PostgreSQL Schema
-- Version: 1.0
-- Description: Songs, performances, and scoring tables with async processing support
-- ============================================================================

-- ============================================================================
-- ENUM TYPES
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS karaoke;
SET search_path TO karaoke, public;
-- Processing status for async operations
CREATE TYPE processing_status_enum AS ENUM (
    'PENDING',
    'PROCESSING',
    'COMPLETED',
    'FAILED'
);

-- Song difficulty levels
CREATE TYPE difficulty_level_enum AS ENUM (
    'EASY',
    'MEDIUM',
    'HARD'
);

-- ============================================================================
-- MAIN TABLES
-- ============================================================================

-- Songs table: Reference tracks for karaoke
CREATE TABLE songs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid VARCHAR(36) UNIQUE NOT NULL,
    title VARCHAR(200) NOT NULL,
    artist VARCHAR(200) NOT NULL,
    duration INTEGER NOT NULL,
    genre VARCHAR(50),
    difficulty_level difficulty_level_enum NOT NULL DEFAULT 'MEDIUM',
    reference_audio_path TEXT NOT NULL,
    reference_pitch_data TEXT,
    reference_rhythm_data TEXT,
    reference_processing_status processing_status_enum NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Constraints
    CONSTRAINT check_duration_range CHECK (duration BETWEEN 10 AND 600)
);

-- Performances table: User karaoke recordings
CREATE TABLE performances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    song_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    audio_file_path TEXT NOT NULL,
    total_score DOUBLE PRECISION,
    processing_status processing_status_enum NOT NULL DEFAULT 'PENDING',
    processing_progress INTEGER DEFAULT 0,
    processing_message VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign keys
    CONSTRAINT fk_performance_song FOREIGN KEY (song_id)
        REFERENCES songs(id) ON DELETE CASCADE,

    -- Constraints
    CONSTRAINT check_processing_progress CHECK (processing_progress BETWEEN 0 AND 100)
);

-- Performance scores table: Detailed scoring results
CREATE TABLE performance_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    performance_id UUID NOT NULL UNIQUE,
    pitch_score DOUBLE PRECISION NOT NULL,
    rhythm_score DOUBLE PRECISION NOT NULL,
    voice_quality_score DOUBLE PRECISION NOT NULL,
    detailed_metrics TEXT,

    -- Foreign keys
    CONSTRAINT fk_score_performance FOREIGN KEY (performance_id)
        REFERENCES performances(id) ON DELETE CASCADE
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Songs indexes
CREATE INDEX idx_songs_artist ON songs(artist);
CREATE INDEX idx_songs_difficulty ON songs(difficulty_level);
CREATE INDEX idx_songs_processing_status ON songs(reference_processing_status);
CREATE INDEX idx_songs_created_at ON songs(created_at DESC);

-- Performances indexes
CREATE INDEX idx_performances_song_id ON performances(song_id);
CREATE INDEX idx_performances_user_id ON performances(user_id);
CREATE INDEX idx_performances_status ON performances(processing_status);
CREATE INDEX idx_performances_user_song ON performances(user_id, song_id);
CREATE INDEX idx_performances_created_at ON performances(created_at DESC);

-- Performance scores indexes
CREATE INDEX idx_scores_performance_id ON performance_scores(performance_id);

-- ============================================================================
-- FUNCTIONS & TRIGGERS
-- ============================================================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to songs
CREATE TRIGGER update_songs_updated_at
    BEFORE UPDATE ON songs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to performances
CREATE TRIGGER update_performances_updated_at
    BEFORE UPDATE ON performances
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE songs IS 'Reference karaoke tracks with pitch and rhythm analysis';
COMMENT ON TABLE performances IS 'User performance recordings awaiting or completed scoring';
COMMENT ON TABLE performance_scores IS 'Detailed scoring breakdown for performances';

COMMENT ON COLUMN songs.reference_pitch_data IS 'JSON array of pitch data extracted from reference track';
COMMENT ON COLUMN songs.reference_rhythm_data IS 'JSON array of rhythm/timing data from reference track';
COMMENT ON COLUMN performances.processing_progress IS 'Progress percentage (0-100) for async processing';
COMMENT ON COLUMN performance_scores.detailed_metrics IS 'JSON object with comprehensive scoring metrics';
