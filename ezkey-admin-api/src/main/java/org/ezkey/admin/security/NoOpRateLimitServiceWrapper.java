/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpRateLimitServiceWrapper
 * Description: Wrapper that extends RateLimitService and delegates to NoOp implementation.
 */

package org.ezkey.admin.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.admin.config.ApiKeyRateLimitProperties;

/**
 * Wrapper that extends RateLimitService and delegates to NoOp implementation.
 *
 * <p>This class allows Spring to inject RateLimitService when rate limiting is disabled, by
 * extending the real service class and delegating all calls to the NoOp implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class NoOpRateLimitServiceWrapper extends RateLimitService {

  private final NoOpRateLimitService noOpService;

  /**
   * Constructs the wrapper with NoOp service delegation.
   *
   * @param properties the rate limiting properties (required by parent constructor)
   * @param meterRegistry the metrics registry
   */
  public NoOpRateLimitServiceWrapper(
      ApiKeyRateLimitProperties properties, MeterRegistry meterRegistry) {
    super(properties, meterRegistry);
    this.noOpService = new NoOpRateLimitService(meterRegistry);
  }

  @Override
  public boolean canCreateAuthAttempt(String apiKeyId) {
    return noOpService.canCreateAuthAttempt(apiKeyId);
  }

  @Override
  public boolean canWaitAuthAttempt(String apiKeyId) {
    return noOpService.canWaitAuthAttempt(apiKeyId);
  }

  @Override
  public void recordCreateAuthAttempt(String apiKeyId) {
    noOpService.recordCreateAuthAttempt(apiKeyId);
  }

  @Override
  public void recordWaitAuthAttempt(String apiKeyId) {
    noOpService.recordWaitAuthAttempt(apiKeyId);
  }
}
