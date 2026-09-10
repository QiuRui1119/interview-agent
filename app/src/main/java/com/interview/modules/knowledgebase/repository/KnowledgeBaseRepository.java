package com.interview.modules.knowledgebase.repository;

import org.springframework.stereotype.Repository;
import com.interview.modules.knowledgebase.model.KnowledgeBaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.interview.modules.knowledgebase.model.VectorStatus;
import com.interview.modules.knowledgebase.model.KnowledgeSourceType;
import java.util.Optional;
import java.util.List;


@Repository
public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {
    /**
     * 根据文件哈希查找知识库（用于去重）
     */
    Optional<KnowledgeBaseEntity> findByFileHashAndSourceType(
        String fileHash, KnowledgeSourceType sourceType);

    Optional<KnowledgeBaseEntity> findBySourceTypeAndSourceKey(
        KnowledgeSourceType sourceType, String sourceKey);

    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHashAndSourceType(String fileHash, KnowledgeSourceType sourceType);

    /**
     * 按上传时间倒序查找所有知识库
     */
    List<KnowledgeBaseEntity> findAllByOrderByUploadTimeDesc();

    /**
     * 获取所有不同的分类
     */
    @Query("SELECT DISTINCT k.category FROM KnowledgeBaseEntity k")
    List<String> findDistinctCategory();

    /**
     * 根据分类查找知识库
     */
    List<KnowledgeBaseEntity> findByCategoryOrderByUploadTimeDesc(String category);

    /**
     * 查找未分类的知识库
     */
    List<KnowledgeBaseEntity> findByCategoryIsNullOrderByUploadTimeDesc();

    /**
     * 按名称或文件名模糊搜索（不区分大小写）
     */
    @Query("SELECT k FROM KnowledgeBaseEntity k WHERE LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(k.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY k.uploadTime DESC")
    List<KnowledgeBaseEntity> searchByNameOrOriginalFileName(@Param("keyword") String keyword);

    /**
     * 按文件大小排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByFileSizeDesc();


    /**
     * 按访问次数排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByAccessCountDesc();

    /**
     * 按问题数量排序
     */
    List<KnowledgeBaseEntity> findAllByOrderByQuestionCountDesc();

    // ==================== 批量更新 ====================

    /**
     * 批量增加知识库提问计数
     * @param ids 知识库ID列表
     * @return 更新的行数
     */
    @Modifying
    @Query("UPDATE KnowledgeBaseEntity k SET k.questionCount = k.questionCount + 1 WHERE k.id IN :ids")
    int batchIncreaseQuestionCount(@Param("ids") List<Long> ids);

    // ==================== 统计查询 ====================

    /**
     * 统计总提问次数
     */
    @Query("SELECT COALESCE(SUM(k.questionCount), 0) FROM KnowledgeBaseEntity k")
    Long sumQuestionCount();

    /**
     * 统计总访问次数
     */
    @Query("SELECT COALESCE(SUM(k.accessCount), 0) FROM KnowledgeBaseEntity k")
    Long sumAccessCount();

    /**
     * 按向量化状态统计数量
     */
    Long countByVectorStatus(VectorStatus vectorStatus);

    /**
     * 按向量化状态查找知识库（按上传时间倒序）
     */
    List<KnowledgeBaseEntity> findByVectorStatusOrderByUploadTimeDesc(VectorStatus vectorStatus);
}
