/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorSelfRegistrationRateLimiterTest
 * Description: Unit tests for anonymous evaluator signup rate limits.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.exception.EvaluatorSelfRegistrationCapacityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EvaluatorSelfRegistrationRateLimiter")
class EvaluatorSelfRegistrationRateLimiterTest {

  private EvaluatorSelfRegistrationProperties properties;
  private EvaluatorSelfRegistrationRateLimiter limiter;

  @BeforeEach
  void setUp() {
    properties = new EvaluatorSelfRegistrationProperties();
    properties.setDailyCap(2);
    properties.setPerIpWindowHours(24);
    properties.setPerIpMaxSuccess(1);
    limiter = new EvaluatorSelfRegistrationRateLimiter(properties);
  }

  @Test
  @DisplayName("allows successes up to daily cap")
  void allowsDailyCap() {
    limiter.verifyAndRecordSuccess("203.0.113.1");
    limiter.verifyAndRecordSuccess("203.0.113.2");
    assertEquals(2, limiter.currentDailySuccessCount());
    assertThrows(
        EvaluatorSelfRegistrationCapacityException.class,
        () -> limiter.verifyAndRecordSuccess("203.0.113.3"));
  }

  @Test
  @DisplayName("blocks second success from same IP when perIpMaxSuccess=1")
  void blocksSameIpWhenMaxOne() {
    limiter.verifyAndRecordSuccess("203.0.113.10");
    assertEquals(1, limiter.currentIpSuccessCount("203.0.113.10"));
    assertThrows(
        EvaluatorSelfRegistrationCapacityException.class,
        () -> limiter.verifyAndRecordSuccess("203.0.113.10"));
  }

  @Test
  @DisplayName("honors perIpMaxSuccess=3 (2nd/3rd OK, 4th capacity)")
  void honorsPerIpMaxSuccessThree() {
    properties.setDailyCap(20);
    properties.setPerIpMaxSuccess(3);
    limiter = new EvaluatorSelfRegistrationRateLimiter(properties);

    limiter.verifyAndRecordSuccess("198.51.100.7");
    limiter.verifyAndRecordSuccess("198.51.100.7");
    limiter.verifyAndRecordSuccess("198.51.100.7");
    assertEquals(3, limiter.currentIpSuccessCount("198.51.100.7"));
    assertEquals(3, limiter.currentDailySuccessCount());

    assertThrows(
        EvaluatorSelfRegistrationCapacityException.class,
        () -> limiter.verifyAndRecordSuccess("198.51.100.7"));
    assertEquals(3, limiter.currentIpSuccessCount("198.51.100.7"));
    assertEquals(3, limiter.currentDailySuccessCount());
  }

  @Test
  @DisplayName("daily cap still honored when per-IP headroom remains")
  void dailyCapHonoredWithPerIpHeadroom() {
    properties.setDailyCap(2);
    properties.setPerIpMaxSuccess(5);
    limiter = new EvaluatorSelfRegistrationRateLimiter(properties);

    limiter.verifyAndRecordSuccess("203.0.113.50");
    limiter.verifyAndRecordSuccess("203.0.113.50");
    assertEquals(2, limiter.currentDailySuccessCount());
    assertThrows(
        EvaluatorSelfRegistrationCapacityException.class,
        () -> limiter.verifyAndRecordSuccess("203.0.113.50"));
    assertEquals(2, limiter.currentIpSuccessCount("203.0.113.50"));
  }
}
