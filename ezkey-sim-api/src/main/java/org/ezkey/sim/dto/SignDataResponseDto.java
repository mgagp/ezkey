/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: SignDataResponseDto
 * Description: Response DTO containing signature of signed data.
 */

package org.ezkey.sim.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(description = "Response DTO containing signature of signed data")
public class SignDataResponseDto {

    @Schema(description = "Base64-encoded signature", 
            example = "YTNlNjM0ZjIyNDY5ZDMyOWI3MmE3MzJiNGQ2NWQwNGQyYWM...", 
            requiredMode = RequiredMode.REQUIRED)
    private String signature;

    @Schema(description = "Original data that was signed", 
            example = "Hello, World!", 
            requiredMode = RequiredMode.REQUIRED)
    private String originalData;

    @Schema(description = "Signature algorithm used", 
            example = "SHA256withRSA", 
            requiredMode = RequiredMode.REQUIRED)
    private String algorithm;

    public SignDataResponseDto(String signature, String originalData, String algorithm) {
        this.signature = signature;
        this.originalData = originalData;
        this.algorithm = algorithm;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getOriginalData() {
        return originalData;
    }

    public void setOriginalData(String originalData) {
        this.originalData = originalData;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}