/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpRateLimitServiceWrapper
 * Description: Extends RateLimitService and delegates to NoOpRateLimitService.
 */

package org.ezkey.m2m.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.m2m.config.ApiKeyRateLimitProperties;

/**
 * Wrapper that extends {@link RateLimitService} and delegates all calls to {@link
 * NoOpRateLimitService}.
 *
 * <p>This allows Spring to satisfy {@code RateLimitService} injection points when rate limiting is
 * disabled, without requiring null checks in controllers.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class NoOpRateLimitServiceWrapper extends RateLimitService {

  private final NoOpRateLimitService noOpService;

  /**
   * Constructs the wrapper, delegating to a {@link NoOpRateLimitService}.
   *
   * @param properties rate limiting properties (required by parent constructor)
   * @param meterRegistry metrics registry
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
