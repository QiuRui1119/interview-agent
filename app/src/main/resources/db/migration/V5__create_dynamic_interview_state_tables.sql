CREATE TABLE interview_sessions (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
  status_reason VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_interview_sessions_session_id UNIQUE (session_id),
  CONSTRAINT ck_interview_sessions_status CHECK (status IN (
    'CREATED', 'GENERATING', 'READY', 'IN_PROGRESS', 'COMPLETED', 'EVALUATED',
    'GENERATION_FAILED', 'EVALUATION_FAILED', 'ABORTED'
  ))
);

CREATE TABLE interview_topics (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL,
  topic_key VARCHAR(100) NOT NULL,
  name VARCHAR(255) NOT NULL,
  position INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_interview_topics_session
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  CONSTRAINT uk_interview_topics_session_position UNIQUE (session_id, position),
  CONSTRAINT ck_interview_topics_position CHECK (position >= 0),
  CONSTRAINT ck_interview_topics_status CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_interview_topics_session_status
  ON interview_topics(session_id, status);

CREATE TABLE interview_turns (
  id BIGSERIAL PRIMARY KEY,
  topic_id BIGINT NOT NULL,
  position INTEGER NOT NULL,
  question TEXT NOT NULL,
  answer TEXT,
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  answered_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_interview_turns_topic
    FOREIGN KEY (topic_id) REFERENCES interview_topics (id) ON DELETE CASCADE,
  CONSTRAINT uk_interview_turns_topic_position UNIQUE (topic_id, position),
  CONSTRAINT ck_interview_turns_position CHECK (position >= 0),
  CONSTRAINT ck_interview_turns_status CHECK (status IN (
    'CREATED', 'ANSWER_PENDING', 'DECIDING', 'DECIDED'
  )),
  CONSTRAINT ck_interview_turns_answer_state CHECK (
    status NOT IN ('DECIDING', 'DECIDED') OR answer IS NOT NULL
  )
);

CREATE INDEX idx_interview_turns_topic_status
  ON interview_turns(topic_id, status);
