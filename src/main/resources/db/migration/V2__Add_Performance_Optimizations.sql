-- ============================================================================
-- Karaoke Service - Performance Optimizations
-- Version: 2.0
-- Description: Additional indexes and optimizations for query performance
-- ============================================================================

-- Composite index for common user queries (performances by user and status)
CREATE INDEX idx_performances_user_status
    ON performances(user_id, processing_status);

-- Index for finding top scores by song
CREATE INDEX idx_performances_song_score
    ON performances(song_id, total_score DESC NULLS LAST);

-- Full text search index for song titles and artists
CREATE INDEX idx_songs_title_artist_search
    ON songs USING gin(to_tsvector('english', title || ' ' || artist));

-- Partial index for active processing (reduces index size)
CREATE INDEX idx_performances_processing_active
    ON performances(processing_status, processing_progress)
    WHERE processing_status = 'PROCESSING';

-- ============================================================================
-- VIEWS FOR COMMON QUERIES
-- ============================================================================

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

COMMENT ON VIEW completed_performances IS 'Performances with completed scoring and song details';
COMMENT ON VIEW available_songs IS 'Songs ready for user performances (reference data processed)';
