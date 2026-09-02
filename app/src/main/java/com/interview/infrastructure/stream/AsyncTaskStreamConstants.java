package com.interview.infrastructure.stream;

public final class AsyncTaskStreamConstants {

  public static final String RESUME_ANALYSIS_STREAM_KEY = "resume:analysis:tasks";
  public static final String RESUME_ANALYSIS_CONSUMER_GROUP = "resume-analysis-workers";
  public static final String RESUME_ANALYSIS_CONSUMER_NAME_PREFIX = "resume-analysis-";
  public static final String RESUME_ANALYSIS_RESUME_ID_FIELD = "resumeId";
  public static final String RESUME_ANALYSIS_RESUME_TEXT_FIELD = "resumeText";
  public static final int STREAM_MAX_LEN=1000;
  
  private AsyncTaskStreamConstants() {
  }
}
  