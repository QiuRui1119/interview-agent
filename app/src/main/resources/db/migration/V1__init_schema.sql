CREATE TABLE resumes (
  id BIGSERIAL PRIMARY KEY,
  file_hash VARCHAR(64) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  file_size BIGINT,
  content_type VARCHAR(255),
  storage_key VARCHAR(500),
  storage_url VARCHAR(1000),
  resume_text TEXT,
  upload_time TIMESTAMP NOT NULL,
  last_access_time TIMESTAMP,
  access_count INTEGER NOT NULL,
  analyze_status VARCHAR(20) NOT NULL,
  analyze_error VARCHAR(500)
);

CREATE UNIQUE INDEX idx_resume_hash ON resumes (file_hash);

CREATE TABLE knowledge_bases (
  id BIGSERIAL PRIMARY KEY,
  file_hash VARCHAR(64) NOT NULL,
  source_type VARCHAR(20) NOT NULL DEFAULT 'USER_UPLOAD',
  source_key VARCHAR(500),
  content_hash VARCHAR(64),
  version INTEGER NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(100) NOT NULL,
  original_file_name VARCHAR(255) NOT NULL,
  file_size BIGINT,
  content_type VARCHAR(255),
  storage_key VARCHAR(500),
  storage_url VARCHAR(1000),
  upload_time TIMESTAMP NOT NULL,
  last_accessed_time TIMESTAMP,
  access_count INTEGER NOT NULL,
  question_count INTEGER,
  vector_status VARCHAR(20) NOT NULL,
  vector_error VARCHAR(1000),
  vector_chunk_count INTEGER,
  CONSTRAINT uk_kb_source_hash UNIQUE (source_type, file_hash)
);

CREATE INDEX idx_knowledge_base_category ON knowledge_bases (category);
CREATE INDEX idx_kb_source_type ON knowledge_bases (source_type);
CREATE UNIQUE INDEX uk_kb_builtin_source_key
  ON knowledge_bases (source_key) WHERE source_type = 'BUILT_IN';

CREATE TABLE resume_analyses (
  id SERIAL PRIMARY KEY,
  resume_id BIGINT NOT NULL,
  overall_score INTEGER,
  content_score INTEGER,
  structure_score INTEGER,
  skill_match_score INTEGER,
  expression_score INTEGER,
  project_score INTEGER,
  summary TEXT,
  strengths_json TEXT,
  suggestions_json TEXT,
  analysis_time TIMESTAMP(6) NOT NULL,
  CONSTRAINT fk_resume_analyses_resume
    FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE
);

CREATE INDEX idx_resume_analyses_resume_id ON resume_analyses (resume_id);

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
  id UUID PRIMARY KEY,
  content TEXT NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  embedding VECTOR(1024) NOT NULL
);

CREATE INDEX idx_vector_store_embedding_hnsw
  ON vector_store USING HNSW (embedding vector_cosine_ops);
CREATE INDEX idx_vector_store_metadata_gin
  ON vector_store USING GIN (metadata);
CREATE INDEX idx_vector_store_metadata_kb_id
  ON vector_store ((metadata->>'kb_id'));

CREATE TABLE interview_sessions (
  id BIGSERIAL PRIMARY KEY,
  session_id VARCHAR(36) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
  status_reason VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  resume_id BIGINT,
  skill_id VARCHAR(100),
  difficulty VARCHAR(30),
  topic_count INTEGER NOT NULL DEFAULT 0,
  max_follow_ups_per_topic INTEGER NOT NULL DEFAULT 0,
  max_turns INTEGER NOT NULL DEFAULT 0,
  current_topic_order INTEGER,
  current_turn_id BIGINT,
  total_turns INTEGER NOT NULL DEFAULT 0,
  generation_status VARCHAR(30),
  generation_error VARCHAR(1000),
  evaluate_status VARCHAR(30),
  evaluate_error VARCHAR(1000),
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  knowledge_base_ids_json TEXT NOT NULL DEFAULT '[]',
  evaluation_report TEXT,
  overall_score INTEGER,
  evaluated_at TIMESTAMP,
  CONSTRAINT uk_interview_sessions_session_id UNIQUE (session_id),
  CONSTRAINT ck_interview_sessions_status
    CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED')),
  CONSTRAINT ck_interview_sessions_overall_score
    CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_interview_sessions_overall_score ON interview_sessions (overall_score);

CREATE TABLE interview_topics (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL,
  category VARCHAR(100) NOT NULL,
  topic_name VARCHAR(255) NOT NULL,
  topic_order INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  objective TEXT,
  retrieval_query TEXT,
  base_question TEXT,
  fallback_follow_up TEXT,
  reference_answer TEXT,
  scoring_points_json TEXT,
  follow_up_count INTEGER NOT NULL DEFAULT 0,
  max_follow_ups INTEGER NOT NULL DEFAULT 0,
  evidence_snapshot_json TEXT NOT NULL DEFAULT '[]',
  CONSTRAINT fk_interview_topics_session
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  CONSTRAINT uk_interview_topics_session_topic_order UNIQUE (session_id, topic_order),
  CONSTRAINT ck_interview_topics_topic_order CHECK (topic_order >= 0),
  CONSTRAINT ck_interview_topics_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'COMPLETED'))
);

CREATE INDEX idx_interview_topics_session_status
  ON interview_topics (session_id, status);

CREATE TABLE interview_turns (
  id BIGSERIAL PRIMARY KEY,
  topic_id BIGINT NOT NULL,
  turn_order INTEGER NOT NULL,
  question TEXT NOT NULL,
  answer TEXT,
  turn_type VARCHAR(30) NOT NULL DEFAULT 'BASE',
  parent_turn_id BIGINT,
  decision TEXT,
  request_id VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
  answered_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  session_id BIGINT NOT NULL,
  CONSTRAINT fk_interview_turns_topic
    FOREIGN KEY (topic_id) REFERENCES interview_topics (id) ON DELETE CASCADE,
  CONSTRAINT fk_interview_turns_session
    FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
  CONSTRAINT fk_interview_turns_parent
    FOREIGN KEY (parent_turn_id) REFERENCES interview_turns (id) ON DELETE SET NULL,
  CONSTRAINT uk_interview_turns_session_turn_order UNIQUE (session_id, turn_order),
  CONSTRAINT uk_interview_turns_session_request_id UNIQUE (session_id, request_id),
  CONSTRAINT ck_interview_turns_turn_order CHECK (turn_order >= 0),
  CONSTRAINT ck_interview_turns_status
    CHECK (status IN ('CREATED', 'ANSWER_PENDING', 'DECIDING', 'DECIDED')),
  CONSTRAINT ck_interview_turns_answer_state
    CHECK (status NOT IN ('DECIDING', 'DECIDED') OR answer IS NOT NULL)
);

CREATE INDEX idx_interview_turns_topic_status ON interview_turns (topic_id, status);
CREATE INDEX idx_interview_turns_session_status ON interview_turns (session_id, status);
