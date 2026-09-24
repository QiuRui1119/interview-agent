ALTER TABLE interview_sessions
  ADD COLUMN overall_score INTEGER,
  ADD COLUMN evaluated_at TIMESTAMP;

ALTER TABLE interview_sessions
  ADD CONSTRAINT ck_interview_sessions_overall_score
  CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100);

CREATE INDEX idx_interview_sessions_overall_score
  ON interview_sessions(overall_score);
