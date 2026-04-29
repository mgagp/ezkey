/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: AuditChainHeartbeatEvaluateTicker
 * Description: Periodically evaluates peripheral heartbeat supervision so incidents sync without traffic.
 */

package org.ezkey.admin.scheduling;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically pings {@link AuditChainHeartbeatGuardService} so heartbeat transitions emit
 * incidents and alerts even when peripheral APIs are idle.
 *
 * <p>Runs only in Admin API where scheduling is enabled.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Component
@ConditionalOnProperty(
    prefix = "ezkey.audit.chain.heartbeat",
    name = "admin-evaluate-scheduler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditChainHeartbeatEvaluateTicker {

  private final AuditChainHeartbeatGuardService guardService;

  /**
   * Constructs the ticker.
   *
   * @param guardService heartbeat evaluation entry point (shared core bean)
   */
  public AuditChainHeartbeatEvaluateTicker(AuditChainHeartbeatGuardService guardService) {
    this.guardService = guardService;
  }

  /** Evaluates heartbeat supervision at a fixed delay between completions. */
  @Scheduled(
      fixedDelayString = "${ezkey.audit.chain.heartbeat.admin-evaluate-fixed-delay-ms:30000}")
  public void evaluateHeartbeat() {
    guardService.evaluate();
  }
}
