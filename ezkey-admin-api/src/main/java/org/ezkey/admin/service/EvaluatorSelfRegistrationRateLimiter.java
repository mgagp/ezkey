/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EvaluatorSelfRegistrationRateLimiter
 * Description: In-memory rate limits for anonymous evaluator self-registration.
 */
package org.ezkey.admin.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.exception.EvaluatorSelfRegistrationCapacityException;
import org.springframework.stereotype.Service;

/**
 * Applies global daily and per-IP success limits for evaluator self-registration.
 *
 * <p>In-memory counters are acceptable for single-node EXP1 / community preview instances.
 * Onboarding-resume redeem shares the Admin login/activate rate-limit bucket (not this limiter).
 *
 * <p>Per-IP capacity honors {@code ezkey.evaluator.self-registration.per-ip-max-success} via a real
 * counter (not a Boolean one-shot marker).
 */
@Service
public class EvaluatorSelfRegistrationRateLimiter {

  private final EvaluatorSelfRegistrationProperties properties;

  private volatile LocalDate currentUtcDay = LocalDate.now(ZoneOffset.UTC);
  private final AtomicInteger dailySuccessCount = new AtomicInteger(0);

  private final Cache<String, AtomicInteger> ipSuccessCounts;

  public EvaluatorSelfRegistrationRateLimiter(EvaluatorSelfRegistrationProperties properties) {
    this.properties = properties;
    this.ipSuccessCounts =
        Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(Math.max(1, properties.getPerIpWindowHours())))
            .maximumSize(10_000)
            .build();
  }

  /**
   * Verifies limits and records a successful signup.
   *
   * @param clientIp normalized client IP from {@link org.ezkey.audit.util.ClientContext}
   * @throws EvaluatorSelfRegistrationCapacityException when a limit is reached
   */
  public void verifyAndRecordSuccess(String clientIp) {
    resetDailyCounterIfNeeded();
    int dailyCap = Math.max(0, properties.getDailyCap());
    if (dailySuccessCount.get() >= dailyCap) {
      throw new EvaluatorSelfRegistrationCapacityException();
    }

    String ipKey = normalizeIpKey(clientIp);
    int maxPerIp = Math.max(0, properties.getPerIpMaxSuccess());
    if (ipKey != null) {
      if (maxPerIp <= 0) {
        throw new EvaluatorSelfRegistrationCapacityException();
      }
      AtomicInteger ipCount = ipSuccessCounts.get(ipKey, _ -> new AtomicInteger(0));
      // Increment first so concurrent callers cannot both pass a stale read.
      int nextIpCount = ipCount.incrementAndGet();
      if (nextIpCount > maxPerIp) {
        ipCount.decrementAndGet();
        throw new EvaluatorSelfRegistrationCapacityException();
      }
    }

    int nextDaily = dailySuccessCount.incrementAndGet();
    if (nextDaily > dailyCap) {
      dailySuccessCount.decrementAndGet();
      if (ipKey != null) {
        AtomicInteger ipCount = ipSuccessCounts.getIfPresent(ipKey);
        if (ipCount != null) {
          ipCount.decrementAndGet();
        }
      }
      throw new EvaluatorSelfRegistrationCapacityException();
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

  /**
   * Returns the current per-IP success count within the window (for tests).
   *
   * @param clientIp client IP key
   * @return recorded successes, or {@code 0} when absent
   */
  int currentIpSuccessCount(String clientIp) {
    String ipKey = normalizeIpKey(clientIp);
    if (ipKey == null) {
      return 0;
    }
    AtomicInteger count = ipSuccessCounts.getIfPresent(ipKey);
    return count == null ? 0 : count.get();
  }

  private static String normalizeIpKey(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return null;
    }
    return clientIp.trim();
  }
}
