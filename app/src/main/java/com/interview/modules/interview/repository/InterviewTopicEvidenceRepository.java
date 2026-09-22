package com.interview.modules.interview.repository;

import com.interview.modules.interview.model.InterviewTopicEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewTopicEvidenceRepository
    extends JpaRepository<InterviewTopicEvidenceEntity, Long> {}
