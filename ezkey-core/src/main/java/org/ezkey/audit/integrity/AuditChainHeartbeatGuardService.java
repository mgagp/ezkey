/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditChainHeartbeatGuardService
 * Description: Evaluates checkpoint heartbeat staleness and drives incidents/alerts for peripherals.
 */

package org.ezkey.audit.integrity;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Computes periodic-chain heartbeat supervision for peripheral APIs (Auth API, Integration API).
 *
 * <p>The Admin API advances checkpoints; peripherals compare clock time against {@link
 * AuditChainCheckpoint#getWindowEnd()} plus grace windows and may enter fail-closed mode when the
 * scheduler appears stalled beyond acceptable thresholds.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditChainHeartbeatGuardService {

  /** Dedupe key for singleton heartbeat-stale alerts across an outage episode. */
  public static final String HEARTBEAT_STALE_ALERT_DEDUPE_KEY = "AUDIT_CHAIN_HEARTBEAT_STALE";

  private static final Logger LOG = LoggerFactory.getLogger(AuditChainHeartbeatGuardService.class);

  private static final AuditChainHeartbeatEvaluation ALWAYS_OK =
      new AuditChainHeartbeatEvaluation(AuditChainHeartbeatPhase.OK, null, null, null, null, false);

  private final AuditChainHeartbeatProperties heartbeatProperties;
  private final AuditChainProperties chainProperties;
  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditChainIncidentRepository incidentRepository;
  private final AlertService alertService;

  private final Object incidentMonitor = new Object();

  private volatile AuditChainHeartbeatEvaluation cachedEvaluation;
  private volatile Instant cacheExpiryMonotonic = Instant.EPOCH;

  /** Last supervision phase emitted to application logs (transition-only diagnostics). */
  private volatile AuditChainHeartbeatPhase lastLoggedPhase;

  private OffsetDateTime applicationStartedAt;

  /**
   * Constructs the heartbeat guard service.
   *
   * @param heartbeatProperties heartbeat thresholds and caching
   * @param chainProperties checkpoint window sizing (must match Admin API configuration)
   * @param checkpointRepository checkpoint persistence
   * @param incidentRepository operational incident persistence
   * @param alertService alert subsystem for operator-facing heartbeat degradation signals
   */
  public AuditChainHeartbeatGuardService(
      AuditChainHeartbeatProperties heartbeatProperties,
      AuditChainProperties chainProperties,
      AuditChainCheckpointRepository checkpointRepository,
      AuditChainIncidentRepository incidentRepository,
      AlertService alertService) {
    this.heartbeatProperties = heartbeatProperties;
    this.chainProperties = chainProperties;
    this.checkpointRepository = checkpointRepository;
    this.incidentRepository = incidentRepository;
    this.alertService = alertService;
  }

  @PostConstruct
  void configureHeartbeatDiagnostics() {
    applicationStartedAt = OffsetDateTime.now(ZoneOffset.UTC);
    validateHeartbeatConfiguration();
  }

  private void validateHeartbeatConfiguration() {
    if (!heartbeatProperties.isEnabled()) {
      LOG.info(
          "Audit chain heartbeat supervision is disabled"
              + " (ezkey.audit.chain.heartbeat.enabled=false)");
      return;
    }

    int windowMinutes = chainProperties.getWindowMinutes();
    if (windowMinutes <= 0) {
      LOG.warn(
          "Misconfigured audit-chain heartbeat: ezkey.audit.chain.window-minutes ({}) "
              + "must be positive — peripheral supervision thresholds are unreliable",
          windowMinutes);
      return;
    }

    int graceWindowsConfigured = heartbeatProperties.getGraceWindows();
    int graceWindows = Math.max(1, graceWindowsConfigured);

    Duration stop = heartbeatProperties.getStopBeforeNextWindow();
    if (stop == null || stop.isNegative()) {
      stop = Duration.ofMinutes(1);
    }
    long stopMinutes = Math.max(0L, stop.toMinutes());

    // After latest.window_end, fail-closed threshold is computed as:
    // windowEnd + graceWindows*windowMinutes - stopBeforeNextWindow.
    // Stale (unbounded-risk) supervision starts at windowEnd + windowMinutes.
    // Prefer a strictly positive UNSUPERVISED_ACTIVITY interval: failClosedNotBefore >
    // staleStartsAt.
    boolean suspicious =
        heartbeatThresholdsCollapseUnsupervisedWindow(windowMinutes, graceWindows, stopMinutes);

    if (suspicious) {
      LOG.warn(
          "Audit chain heartbeat thresholds may behave poorly: ezkey.audit.chain.window-minutes={},"
              + " effective grace-windows={}, ezkey.audit.chain.heartbeat.stop-before-next-window"
              + " (~{} minute(s)) leaves little or no unsupervised cushion after first stale"
              + " boundary — inspect ezkey-core CONFIGURATION docs for intended timing semantics",
          windowMinutes,
          graceWindows,
          stopMinutes);
    } else {
      LOG.info(
          "Audit chain heartbeat supervision thresholds: windowMinutes={}, grace-windows={}, "
              + "stop-before-next-window~={} minute(s)",
          windowMinutes,
          graceWindowsConfigured,
          stopMinutes);
    }
  }

  /**
   * Returns a cached heartbeat evaluation with small TTL to limit database load under high QPS.
   *
   * @return current evaluation snapshot
   */
  public AuditChainHeartbeatEvaluation evaluate() {
    if (!heartbeatProperties.isEnabled()) {
      return ALWAYS_OK;
    }

    Instant now = Instant.now();
    AuditChainHeartbeatEvaluation hit = cachedEvaluation;
    if (hit != null && now.isBefore(cacheExpiryMonotonic)) {
      return hit;
    }

    synchronized (this) {
      now = Instant.now();
      hit = cachedEvaluation;
      if (hit != null && now.isBefore(cacheExpiryMonotonic)) {
        return hit;
      }

      AuditChainHeartbeatEvaluation fresh = computeFresh(OffsetDateTime.now(ZoneOffset.UTC));

      synchronized (incidentMonitor) {
        syncIncidentAndAlert(fresh);
      }

      logPhaseTransitionIfNeeded(fresh);

      cachedEvaluation = fresh;
      Duration ttl = heartbeatProperties.getCacheTtl();
      if (ttl == null || ttl.isNegative() || ttl.isZero()) {
        ttl = Duration.ofSeconds(5);
      }
      cacheExpiryMonotonic = Instant.now().plus(ttl);
      return fresh;
    }
  }

  /**
   * Whether peripheral APIs must enforce fail-closed MFA eligibility for new work.
   *
   * @return true when heartbeat supervision requires blocking new peripheral MFA writes
   */
  public boolean shouldFailClosedPeripheralWrites() {
    if (!heartbeatProperties.isEnabled() || !heartbeatProperties.isRequired()) {
      return false;
    }
    return evaluate().peripheralFailClosed();
  }

  private AuditChainHeartbeatEvaluation computeFresh(OffsetDateTime now) {
    int windowMinutes = chainProperties.getWindowMinutes();
    int graceWindows = heartbeatProperties.getGraceWindows();
    if (graceWindows < 1) {
      graceWindows = 1;
    }

    Duration stopBeforeNextWindow = heartbeatProperties.getStopBeforeNextWindow();
    if (stopBeforeNextWindow == null || stopBeforeNextWindow.isNegative()) {
      stopBeforeNextWindow = Duration.ofMinutes(1);
    }

    Optional<AuditChainCheckpoint> latestOpt = checkpointRepository.findLatest();

    if (latestOpt.isEmpty()) {
      Duration sinceStartup = Duration.between(applicationStartedAt.toInstant(), now.toInstant());
      Duration bootstrapGrace = heartbeatProperties.getBootstrapGrace();
      if (bootstrapGrace == null || bootstrapGrace.isNegative()) {
        bootstrapGrace = Duration.ofMinutes(10);
      }

      if (sinceStartup.compareTo(bootstrapGrace) > 0) {
        return new AuditChainHeartbeatEvaluation(
            AuditChainHeartbeatPhase.DEGRADED_SERVICE, null, null, applicationStartedAt, now, true);
      }

      return new AuditChainHeartbeatEvaluation(
          AuditChainHeartbeatPhase.BOOTSTRAPPING, null, null, null, null, false);
    }

    AuditChainCheckpoint latest = latestOpt.get();
    OffsetDateTime windowEnd = latest.getWindowEnd();
    long anchorId = latest.getCheckpointId();

    OffsetDateTime staleStartsAt = windowEnd.plusMinutes(windowMinutes);
    OffsetDateTime outerPhaseEnd = windowEnd.plusMinutes((long) graceWindows * windowMinutes);
    OffsetDateTime failClosedNotBefore = outerPhaseEnd.minus(stopBeforeNextWindow);

    AuditChainHeartbeatPhase phase;
    boolean peripheralFailClosed;

    if (now.isBefore(staleStartsAt)) {
      phase = AuditChainHeartbeatPhase.OK;
      peripheralFailClosed = false;
    } else if (now.isBefore(failClosedNotBefore)) {
      phase = AuditChainHeartbeatPhase.UNSUPERVISED_ACTIVITY;
      peripheralFailClosed = false;
    } else {
      phase = AuditChainHeartbeatPhase.DEGRADED_SERVICE;
      peripheralFailClosed = true;
    }

    return new AuditChainHeartbeatEvaluation(
        phase, anchorId, windowEnd, staleStartsAt, failClosedNotBefore, peripheralFailClosed);
  }

  /**
   * When thresholds leave no slack between staleness onset and earliest fail-closed time,
   * peripherals may jump straight to degraded right after staleness begins.
   */
  static boolean heartbeatThresholdsCollapseUnsupervisedWindow(
      long windowMinutes, int graceWindowsAtLeastOne, long stopBeforeNextWindowMinutes) {
    long outerSpanMinutes = (long) graceWindowsAtLeastOne * windowMinutes;
    long earliestFailClosedOffsetMinutes = outerSpanMinutes - stopBeforeNextWindowMinutes;
    return earliestFailClosedOffsetMinutes <= windowMinutes;
  }

  /**
   * Emits INFO log lines only when the derived supervision phase changes (reduces chatter under TTL
   * cache churn).
   */
  private void logPhaseTransitionIfNeeded(AuditChainHeartbeatEvaluation fresh) {
    AuditChainHeartbeatPhase previous = lastLoggedPhase;
    if (Objects.equals(previous, fresh.phase())) {
      return;
    }
    lastLoggedPhase = fresh.phase();

    LOG.info(
        "Audit chain heartbeat phase transition: {} -> {} (failClosed={}, anchorCheckpointId={}, "
            + "latestWindowEnd={}, stalePhaseStartsAt={}, failClosedNotBefore={})",
        previous == null ? "initial" : previous.name(),
        fresh.phase(),
        fresh.peripheralFailClosed(),
        fresh.anchorCheckpointId(),
        fresh.latestWindowEnd(),
        fresh.stalePhaseStartsAt(),
        fresh.failClosedNotBefore());
  }

  private void syncIncidentAndAlert(AuditChainHeartbeatEvaluation ev) {
    boolean degradedNow = ev.peripheralFailClosed();
    OffsetDateTime clock = OffsetDateTime.now(ZoneOffset.UTC);

    Optional<AuditChainIncident> open =
        incidentRepository.findFirstByStatusOrderByCreatedAtDesc(
            AuditChainIncidentStatus.IN_PROGRESS);

    if (degradedNow) {
      alertService.raiseOrTouch(
          AlertType.AUDIT_CHAIN_HEARTBEAT_STALE,
          AlertSeverity.WARNING,
          HEARTBEAT_STALE_ALERT_DEDUPE_KEY,
          buildAlertPayload(ev));

      if (open.isEmpty()) {
        AuditChainIncident incident = new AuditChainIncident();
        incident.setStatus(AuditChainIncidentStatus.IN_PROGRESS);
        incident.setAnchorCheckpointId(ev.anchorCheckpointId());
        incident.setStaleSince(ev.stalePhaseStartsAt() != null ? ev.stalePhaseStartsAt() : clock);
        incident.setDegradedSince(clock);
        incidentRepository.save(incident);
      }
      return;
    }

    if (open.isPresent()) {
      AuditChainIncident incident = open.get();
      incident.setStatus(AuditChainIncidentStatus.RECOVERED_PENDING_DECLARATION);
      incident.setRecoveredAt(clock);
      incidentRepository.save(incident);

      alertService.resolveByDedupeKey(
          HEARTBEAT_STALE_ALERT_DEDUPE_KEY, AlertResolutionReason.HEARTBEAT_RESTORED, null);
    }
  }

  private static String buildAlertPayload(AuditChainHeartbeatEvaluation ev) {
    String anchor =
        ev.anchorCheckpointId() == null ? "null" : Long.toString(ev.anchorCheckpointId());
    String windowEnd =
        ev.latestWindowEnd() == null ? "" : ev.latestWindowEnd().toString().replace("\"", "\\\"");
    return "{\"phase\":\""
        + ev.phase().name()
        + "\",\"anchorCheckpointId\":"
        + anchor
        + ",\"latestWindowEnd\":\""
        + windowEnd
        + "\"}";
  }
}
