package com.interview.modules.resume.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Long> {

  /**
   * 根据简历查找所有评测记录。
   */
  List<ResumeAnalysisEntity> findByResumeEntityOrderByAnalysisTimeDesc(
      ResumeEntity resumeEntity);

  /**
   * 根据简历 ID 查找最新评测记录。
   */
  ResumeAnalysisEntity findFirstByResumeEntityIdOrderByAnalysisTimeDesc(Long resumeId);

  /**
   * 根据简历 ID 查找所有评测记录。
   */
  List<ResumeAnalysisEntity> findByResumeEntityIdOrderByAnalysisTimeDesc(Long resumeId);
}
