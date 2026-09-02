package com.interview.modules.resume.listener;

import org.springframework.stereotype.Component;
import com.interview.common.exception.BusinessException;
import com.interview.common.exception.ErrorCode;
import com.interview.infrastructure.stream.AsyncTaskStreamConstants;
import com.interview.infrastructure.redis.RedisService;
import com.interview.infrastructure.stream.AbstractStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import com.interview.common.config.ResumeAnalysisTaskProperties;
import com.interview.modules.resume.service.ResumePersistenceService;
import org.redisson.api.stream.StreamMessageId;
import java.util.Map;
import com.interview.modules.resume.repository.ResumeRepository;
import com.interview.modules.interview.model.ResumeAnalysisResponse;
import com.interview.modules.resume.service.ResumeGradingService;

@Slf4j
@Component
public class AnalyzeStreamConsumer extends AbstractStreamConsumer {

    private final ResumeAnalysisTaskProperties resumeAnalysisTaskProperties;
    private final ResumePersistenceService resumePersistenceService;
    private final ResumeRepository resumeRepository;
    private final ResumeGradingService resumeGradingService;


    public AnalyzeStreamConsumer(
            RedisService redisService,
            ResumeAnalysisTaskProperties resumeAnalysisTaskProperties,
            ResumePersistenceService resumePersistenceService,
            ResumeRepository resumeRepository,
            ResumeGradingService resumeGradingService) {
        super(redisService);
        this.resumeAnalysisTaskProperties = resumeAnalysisTaskProperties;
        this.resumePersistenceService = resumePersistenceService;
        this.resumeRepository = resumeRepository;
        this.resumeGradingService = resumeGradingService;
    }

    @Override
    protected boolean enabled(){
        return resumeAnalysisTaskProperties.isEnabled();
    }

    @Override
    protected String streamKey() {
        return resumeAnalysisTaskProperties.getStreamKey();
    }

    @Override
    protected String consumerGroup() {
        return resumeAnalysisTaskProperties.getConsumerGroup();
    }

    @Override
    protected String consumerName() {
        return resumeAnalysisTaskProperties.getConsumerName();
    }

    @Override
    protected int batchSize() {
        return resumeAnalysisTaskProperties.getBatchSize();
    }

    @Override
    protected void markProcessing(Map<String, String> body) {
        Long resumeId = parseResumeId(body);
        log.info("Marking resume analysis as processing: resumeId={}", resumeId);
        resumePersistenceService.markAnalysisProcessing(resumeId);
    }

    private Long parseResumeId(Map<String, String> body) {
        String resumeIdValue = body == null
                ? null
                : body.get(AsyncTaskStreamConstants.RESUME_ANALYSIS_RESUME_ID_FIELD);
        if (resumeIdValue == null || resumeIdValue.isBlank()) {
            throw new BusinessException(
                    ErrorCode.RESUME_ANALYSIS_FAILED,
                    "Resume analysis message is missing resumeId");
        }

        try {
            return Long.valueOf(resumeIdValue);
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    ErrorCode.RESUME_ANALYSIS_FAILED,
                    "Resume analysis message contains an invalid resumeId",
                    ex);
        }
    }

    @Override
    protected void handleMessage(StreamMessageId messageId, Map<String, String> body) {
        Long resumeId = parseResumeId(body);
        if (!resumeRepository.existsById(resumeId)) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }
        String resumeText = body.get(AsyncTaskStreamConstants.RESUME_ANALYSIS_RESUME_TEXT_FIELD);
        ResumeAnalysisResponse analysisResult = resumeGradingService.analyzeResume(resumeText);
        //判断简历是否存在
        if (!resumeRepository.existsById(resumeId)) {
            log.warn("简历已被删除，跳过分析任务: resumeId={}", resumeId);
            return;
        }
        resumePersistenceService.saveAnalysisStructureAndComplete(resumeId, analysisResult);
    }

    @Override
    protected void handleMessageFailure(StreamMessageId messageId, Map<String, String> body, Exception failure) {
        Long resumeId = parseResumeId(body);
        log.error("Failed to process resume analysis message: resumeId={}, streamKey={}, messageId={}",
                resumeId, streamKey(), messageId, failure);
        resumePersistenceService.markAnalysisFailed(resumeId, failure.getMessage());
    }

    @Override
    protected void markCompleted(Map<String, String> body) {
        // Analysis structure and COMPLETED status are persisted in one transaction.
    }
}
