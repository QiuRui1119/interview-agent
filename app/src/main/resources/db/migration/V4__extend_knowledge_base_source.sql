ALTER TABLE knowledge_bases
  ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'USER_UPLOAD',
  ADD COLUMN source_key VARCHAR(500),
  ADD COLUMN content_hash VARCHAR(64),
  ADD COLUMN version INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_kb_source_type
  ON knowledge_bases(source_type);

CREATE UNIQUE INDEX uk_kb_builtin_source_key
  ON knowledge_bases(source_key)
  WHERE source_type = 'BUILT_IN';

DROP INDEX IF EXISTS idx_knowledge_base_hash;

CREATE UNIQUE INDEX uk_kb_source_hash
  ON knowledge_bases(source_type, file_hash);
