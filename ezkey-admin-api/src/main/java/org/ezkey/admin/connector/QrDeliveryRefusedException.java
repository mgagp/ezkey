/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: QrDeliveryRefusedException
 */

package org.ezkey.admin.connector;

/**
 * The core refused a send before calling any connector.
 *
 * @since 2026
 */
public class QrDeliveryRefusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient Reason reason;

  /**
   * Builds the exception.
   *
   * @param reason why the send was refused
   */
  public QrDeliveryRefusedException(Reason reason) {
    super(reason.code());
    this.reason = reason;
  }

  public Reason getReason() {
    return reason;
  }

  /** Why the core refused, with a typed code the Admin UI can translate. */
  public enum Reason {
    CONNECTOR_UNAVAILABLE("connector-unavailable", 503),
    LOCALE_NOT_OFFERED("locale-not-offered", 422),
    ENROLLMENT_NOT_CREATED("enrollment-not-created", 409),
    ENROLLMENT_EXPIRED("enrollment-expired", 409),
    ADMIN_ENROLLMENT("admin-enrollment", 409),
    SELF_REGISTERED_TENANT("self-registered-tenant", 409),
    PARENT_NOT_OPERATIONAL("parent-not-operational", 409),
    CONTACT_EMAIL_MISSING("contact-email-missing", 422),
    CONTACT_EMAIL_INVALID("contact-email-invalid", 422),
    EXPECTED_SERVER_MISSING("expected-server-missing", 422),
    DAILY_CAP_REACHED("daily-cap-reached", 429);

    private final String code;
    private final int httpStatus;

    Reason(String code, int httpStatus) {
      this.code = code;
      this.httpStatus = httpStatus;
    }

    public String code() {
      return code;
    }

    public int httpStatus() {
      return httpStatus;
    }
  }
}
