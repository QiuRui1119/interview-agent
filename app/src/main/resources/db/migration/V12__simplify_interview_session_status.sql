ALTER TABLE interview_sessions
  DROP CONSTRAINT ck_interview_sessions_status;

UPDATE interview_sessions
SET status = CASE
  WHEN status IN ('GENERATING', 'GENERATION_FAILED') THEN 'CREATED'
  WHEN status = 'READY' THEN 'IN_PROGRESS'
  WHEN status IN ('EVALUATED', 'EVALUATION_FAILED') THEN 'COMPLETED'
  ELSE status
END;

ALTER TABLE interview_sessions
  ADD CONSTRAINT ck_interview_sessions_status
  CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED'));
