CREATE TABLE knowledge_bases (
  id BIGSERIAL PRIMARY KEY,
  file_hash VARCHAR(64) NOT NULL,
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
  vector_chunk_count INTEGER
);

CREATE UNIQUE INDEX idx_knowledge_base_hash ON knowledge_bases (file_hash);
CREATE INDEX idx_knowledge_base_category ON knowledge_bases (category);

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
  analysis_time BIGINT NOT NULL,
  CONSTRAINT fk_resume_analyses_resume
    FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE
);

CREATE INDEX idx_resume_analyses_resume_id ON resume_analyses (resume_id);
