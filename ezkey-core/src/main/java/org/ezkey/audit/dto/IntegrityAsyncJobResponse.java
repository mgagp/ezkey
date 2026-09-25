/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrityAsyncJobResponse
 * Description: Status and result summary for an Integrity async job (no secrets).
 */

package org.ezkey.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.ezkey.audit.integrity.IntegrityAsyncJob;
import org.ezkey.audit.integrity.IntegrityAsyncJobStatus;
import org.ezkey.audit.integrity.IntegrityAsyncJobType;

/**
 * Operator-visible Integrity async job status (no HMAC secrets or raw crypto material).
 *
 * @param jobId opaque job UUID
 * @param type job kind
 * @param status lifecycle status
 * @param startedByUsername who started the job
 * @param startedByAdminId admin id of starter
 * @param startedAt start timestamp
 * @param heartbeatAt last heartbeat
 * @param finishedAt finish timestamp when terminal
 * @param scopeFrom inclusive window start
 * @param scopeTo exclusive window end
 * @param resultSummary short safe summary
 * @param errorSummary truncated failure reason when failed/expired
 * @param intact result intact flag when available
 * @param alertId alert id when validation raised one
 * @param abandonedAt when the slot was abandoned
 * @param resumeOneLiner short line for 409 / banner resume
 * @since 2026
 */
@Schema(description = "Integrity async job status and result summary (non-secret).")
public record IntegrityAsyncJobResponse(
    UUID jobId,
    IntegrityAsyncJobType type,
    IntegrityAsyncJobStatus status,
    String startedByUsername,
    Integer startedByAdminId,
    OffsetDateTime startedAt,
    OffsetDateTime heartbeatAt,
    OffsetDateTime finishedAt,
    OffsetDateTime scopeFrom,
    OffsetDateTime scopeTo,
    String resultSummary,
    String errorSummary,
    Boolean intact,
    Long alertId,
    OffsetDateTime abandonedAt,
    String resumeOneLiner) {

  /**
   * Maps an entity to the API response.
   *
   * @param job persisted job
   * @return response DTO
   */
  public static IntegrityAsyncJobResponse from(IntegrityAsyncJob job) {
    return new IntegrityAsyncJobResponse(
        job.getJobId(),
        job.getJobType(),
        job.getStatus(),
        job.getStartedByUsername(),
        job.getStartedByAdminId(),
        job.getStartedAt(),
        job.getHeartbeatAt(),
        job.getFinishedAt(),
        job.getScopeFrom(),
        job.getScopeTo(),
        job.getResultSummary(),
        job.getErrorSummary(),
        job.getResultIntact(),
        job.getResultAlertId(),
        job.getAbandonedAt(),
        buildResumeOneLiner(job));
  }

  private static String buildResumeOneLiner(IntegrityAsyncJob job) {
    return job.getJobType()
        + " · "
        + job.getStatus()
        + " · started by "
        + job.getStartedByUsername()
        + " at "
        + job.getStartedAt();
  }
}
