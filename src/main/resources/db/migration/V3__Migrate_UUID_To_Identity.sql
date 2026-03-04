-- ============================================================================
-- Karaoke Service - Migrate UUID IDs to BIGSERIAL (Long)
-- Version: 3.0
-- Description: Replaces UUID primary keys with BIGSERIAL (BIGINT)
-- CRITICAL: This migration DROPS and RECREATES tables. 
-- Assumption: There is NO production data yet.
-- ============================================================================

SET search_path TO karaoke, public;

-- 1. Drop views that depend on the tables
DROP VIEW IF EXISTS available_songs;
DROP VIEW IF EXISTS completed_performances;

-- 2. Drop tables in reverse dependency order
DROP TABLE IF EXISTS performance_scores;
DROP TABLE IF EXISTS performances;
DROP TABLE IF EXISTS songs;

-- 3. Recreate tables with BIGSERIAL primary keys

-- Songs table
CREATE TABLE songs (
    id BIGSERIAL PRIMARY KEY,
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

-- Performances table
CREATE TABLE performances (
    id BIGSERIAL PRIMARY KEY,
    song_id BIGINT NOT NULL,
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

-- Performance scores table
CREATE TABLE performance_scores (
    id BIGSERIAL PRIMARY KEY,
    performance_id BIGINT NOT NULL UNIQUE,
    pitch_score DOUBLE PRECISION NOT NULL,
    rhythm_score DOUBLE PRECISION NOT NULL,
    voice_quality_score DOUBLE PRECISION NOT NULL,
    detailed_metrics TEXT,

    -- Foreign keys
    CONSTRAINT fk_score_performance FOREIGN KEY (performance_id)
        REFERENCES performances(id) ON DELETE CASCADE
);

-- 4. Recreate Indexes from V1 and V2

-- Songs indexes
CREATE INDEX idx_songs_artist ON songs(artist);
CREATE INDEX idx_songs_difficulty ON songs(difficulty_level);
CREATE INDEX idx_songs_processing_status ON songs(reference_processing_status);
CREATE INDEX idx_songs_created_at ON songs(created_at DESC);
CREATE INDEX idx_songs_title_artist_search ON songs USING gin(to_tsvector('english', title || ' ' || artist));

-- Performances indexes
CREATE INDEX idx_performances_song_id ON performances(song_id);
CREATE INDEX idx_performances_user_id ON performances(user_id);
CREATE INDEX idx_performances_status ON performances(processing_status);
CREATE INDEX idx_performances_user_song ON performances(user_id, song_id);
CREATE INDEX idx_performances_created_at ON performances(created_at DESC);
CREATE INDEX idx_performances_user_status ON performances(user_id, processing_status);
CREATE INDEX idx_performances_song_score ON performances(song_id, total_score DESC NULLS LAST);
CREATE INDEX idx_performances_processing_active ON performances(processing_status, processing_progress) WHERE processing_status = 'PROCESSING';

-- Performance scores indexes
CREATE INDEX idx_scores_performance_id ON performance_scores(performance_id);

-- 5. Recreate Triggers (Functions already exist from V1)

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

-- 6. Recreate Views from V2

-- View for completed performances with scores
CREATE OR REPLACE VIEW completed_performances AS
SELECT
    p.id,
    p.song_id,
    p.user_id,
    s.title AS song_title,
    s.artist AS song_artist,
    p.total_score,
    ps.pitch_score,
    ps.rhythm_score,
    ps.voice_quality_score,
    p.created_at,
    p.updated_at
FROM performances p
JOIN songs s ON p.song_id = s.id
LEFT JOIN performance_scores ps ON p.id = ps.performance_id
WHERE p.processing_status = 'COMPLETED';

-- View for songs ready for performance
CREATE OR REPLACE VIEW available_songs AS
SELECT
    id,
    uuid,
    title,
    artist,
    duration,
    genre,
    difficulty_level,
    created_at
FROM songs
WHERE reference_processing_status = 'COMPLETED';

-- 7. Restore Comments
COMMENT ON TABLE songs IS 'Reference karaoke tracks with pitch and rhythm analysis';
COMMENT ON TABLE performances IS 'User performance recordings awaiting or completed scoring';
COMMENT ON TABLE performance_scores IS 'Detailed scoring breakdown for performances';

COMMENT ON COLUMN songs.reference_pitch_data IS 'JSON array of pitch data extracted from reference track';
COMMENT ON COLUMN songs.reference_rhythm_data IS 'JSON array of rhythm/timing data from reference track';
COMMENT ON COLUMN performances.processing_progress IS 'Progress percentage (0-100) for async processing';
COMMENT ON COLUMN performance_scores.detailed_metrics IS 'JSON object with comprehensive scoring metrics';

COMMENT ON VIEW completed_performances IS 'Performances with completed scoring and song details';
COMMENT ON VIEW available_songs IS 'Songs ready for user performances (reference data processed)';
