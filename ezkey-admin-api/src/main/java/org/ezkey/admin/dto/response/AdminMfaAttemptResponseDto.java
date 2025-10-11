/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * DTO: AdminMfaAttemptResponseDto
 */

package org.ezkey.admin.dto.response;

/**
 * Response containing created auth attempt identifier for MFA.
 */
public class AdminMfaAttemptResponseDto {

    private Integer authAttemptId;

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }
}


