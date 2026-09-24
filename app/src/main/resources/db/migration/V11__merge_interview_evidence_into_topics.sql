ALTER TABLE interview_topics
  ADD COLUMN evidence_snapshot_json TEXT NOT NULL DEFAULT '[]';

UPDATE interview_topics topic
SET evidence_snapshot_json = snapshots.snapshot_json
FROM (
  SELECT evidence.topic_id,
         jsonb_agg(
           jsonb_strip_nulls(jsonb_build_object(
             'knowledgeBaseId', evidence.knowledge_base_id,
             'chunkId', evidence.chunk_id,
             'sourceName', evidence.source_name,
             'sourceKey', evidence.source_key,
             'sourceType', evidence.source_type,
             'category', evidence.category,
             'version', evidence.version,
             'score', evidence.score,
             'content', evidence.content_snapshot
           )) ORDER BY evidence.id
         )::TEXT AS snapshot_json
  FROM interview_topic_evidence evidence
  GROUP BY evidence.topic_id
) snapshots
WHERE topic.id = snapshots.topic_id;

DROP TABLE interview_topic_evidence;
