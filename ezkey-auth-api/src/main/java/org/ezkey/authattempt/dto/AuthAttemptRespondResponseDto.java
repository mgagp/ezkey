/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptRespondResponseDto
 * Description: Response DTO for authentication attempt submissions in auth API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for authentication attempt submissions in auth API.
 * <p>
 * This DTO represents the response data returned to mobile devices after they
 * submit their authentication attempt response. It provides confirmation of
 * the submission status and any relevant feedback message for the mobile app
 * to display to the user.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by auth-api when mobile devices submit
 * authentication responses. Provides immediate feedback on whether the
 * response was successfully processed and recorded by the system.
 * </p>
 *
 * <p>
 * <b>Response Handling:</b> The mobile app should check the success flag
 * to determine if the authentication response was accepted. The message
 * field provides additional context for error handling or user feedback.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>success:</b> Indicates whether the response submission was successful</li>
 * <li><b>message:</b> Additional information or error details</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondResponse
 * @see AuthAttemptRespondRequestDto
 */
@Schema(description = "Response DTO for authentication attempt submissions")
public class AuthAttemptRespondResponseDto {

    /**
     * Indicates whether the authentication response submission was successful.
     * <p>
     * True means the response was accepted and processed by the system,
     * false indicates an error occurred during processing. Mobile apps
     * should check this flag to determine next actions.
     * </p>
     */
    @Schema(description = "Whether the response submission was successful", 
            example = "true", 
            required = true)
    private Boolean success;

    /**
     * Additional message providing context about the submission result.
     * <p>
     * Contains success confirmation or detailed error information for
     * user feedback. Mobile apps can display this message to inform
     * users about the authentication attempt status.
     * </p>
     */
    @Schema(description = "Success confirmation or error details for user feedback", 
            example = "Authentication response processed successfully", 
            required = true)
    private String message;

    /**
     * Gets the success status of the response submission.
     *
     * @return true if successful, false if there was an error
     */
    public Boolean getSuccess(){
        return success;
    }

    /**
     * Sets the success status of the response submission.
     *
     * @param success true if successful, false if there was an error
     */
    public void setSuccess(Boolean success){
        this.success = success;
    }

    /**
     * Gets the response message.
     *
     * @return the response message
     */
    public String getMessage(){
        return message;
    }

    /**
     * Sets the response message.
     *
     * @param message the response message to set
     */
    public void setMessage(String message){
        this.message = message;
    }
}