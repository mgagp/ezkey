/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: HashTokenRequestDto
 * Description: Request DTO for hashing a bearer token (SHA-256 hex).
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for the hash-token endpoint.
 *
 * <p>Accepts a bearer token (plaintext) and returns its SHA-256 hash in hexadecimal. Used for
 * development and debugging when admin bearer tokens are stored as hashes in the database (e.g. to
 * look up or verify a row in ezkey_admin_tokens).
 *
 * @since 2025
 */
@Schema(description = "Request DTO for hashing a bearer token (SHA-256 hex)")
public class HashTokenRequestDto {

  @Schema(
      description = "Bearer token to hash (e.g. ezkey_... or ezkey_recovery_...)",
      example = "ezkey_a1b2c3d4e5f67890",
      requiredMode = RequiredMode.REQUIRED)
  @NotBlank(message = "Token cannot be blank")
  private String token;

  public HashTokenRequestDto() {}

  public HashTokenRequestDto(String token) {
    this.token = token;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
