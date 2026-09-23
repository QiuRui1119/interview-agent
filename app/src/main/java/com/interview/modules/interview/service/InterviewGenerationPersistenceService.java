package com.interview.modules.interview.service;

import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.modules.interview.model.InterviewSessionEntity;
import com.interview.modules.interview.model.InterviewSessionStatus;
import com.interview.modules.interview.model.InterviewTopicEntity;
import com.interview.modules.interview.model.InterviewTopicStatus;
import com.interview.modules.interview.model.InterviewTopicEvidenceEntity;
import com.interview.modules.interview.model.InterviewTurnEntity;
import com.interview.modules.interview.model.InterviewTurnStatus;
import com.interview.modules.interview.repository.InterviewSessionRepository;
import com.interview.modules.interview.repository.InterviewTopicEvidenceRepository;
import com.interview.modules.interview.repository.InterviewTopicRepository;
import com.interview.modules.interview.repository.InterviewTurnRepository;
import com.interview.modules.knowledgebase.model.RetrievedChunkDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewGenerationPersistenceService {
  private final InterviewSessionRepository sessionRepository;
  private final InterviewTopicRepository topicRepository;
  private final InterviewTurnRepository turnRepository;
  private final InterviewTopicEvidenceRepository evidenceRepository;

  @Transactional
  public void save(String sessionId, List<InterviewTopicGenerationService.GeneratedTopic> generated) {
    InterviewSessionEntity session = sessionRepository.findBySessionId(sessionId)
        .orElseThrow(() -> new BusinessException(ErrorCode.INTERVIEW_SESSION_NOT_FOUND));
    if (session.getStatus() != InterviewSessionStatus.GENERATING) return;
    InterviewTurnEntity firstTurn = null;
    for (int index = 0; index < generated.size(); index++) {
      var data = generated.get(index);
      InterviewTopicEntity topic = InterviewTopicEntity.create(
          session, data.category(), data.topicName(), index);
      topic.applyGeneratedContent(data.objective(), data.retrievalQuery(), data.baseQuestion(),
          data.fallbackFollowUp(), data.referenceAnswer(), data.scoringPointsJson(), 1);
      if (index == 0) {
        topic.transitionTo(InterviewTopicStatus.ACTIVE);
      }
      topic = topicRepository.save(topic);
      for (RetrievedChunkDTO chunk : data.evidence()) {
        evidenceRepository.save(InterviewTopicEvidenceEntity.create(topic,
            chunk.knowledgeBaseId(), chunk.chunkId(), chunk.sourceName(), chunk.sourceKey(),
            chunk.sourceType(), chunk.category(), chunk.version(), chunk.score(), chunk.content()));
      }
      if (index == 0) {
        firstTurn = InterviewTurnEntity.create(topic, 0, data.baseQuestion());
        firstTurn.transitionTo(InterviewTurnStatus.ANSWER_PENDING);
        firstTurn = turnRepository.saveAndFlush(firstTurn);
      }
    }
    session.markGenerationCompleted(firstTurn.getId());
    sessionRepository.save(session);
  }
}
