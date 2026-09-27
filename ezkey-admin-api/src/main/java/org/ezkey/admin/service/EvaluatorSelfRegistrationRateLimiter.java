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
 */
@Service
public class EvaluatorSelfRegistrationRateLimiter {

  private final EvaluatorSelfRegistrationProperties properties;

  private volatile LocalDate currentUtcDay = LocalDate.now(ZoneOffset.UTC);
  private final AtomicInteger dailySuccessCount = new AtomicInteger(0);

  private final Cache<String, AtomicInteger> ipSuccessCounts;

  /**
   * Creates the rate limiter.
   *
   * @param properties self-registration daily and per-IP limits
   */
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
    if (dailySuccessCount.get() >= properties.getDailyCap()) {
      throw new EvaluatorSelfRegistrationCapacityException();
    }
    String ipKey = normalizeIpKey(clientIp);
    int perIpMax = Math.max(1, properties.getPerIpMaxSuccess());
    if (ipKey != null) {
      AtomicInteger ipCount = ipSuccessCounts.get(ipKey, _ -> new AtomicInteger(0));
      if (ipCount.get() >= perIpMax) {
        throw new EvaluatorSelfRegistrationCapacityException();
      }
      ipCount.incrementAndGet();
    }
    dailySuccessCount.incrementAndGet();
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
}
