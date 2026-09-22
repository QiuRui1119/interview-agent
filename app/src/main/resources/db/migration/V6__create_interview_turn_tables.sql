ALTER TABLE interview_sessions
  ADD COLUMN resume_id BIGINT,
  ADD COLUMN skill_id VARCHAR(100),
  ADD COLUMN difficulty VARCHAR(30),
  ADD COLUMN topic_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN max_follow_ups_per_topic INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN max_turns INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN current_topic_order INTEGER,
  ADD COLUMN current_turn_id BIGINT,
  ADD COLUMN total_turns INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN generation_status VARCHAR(30),
  ADD COLUMN generation_error VARCHAR(1000),
  ADD COLUMN evaluate_status VARCHAR(30),
  ADD COLUMN evaluate_error VARCHAR(1000),
  ADD COLUMN started_at TIMESTAMP,
  ADD COLUMN completed_at TIMESTAMP;

ALTER TABLE interview_topics
  RENAME COLUMN topic_key TO category;

ALTER TABLE interview_topics
  RENAME COLUMN name TO topic_name;

ALTER TABLE interview_topics
  RENAME COLUMN position TO topic_order;

ALTER TABLE interview_topics
  ADD COLUMN objective TEXT,
  ADD COLUMN retrieval_query TEXT,
  ADD COLUMN base_question TEXT,
  ADD COLUMN fallback_follow_up TEXT,
  ADD COLUMN reference_answer TEXT,
  ADD COLUMN scoring_points_json TEXT,
  ADD COLUMN follow_up_count INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN max_follow_ups INTEGER NOT NULL DEFAULT 0;

ALTER TABLE interview_topics
  DROP CONSTRAINT uk_interview_topics_session_position;

ALTER TABLE interview_topics
  ADD CONSTRAINT uk_interview_topics_session_topic_order
  UNIQUE (session_id, topic_order);

ALTER TABLE interview_turns
  ADD COLUMN session_id BIGINT,
  ADD COLUMN turn_type VARCHAR(30) NOT NULL DEFAULT 'BASE',
  ADD COLUMN parent_turn_id BIGINT,
  ADD COLUMN decision TEXT,
  ADD COLUMN request_id VARCHAR(100),
  ADD CONSTRAINT fk_interview_turns_session
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  ADD CONSTRAINT fk_interview_turns_parent
    FOREIGN KEY (parent_turn_id) REFERENCES interview_turns (id) ON DELETE SET NULL;

UPDATE interview_turns t
SET session_id = topic.session_id
FROM interview_topics topic
WHERE t.topic_id = topic.id;

ALTER TABLE interview_turns
  ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE interview_turns
  RENAME COLUMN position TO turn_order;

ALTER TABLE interview_turns
  DROP CONSTRAINT uk_interview_turns_topic_position;

ALTER TABLE interview_turns
  ADD CONSTRAINT uk_interview_turns_session_turn_order
    UNIQUE (session_id, turn_order),
  ADD CONSTRAINT uk_interview_turns_session_request_id
    UNIQUE (session_id, request_id);

CREATE INDEX idx_interview_turns_session_status
  ON interview_turns(session_id, status);

CREATE TABLE interview_topic_evidence (
  id BIGSERIAL PRIMARY KEY,
  topic_id BIGINT NOT NULL,
  knowledge_base_id BIGINT NOT NULL,
  chunk_id VARCHAR(255) NOT NULL,
  source_name VARCHAR(255),
  source_key VARCHAR(500),
  source_type VARCHAR(20),
  category VARCHAR(100),
  version INTEGER,
  score DOUBLE PRECISION,
  content_snapshot TEXT NOT NULL,
  CONSTRAINT fk_interview_topic_evidence_topic
    FOREIGN KEY (topic_id) REFERENCES interview_topics (id) ON DELETE CASCADE
);

CREATE INDEX idx_interview_topic_evidence_topic
  ON interview_topic_evidence(topic_id);
