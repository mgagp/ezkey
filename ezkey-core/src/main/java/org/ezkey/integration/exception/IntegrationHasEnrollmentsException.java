/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: IntegrationHasEnrollmentsException
 * Description: Thrown when attempting to delete an integration that has one or more enrollments.
 */

package org.ezkey.integration.exception;

/**
 * Exception thrown when attempting to delete an integration that still has one or more enrollments.
 *
 * <p>This exception is raised during integration deletion if the integration is referenced by at
 * least one enrollment. Enrollments must be removed or revoked before the integration can be
 * deleted.
 *
 * <p><b>Handling:</b> This exception should be caught and mapped to an HTTP 409 Conflict status
 * code by the domain exception handler.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SuppressWarnings("serial")
public class IntegrationHasEnrollmentsException extends RuntimeException {

  private static final String MESSAGE =
      "Cannot delete integration: it has one or more enrollments. Remove or revoke enrollments"
          + " first.";

  /** Constructs a new IntegrationHasEnrollmentsException with the default message. */
  public IntegrationHasEnrollmentsException() {
    super(MESSAGE);
  }
}
