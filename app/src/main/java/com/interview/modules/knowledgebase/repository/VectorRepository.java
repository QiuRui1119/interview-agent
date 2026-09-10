package com.interview.modules.knowledgebase.repository;

import java.util.List;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class VectorRepository {

  private static final int MAX_BATCH_SIZE = 10;

  private final VectorStore vectorStore;
  private final JdbcTemplate jdbcTemplate;

  public void saveAll(List<Document> documents) {
    for (int start = 0; start < documents.size(); start += MAX_BATCH_SIZE) {
      int end = Math.min(start + MAX_BATCH_SIZE, documents.size());
      vectorStore.add(documents.subList(start, end));
    }
  }

  @Transactional(rollbackOn = Exception.class)
  public void deleteByKnowledgeBaseId(Long kbId) {
    jdbcTemplate.update(
        "DELETE FROM vector_store " +
            "WHERE metadata->>'kb_id' = ?",
        kbId.toString());
  }

  @Transactional(rollbackOn = Exception.class)
  public void deleteByVectorJobId(String jobId) {
    jdbcTemplate.update(
        "DELETE FROM vector_store WHERE metadata->>'kb_vector_job_id' = ?",
        jobId);
  }

  /**
   * 在同一个数据库事务中把临时 job 提升为正式版本并删除旧正式向量。
   */
  @Transactional(rollbackOn = Exception.class)
  public void promoteVectorJob(Long kbId, String jobId) {
    int promoted = jdbcTemplate.update(
        "UPDATE vector_store " +
            "SET metadata = jsonb_set(metadata, '{is_current}', 'true'::jsonb) " +
            "WHERE metadata->>'kb_id' = ? AND metadata->>'kb_vector_job_id' = ?",
        kbId.toString(), jobId);
    if (promoted == 0) {
      throw new IllegalStateException("No temporary vectors found for job: " + jobId);
    }

    jdbcTemplate.update(
        "DELETE FROM vector_store " +
            "WHERE metadata->>'kb_id' = ? " +
            "AND (metadata->>'kb_vector_job_id' IS NULL " +
                "OR metadata->>'kb_vector_job_id' <> ?) " +
            "AND COALESCE(metadata->>'is_current', 'true') = 'true'",
        kbId.toString(), jobId);
  }
}
