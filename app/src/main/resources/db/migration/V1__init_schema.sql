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
