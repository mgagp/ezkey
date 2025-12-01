/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ECP256KeyPairResponseDto
 * Description: Response DTO containing EC P-256 key pair for simulation purposes.
 */

package org.ezkey.crypto.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing EC P-256 key pair for postman/testing simulation")
public class ECP256KeyPairResponseDto {

  @Schema(
      description = "Base64-encoded EC P-256 private key (PKCS#8 format)",
      example = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg...",
      requiredMode = RequiredMode.REQUIRED)
  private String privateKey;

  @Schema(
      description = "Base64-encoded EC P-256 public key (X.509 SubjectPublicKeyInfo format)",
      example = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
      requiredMode = RequiredMode.REQUIRED)
  private String publicKey;

  public ECP256KeyPairResponseDto(String privateKey, String publicKey) {
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

