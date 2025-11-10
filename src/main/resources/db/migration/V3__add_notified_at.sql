ALTER TABLE course ADD COLUMN notified_at TIMESTAMP NULL;
CREATE INDEX idx_course_notified_at ON course (notified_at);
