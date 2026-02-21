/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitRequestDto
 * Description: Request parameters for waiting on an authentication attempt response.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request parameters controlling the wait behaviour for an authentication attempt response.
 *
 * <p>Used via query parameters in the wait endpoint. Controls the maximum duration to wait and the
 * polling interval used internally by the service. Providing sensible defaults allows callers to
 * omit either or both parameters for typical use-cases.
 *
 * <p>Shared across admin-api and m2m-api.
 *
 * <p><b>Defaults:</b> {@code timeout = 30} seconds, {@code polling = 2} seconds.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param timeout maximum number of seconds to wait for a device response (1–300, default 30)
 * @param polling interval in seconds between status checks (1–60, default 2)
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Parameters controlling the wait behaviour for an authentication response")
public record AuthAttemptWaitRequestDto(
    /** Maximum number of seconds to wait before returning (1–300). */
    @NotNull(message = "Timeout cannot be null")
        @Min(value = 1, message = "Timeout must be at least 1 second")
        @Max(value = 300, message = "Timeout cannot exceed 300 seconds")
        @Schema(
            description = "Maximum wait duration in seconds",
            example = "30",
            defaultValue = "30")
        Integer timeout,
    /** Interval in seconds between consecutive status checks (1–60). */
    @NotNull(message = "Polling cannot be null")
        @Min(value = 1, message = "Polling interval must be at least 1 second")
        @Max(value = 60, message = "Polling interval cannot exceed 60 seconds")
        @Schema(description = "Polling interval in seconds", example = "2", defaultValue = "2")
        Integer polling) {

  /**
   * Compact constructor — enforces the cross-field invariant: polling must be strictly less than
   * timeout. This logic belongs here (not in Bean Validation) because it involves two fields.
   *
   * <p><b>Note for future refactoring:</b> custom {@code message=} values on the Bean Validation
   * annotations above are tested verbatim by {@code AuthAttemptWaitRequestDtoTest}. Do not replace
   * them with default messages (e.g. when simplifying annotations) without updating those tests.
   */
  public AuthAttemptWaitRequestDto {
    if (timeout != null && polling != null && polling >= timeout) {
      throw new IllegalArgumentException("Polling interval must be less than timeout duration");
    }
  }

  /**
   * Creates a wait request with default values (timeout=30, polling=2).
   *
   * <p>Used when no explicit parameters are provided via query string.
   */
  public AuthAttemptWaitRequestDto() {
    this(30, 2);
  }
}
