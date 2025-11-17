CREATE TABLE IF NOT EXISTS course_cache (
    id BIGSERIAL PRIMARY KEY,
    course_hash VARCHAR(255) NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_course_cache_hash
    ON course_cache (course_hash);

CREATE INDEX IF NOT EXISTS idx_course_cache_source
    ON course_cache (source);
