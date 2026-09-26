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

import org.ezkey.audit.asyncjob.IntegrityAsyncJob;

/**
 * Thrown when a start is refused because the global Integrity async slot is occupied (HTTP 409).
 *
 * @since 2026
 */
public class IntegrityAsyncJobBusyException extends RuntimeException {

  private final IntegrityAsyncJob currentJob;

  /**
   * Creates the exception with the occupying job.
   *
   * @param currentJob the job currently occupying the slot (or the busy reason surrogate)
   * @param message operator-facing one-liner
   */
  public IntegrityAsyncJobBusyException(IntegrityAsyncJob currentJob, String message) {
    super(message);
    this.currentJob = currentJob;
  }

  /**
   * Returns the job that occupies the slot.
   *
   * @return current job
   */
  public IntegrityAsyncJob getCurrentJob() {
    return currentJob;
  }
}
