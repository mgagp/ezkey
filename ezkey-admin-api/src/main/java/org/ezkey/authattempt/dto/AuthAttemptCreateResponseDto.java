/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponseDto
 * Description: Response DTO for authorization attempt creation in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for authentication attempt creation in admin API.
 *
 * <p>This DTO represents the response data returned when an authentication attempt is successfully
 * created through the admin API. It contains the created attempt's ID.
 *
 * <p><b>Usage Context:</b> Returned by admin API when creating authentication requests.
 *
 * <p><b>Core Fields:</b>
 *
 * <ul>
 *   <li><b>authAttemptId:</b> Unique identifier of the created authentication attempt
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateResponse
 * @see AuthAttemptCreateRequestDto
 */
@Schema(description = "Response DTO containing created authentication attempt details")
public class AuthAttemptCreateResponseDto {

  /**
   * Unique identifier of the created authentication attempt. Used to reference this attempt in
   * subsequent operations.
   */
  @Schema(description = "Unique identifier of the created authentication attempt", example = "11")
  private Integer authAttemptId;

  /**
   * Gets the authentication attempt ID.
   *
   * @return the unique identifier of the created authentication attempt
   */
  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  /**
   * Sets the authentication attempt ID.
   *
   * @param authAttemptId the unique identifier of the created authentication attempt to set
   */
  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }
}
