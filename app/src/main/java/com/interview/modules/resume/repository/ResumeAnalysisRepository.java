package com.interview.modules.resume.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.interview.modules.resume.model.ResumeAnalysisEntity;
import com.interview.modules.resume.model.ResumeEntity;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysisEntity, Integer> {

  /**
   * 根据简历查找所有评测记录。
   */
  List<ResumeAnalysisEntity> findByResumeEntityOrderByAnalysisTimeDesc(
      ResumeEntity resumeEntity);

}
