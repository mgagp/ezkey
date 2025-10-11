/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * DTO: AdminMfaValidateRequestDto
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to validate MFA and exchange temp token for bearer token.
 */
public class AdminMfaValidateRequestDto {

    @NotBlank
    private String tempToken;

    @NotNull
    private Integer authAttemptId;

    public String getTempToken() {
        return tempToken;
    }

    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }
}



