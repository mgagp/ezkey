/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ValidateSignatureRequestDto
 * Description: Request DTO for validating a signature against data and public key.
 */

package org.ezkey.sim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request DTO for validating a signature against data and public key")
public class ValidateSignatureRequestDto {

    @Schema(description = "Original data that was signed", 
            example = "Hello, World!", 
            requiredMode = RequiredMode.REQUIRED)
    @NotBlank(message = "Data cannot be blank")
    private String data;

    @Schema(description = "Base64-encoded signature to validate", 
            example = "YTNlNjM0ZjIyNDY5ZDMyOWI3MmE3MzJiNGQ2NWQwNGQyYWM...", 
            requiredMode = RequiredMode.REQUIRED)
    @NotBlank(message = "Signature cannot be blank")
    private String signature;

    @Schema(description = "Base64-encoded X.509 public key", 
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuGb...", 
            requiredMode = RequiredMode.REQUIRED)
    @NotBlank(message = "Public key cannot be blank")
    private String publicKey;

    public ValidateSignatureRequestDto() {
    }

    public ValidateSignatureRequestDto(String data, String signature, String publicKey) {
        this.data = data;
        this.signature = signature;
        this.publicKey = publicKey;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}