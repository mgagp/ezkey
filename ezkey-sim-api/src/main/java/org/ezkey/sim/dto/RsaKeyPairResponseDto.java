/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: RsaKeyPairResponseDto
 * Description: Response DTO containing RSA key pair for simulation purposes.
 */

package org.ezkey.sim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing RSA key pair for postman/testing simulation")
public class RsaKeyPairResponseDto {

    @Schema(description = "Base64-encoded PKCS#8 private key", 
            example = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...", 
            requiredMode = RequiredMode.REQUIRED)
    private String privateKey;

    @Schema(description = "Base64-encoded X.509 public key", 
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuGb...", 
            requiredMode = RequiredMode.REQUIRED)
    private String publicKey;

    @Schema(description = "RSA key size in bits", 
            example = "2048", 
            requiredMode = RequiredMode.REQUIRED)
    private int keySize;

    public RsaKeyPairResponseDto(String privateKey, String publicKey, int keySize) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.keySize = keySize;
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

    public int getKeySize() {
        return keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }
}