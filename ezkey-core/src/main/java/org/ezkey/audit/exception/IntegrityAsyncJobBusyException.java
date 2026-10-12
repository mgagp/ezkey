/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrityAsyncJobBusyException
 * Description: Thrown when the global Integrity async slot is already occupied.
 */

package org.ezkey.audit.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.ezkey.audit.asyncjob.IntegrityAsyncJob;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobStatus;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobTrigger;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobType;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate;

/**
 * Thrown when Integrity heavy work cannot start because the async slot or process-local heavy
 * crypto gate is busy (HTTP 409, type {@code integrity-async-job-busy}).
 *
 * @since 2026
 */
public class IntegrityAsyncJobBusyException extends RuntimeException {

  /**
   * Shared operator-facing detail when {@link org.ezkey.audit.integrity.IntegrityHeavyCryptoGate}
   * is held.
   */
  public static final String HEAVY_CRYPTO_BUSY_MESSAGE =
      "Integrity crypto path busy (scheduled or in-process heavy work)";

  private final IntegrityAsyncJob currentJob;
  private final IntegrityHeavyCryptoGate.Holder busyHolder;

  /**
   * Creates the exception with the occupying job.
   *
   * @param currentJob the job currently occupying the slot (or the busy reason surrogate)
   * @param message operator-facing one-liner
   */
  public IntegrityAsyncJobBusyException(IntegrityAsyncJob currentJob, String message) {
    this(currentJob, message, null);
  }

  /**
   * Creates the exception with the occupying job and optional busyHolder.
   *
   * @param currentJob the job currently occupying the slot (or the busy reason surrogate)
   * @param message operator-facing one-liner
   * @param busyHolder who holds the heavy-crypto path (nullable)
   */
  public IntegrityAsyncJobBusyException(
      IntegrityAsyncJob currentJob, String message, IntegrityHeavyCryptoGate.Holder busyHolder) {
    super(message);
    this.currentJob = currentJob;
    this.busyHolder = busyHolder;
  }

  /**
   * Busy refusal when the process-local heavy crypto gate is held (no real async job row).
   *
   * @return exception with a synthetic currentJob surrogate for the existing 409 contract
   */
  public static IntegrityAsyncJobBusyException forHeavyCryptoBusy() {
    return forHeavyCryptoBusy(HEAVY_CRYPTO_BUSY_MESSAGE, null);
  }

  /**
   * Busy refusal when the process-local heavy crypto gate is held.
   *
   * @param holder process-local gate holder (may be null)
   * @return exception with synthetic currentJob and busyHolder
   */
  public static IntegrityAsyncJobBusyException forHeavyCryptoBusy(
      IntegrityHeavyCryptoGate.Holder holder) {
    return forHeavyCryptoBusy(HEAVY_CRYPTO_BUSY_MESSAGE, holder);
  }

  /**
   * Busy refusal when the process-local heavy crypto gate is held (no real async job row).
   *
   * @param message operator-facing one-liner
   * @return exception with a synthetic currentJob surrogate for the existing 409 contract
   */
  public static IntegrityAsyncJobBusyException forHeavyCryptoBusy(String message) {
    return forHeavyCryptoBusy(message, null);
  }

  /**
   * Busy refusal when the process-local heavy crypto gate is held.
   *
   * @param message operator-facing one-liner
   * @param holder process-local gate holder (may be null)
   * @return exception with synthetic currentJob and busyHolder
   */
  public static IntegrityAsyncJobBusyException forHeavyCryptoBusy(
      String message, IntegrityHeavyCryptoGate.Holder holder) {
    IntegrityAsyncJob synthetic = new IntegrityAsyncJob();
    synthetic.setJobId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    synthetic.setJobType(IntegrityAsyncJobType.RUN_VALIDATION);
    synthetic.setStatus(IntegrityAsyncJobStatus.RUNNING);
    synthetic.setTrigger(
        holder == IntegrityHeavyCryptoGate.Holder.NIGHTLY
            ? IntegrityAsyncJobTrigger.NIGHTLY
            : IntegrityAsyncJobTrigger.MANUAL);
    synthetic.setStartedByAdminId(holder == IntegrityHeavyCryptoGate.Holder.NIGHTLY ? null : 0);
    synthetic.setStartedByUsername("system");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    synthetic.setStartedAt(now);
    synthetic.setHeartbeatAt(now);
    synthetic.setResultSummary(message);
    return new IntegrityAsyncJobBusyException(synthetic, message, holder);
  }

  /**
   * Returns the job that occupies the slot.
   *
   * @return current job
   */
  public IntegrityAsyncJob getCurrentJob() {
    return currentJob;
  }

  /**
   * Who holds the heavy-crypto path when known.
   *
   * @return holder or null
   */
  public IntegrityHeavyCryptoGate.Holder getBusyHolder() {
    return busyHolder;
  }
}
