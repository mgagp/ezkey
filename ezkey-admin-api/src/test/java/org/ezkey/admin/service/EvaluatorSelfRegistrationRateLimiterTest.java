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
  @DisplayName("blocks second success from same IP within window")
  void blocksSameIp() {
    limiter.verifyAndRecordSuccess("203.0.113.10");
    assertThrows(
        EvaluatorSelfRegistrationCapacityException.class,
        () -> limiter.verifyAndRecordSuccess("203.0.113.10"));
  }
}
