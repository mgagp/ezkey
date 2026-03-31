/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: NoOpAdminOperationsRateLimitServiceWrapper
 * Description: Wrapper that extends AdminOperationsRateLimitService and delegates to NoOp implementation.
 */

package org.ezkey.admin.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.ezkey.admin.config.AdminOperationsRateLimitProperties;

/**
 * Wrapper that extends AdminOperationsRateLimitService and delegates to NoOp implementation.
 *
 * <p>This class allows Spring to inject AdminOperationsRateLimitService when rate limiting is
 * disabled, by extending the real service class and delegating all calls to the NoOp
 * implementation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class NoOpAdminOperationsRateLimitServiceWrapper extends AdminOperationsRateLimitService {

  private final NoOpAdminOperationsRateLimitService noOpService;

  /**
   * Constructs the wrapper with NoOp service delegation.
   *
   * @param properties the rate limiting properties (required by parent constructor)
   * @param meterRegistry the metrics registry
   */
  public NoOpAdminOperationsRateLimitServiceWrapper(
      AdminOperationsRateLimitProperties properties, MeterRegistry meterRegistry) {
    super(properties, meterRegistry);
    this.noOpService = new NoOpAdminOperationsRateLimitService(meterRegistry);
  }

  @Override
  public boolean canCreateApiKey(String adminId) {
    return noOpService.canCreateApiKey(adminId);
  }

  @Override
  public boolean canResetEnrollment(String tokenOrAdminId) {
    return noOpService.canResetEnrollment(tokenOrAdminId);
  }

  @Override
  public void recordCreateApiKey(String adminId) {
    noOpService.recordCreateApiKey(adminId);
  }

  @Override
  public boolean canRevokeApiKey(String adminId) {
    return noOpService.canRevokeApiKey(adminId);
  }

  @Override
  public void recordRevokeApiKey(String adminId) {
    noOpService.recordRevokeApiKey(adminId);
  }

  @Override
  public boolean canUpdateApiKey(String adminId) {
    return noOpService.canUpdateApiKey(adminId);
  }

  @Override
  public void recordUpdateApiKey(String adminId) {
    noOpService.recordUpdateApiKey(adminId);
  }

  @Override
  public void recordResetEnrollment(String tokenOrAdminId) {
    noOpService.recordResetEnrollment(tokenOrAdminId);
  }
}
