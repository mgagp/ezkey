/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EvaluatorSelfRegistrationRateLimiter
 * Description: In-memory rate limits for anonymous evaluator self-registration and re-issue.
 */
package org.ezkey.admin.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.exception.EvaluatorSelfRegistrationCapacityException;
import org.springframework.stereotype.Service;

/**
 * Applies global daily and per-IP success limits for evaluator self-registration, plus separate
 * per-IP / per-username hourly limits for onboarding re-issue.
 *
 * <p>In-memory counters are acceptable for single-node EXP1 preview instances.
 */
@Service
public class EvaluatorSelfRegistrationRateLimiter {

  private final EvaluatorSelfRegistrationProperties properties;

  private volatile LocalDate currentUtcDay = LocalDate.now(ZoneOffset.UTC);
  private final AtomicInteger dailySuccessCount = new AtomicInteger(0);

  private final Cache<String, Boolean> ipSuccessMarkers;
  private final Cache<String, AtomicInteger> reissueIpCounters;
  private final Cache<String, AtomicInteger> reissueUsernameCounters;

  public EvaluatorSelfRegistrationRateLimiter(EvaluatorSelfRegistrationProperties properties) {
    this.properties = properties;
    this.ipSuccessMarkers =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(Math.max(1, properties.getPerIpWindowHours())))
            .maximumSize(10_000)
            .build();
    this.reissueIpCounters =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();
    this.reissueUsernameCounters =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10_000).build();
  }

  /**
   * Verifies limits and records a successful signup.
   *
   * @param clientIp normalized client IP from {@link org.ezkey.audit.util.ClientContext}
   * @throws EvaluatorSelfRegistrationCapacityException when a limit is reached
   */
  public void verifyAndRecordSuccess(String clientIp) {
    resetDailyCounterIfNeeded();
    if (dailySuccessCount.get() >= properties.getDailyCap()) {
      throw new EvaluatorSelfRegistrationCapacityException();
    }
    String ipKey = normalizeIpKey(clientIp);
    if (ipKey != null && ipSuccessMarkers.getIfPresent(ipKey) != null) {
      throw new EvaluatorSelfRegistrationCapacityException();
    }
    dailySuccessCount.incrementAndGet();
    if (ipKey != null) {
      ipSuccessMarkers.put(ipKey, Boolean.TRUE);
    }
  }

  /**
   * Verifies and records a successful onboarding re-issue (separate from signup caps).
   *
   * @param clientIp client IP for per-IP hourly budget
   * @param username evaluator username for per-username hourly budget
   * @throws EvaluatorSelfRegistrationCapacityException when a re-issue limit is reached
   */
  public void verifyAndRecordReissue(String clientIp, String username) {
    String ipKey = normalizeIpKey(clientIp);
    if (ipKey != null) {
      AtomicInteger ipCount = reissueIpCounters.get(ipKey, _ -> new AtomicInteger(0));
      if (ipCount.incrementAndGet() > Math.max(1, properties.getReissuePerIpMaxPerHour())) {
        throw new EvaluatorSelfRegistrationCapacityException();
      }
    }
    String userKey = normalizeUsernameKey(username);
    if (userKey != null) {
      AtomicInteger userCount = reissueUsernameCounters.get(userKey, _ -> new AtomicInteger(0));
      if (userCount.incrementAndGet() > Math.max(1, properties.getReissuePerUsernameMaxPerHour())) {
        throw new EvaluatorSelfRegistrationCapacityException();
      }
    }
  }

  /** Resets the daily counter when the UTC day rolls over (visible for tests). */
  void resetDailyCounterIfNeeded() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (!today.equals(currentUtcDay)) {
      synchronized (this) {
        if (!today.equals(currentUtcDay)) {
          currentUtcDay = today;
          dailySuccessCount.set(0);
        }
      }
    }
  }

  /** Returns the current daily success count (for tests). */
  int currentDailySuccessCount() {
    resetDailyCounterIfNeeded();
    return dailySuccessCount.get();
  }

  private static String normalizeIpKey(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return null;
    }
    return clientIp.trim();
  }

  private static String normalizeUsernameKey(String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return username.trim().toLowerCase(Locale.ROOT);
  }
}
