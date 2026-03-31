/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: HashTokenResponseDto
 * Description: Response DTO for the hash-token endpoint.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for the hash-token endpoint.
 *
 * <p>Returns the SHA-256 hash (64 hexadecimal characters) of the provided token. This value matches
 * what is stored in ezkey_admin_tokens.bearer_token_hash for lookup and debugging.
 *
 * @since 2025
 */
@Schema(description = "Response containing the SHA-256 hash (hex) of the token")
public class HashTokenResponseDto {

  @Schema(
      description = "SHA-256 hash of the token in lowercase hexadecimal (64 characters)",
      example = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
  private String tokenHash;

  public HashTokenResponseDto() {}

  public HashTokenResponseDto(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }
}
