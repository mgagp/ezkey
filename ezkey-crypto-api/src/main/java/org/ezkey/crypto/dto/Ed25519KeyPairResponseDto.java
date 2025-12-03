/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: Ed25519KeyPairResponseDto
 * Description: Response DTO containing Ed25519 key pair for simulation purposes.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing Ed25519 key pair for postman/testing simulation")
public class Ed25519KeyPairResponseDto {

  @Schema(
      description = "Base64-encoded Ed25519 private key seed (32 bytes raw)",
      example = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      requiredMode = RequiredMode.REQUIRED)
  private String privateKey;

  @Schema(
      description = "Base64-encoded Ed25519 public key (32 bytes raw)",
      example = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      requiredMode = RequiredMode.REQUIRED)
  private String publicKey;

  public Ed25519KeyPairResponseDto(String privateKey, String publicKey) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }
}
