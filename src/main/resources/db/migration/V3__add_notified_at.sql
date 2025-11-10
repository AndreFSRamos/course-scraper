ALTER TABLE course
  ADD COLUMN IF NOT EXISTS notified_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_course_notified_at ON course (notified_at);
