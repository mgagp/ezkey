/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interceptor: AuditChainHeartbeatPeripheralInterceptor
 * Description: Fail-closed gate for peripheral MFA endpoints when checkpoint heartbeat is stalled.
 */

package org.ezkey.audit.integrity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks selected peripheral endpoints when periodic audit-chain checkpoints appear stalled beyond
 * configured grace thresholds.
 *
 * <p>Registered only from Auth API and Integration API Spring MVC configurations — not Admin API.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuditChainHeartbeatPeripheralInterceptor implements HandlerInterceptor {

  private static final Logger LOG =
      LoggerFactory.getLogger(AuditChainHeartbeatPeripheralInterceptor.class);

  private final AuditChainHeartbeatGuardService guardService;

  /**
   * Constructs the interceptor with the shared heartbeat guard service.
   *
   * @param guardService heartbeat evaluation entry point
   */
  public AuditChainHeartbeatPeripheralInterceptor(AuditChainHeartbeatGuardService guardService) {
    this.guardService = guardService;
  }

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {

    if (!guardService.shouldFailClosedPeripheralWrites()) {
      return true;
    }

    if (!"POST".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String path = normalizePath(request.getServletPath());

    boolean blockAuthPending = "/api/v1/auth-attempts/pending".equals(path);
    boolean blockIntegrationCreate = "/api/v1/auth-attempts".equals(path);

    if (!blockAuthPending && !blockIntegrationCreate) {
      return true;
    }

    AuditChainHeartbeatEvaluation diagnostic = guardService.evaluate();
    LOG.warn(
        "Peripheral MFA request blocked — audit-chain heartbeat fail-closed (path={}, phase={},"
            + " anchorCheckpointId={}, latestWindowEnd={}, stalePhaseStartsAt={},"
            + " failClosedNotBefore={}, "
            + "problemType=https://ezkey.io/problems/system/audit-chain-heartbeat-degraded)",
        path,
        diagnostic.phase(),
        diagnostic.anchorCheckpointId(),
        diagnostic.latestWindowEnd(),
        diagnostic.stalePhaseStartsAt(),
        diagnostic.failClosedNotBefore());

    throw new AuditChainHeartbeatDegradedException();
  }

  private static String normalizePath(String servletPath) {
    if (servletPath.length() > 1 && servletPath.endsWith("/")) {
      return servletPath.substring(0, servletPath.length() - 1);
    }
    return servletPath;
  }
}
