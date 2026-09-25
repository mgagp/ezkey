/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrityAsyncJobAbandonNotAllowedException
 * Description: Thrown when abandon is refused for a healthy RUNNING job or wrong status.
 */

package org.ezkey.audit.exception;

import org.ezkey.audit.asyncjob.IntegrityAsyncJobStatus;

/**
 * Thrown when abandon/free-slot is refused (e.g. healthy RUNNING).
 *
 * @since 2026
 */
public class IntegrityAsyncJobAbandonNotAllowedException extends RuntimeException {

  private final IntegrityAsyncJobStatus status;

  /**
   * Creates the exception.
   *
   * @param status current job status
   * @param message operator-facing detail
   */
  public IntegrityAsyncJobAbandonNotAllowedException(
      IntegrityAsyncJobStatus status, String message) {
    super(message);
    this.status = status;
  }

  /**
   * Returns the status that blocked abandon.
   *
   * @return job status
   */
  public IntegrityAsyncJobStatus getStatus() {
    return status;
  }
}
