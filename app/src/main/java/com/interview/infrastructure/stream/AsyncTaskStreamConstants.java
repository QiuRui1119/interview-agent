package com.interview.infrastructure.stream;

public final class AsyncTaskStreamConstants {

  public static final String RESUME_ANALYSIS_STREAM_KEY = "resume:analysis:tasks";
  public static final String RESUME_ANALYSIS_CONSUMER_GROUP = "resume-analysis-workers";
  public static final String RESUME_ANALYSIS_CONSUMER_NAME_PREFIX = "resume-analysis-";
  public static final String RESUME_ANALYSIS_RESUME_ID_FIELD = "resumeId";
  public static final String RESUME_ANALYSIS_RESUME_TEXT_FIELD = "resumeText";
  public static final String KB_VECTORIZE_STREAM_KEY = "knowledgebase:vectorize:tasks";
  public static final String KB_VECTORIZE_GROUP = "knowledgebase-vectorize-workers";
  public static final String FIELD_KB_ID = "knowledgeBaseId";
  public static final String INTERVIEW_GENERATION_STREAM_KEY = "interview:generation:tasks";
  public static final String INTERVIEW_GENERATION_GROUP = "interview-generation-workers";
  public static final String FIELD_INTERVIEW_SESSION_ID = "sessionId";
  public static final int STREAM_MAX_LEN=1000;
  
  private AsyncTaskStreamConstants() {
  }
}
  
