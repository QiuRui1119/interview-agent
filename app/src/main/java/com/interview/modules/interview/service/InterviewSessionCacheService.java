package com.interview.modules.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.infrastructure.redis.RedisService;
import com.interview.modules.interview.model.InterviewSessionCache;
import com.interview.modules.interview.model.InterviewSessionStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionCacheService {

  private static final String STATE_KEY_PREFIX = "interview:session:";
  private static final String STATE_KEY_SUFFIX = ":state";
  public static final String TURNS_KEY_PATTERN = "interview:session:%s:turns";
  private static final Duration ACTIVE_TTL = Duration.ofHours(2);
  private static final Duration PAUSED_TTL = Duration.ofHours(24);

  private final RedisService redisService;
  private final ObjectMapper objectMapper;

  public void put(InterviewSessionCache cache) {
    if (cache == null || cache.sessionId() == null || cache.sessionId().isBlank()) {
      throw new IllegalArgumentException("Interview session cache must contain sessionId");
    }
    try {
      InterviewSessionCache existing = get(cache.sessionId());
      if (existing != null && existing.version() != null && cache.version() != null
          && existing.version() > cache.version()) {
        log.debug("Skip stale interview cache update: sessionId={}, cachedVersion={}, newVersion={}",
            cache.sessionId(), existing.version(), cache.version());
        return;
      }
      if (cache.status() == InterviewSessionStatus.COMPLETED
          || cache.status() == InterviewSessionStatus.EVALUATED) {
        delete(cache.sessionId());
        return;
      }
      redisService.setString(key(cache.sessionId()), writeJson(cache), ttl(cache.status()));
    } catch (Exception ex) {
      log.warn("Failed to write interview session cache: sessionId={}", cache.sessionId(), ex);
    }
  }

  public InterviewSessionCache get(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return null;
    }
    try {
      String json = redisService.getString(key(sessionId));
      return json == null ? null : readJson(json);
    } catch (Exception ex) {
      log.warn("Failed to read interview session cache: sessionId={}", sessionId, ex);
      return null;
    }
  }

  public void delete(String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      try {
        redisService.delete(key(sessionId));
      } catch (Exception ex) {
        log.warn("Failed to delete interview session cache: sessionId={}", sessionId, ex);
      }
    }
  }

  private static String key(String sessionId) {
    return STATE_KEY_PREFIX + sessionId + STATE_KEY_SUFFIX;
  }

  private static Duration ttl(InterviewSessionStatus status) {
    return status == InterviewSessionStatus.READY
        || status == InterviewSessionStatus.IN_PROGRESS
        || status == InterviewSessionStatus.GENERATING
        || status == InterviewSessionStatus.CREATED
        ? ACTIVE_TTL : PAUSED_TTL;
  }

  private String writeJson(InterviewSessionCache cache) {
    try {
      return objectMapper.writeValueAsString(cache);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize interview session cache", ex);
    }
  }

  private InterviewSessionCache readJson(String json) {
    try {
      return objectMapper.readValue(json, InterviewSessionCache.class);
    } catch (JsonProcessingException ex) {
      log.warn("Invalid interview session cache JSON, treating as cache miss", ex);
      return null;
    }
  }
}
