ALTER TABLE interview_sessions
  ADD COLUMN knowledge_base_ids_json TEXT NOT NULL DEFAULT '[]';
