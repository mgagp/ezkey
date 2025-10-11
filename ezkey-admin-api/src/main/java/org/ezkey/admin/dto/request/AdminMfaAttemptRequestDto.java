/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * DTO: AdminMfaAttemptRequestDto
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to create an MFA attempt from a temporary token.
 */
public class AdminMfaAttemptRequestDto {

    @NotBlank
    private String tempToken;

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }
}



