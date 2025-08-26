/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptWaitRequestDto
 * Description: Request DTO for waiting for authentication response in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for waiting for authentication response in admin API.
 * <p>
 * This DTO represents the request parameters for the authentication wait endpoint
 * that allows applications to poll for authentication completion. It provides
 * configurable timeout and polling interval parameters for flexible waiting behavior.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by integrating applications to wait for mobile device
 * responses to authentication requests. This enables synchronous-like behavior
 * in the asynchronous MFA authentication flow.
 * </p>
 *
 * <p>
 * <b>Parameters:</b>
 * <ul>
 * <li><b>timeout:</b> Maximum duration to wait for authentication completion (seconds)</li>
 * <li><b>polling:</b> Interval between status checks during waiting (seconds)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Note:</b> This endpoint is part of the admin API and should only be
 * accessible to authorized applications. The polling mechanism prevents excessive
 * resource consumption while providing responsive authentication status updates.
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
 * @see AuthAttemptDto
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
@Schema(description = "Request parameters for waiting for authentication response")
public class AuthAttemptWaitRequestDto {

    /**
     * Maximum duration to wait for authentication completion in seconds.
     * Default value is 30 seconds. Must be positive and reasonable (1-300 seconds).
     * 
     * <p>
     * <b>Usage:</b> This parameter controls how long the endpoint will wait before
     * returning a timeout response. Longer timeouts allow for slower device responses
     * but consume more server resources.
     * </p>
     */
    @Schema(description = "Maximum wait duration in seconds", 
            example = "30", 
            defaultValue = "30",
            minimum = "1",
            maximum = "300")
    private Integer timeout;

    /**
     * Interval between status checks during waiting in seconds.
     * Default value is 2 seconds. Must be positive and less than timeout.
     * 
     * <p>
     * <b>Usage:</b> This parameter controls how frequently the system checks for
     * authentication completion. Shorter intervals provide faster response times
     * but increase server load.
     * </p>
     */
    @Schema(description = "Polling interval in seconds", 
            example = "2", 
            defaultValue = "2",
            minimum = "1",
            maximum = "60")
    private Integer polling;

    /**
     * Default constructor for AuthAttemptWaitRequestDto.
     * Initializes with default values: timeout=30, polling=2.
     */
    public AuthAttemptWaitRequestDto() {
        this.timeout = 30;
        this.polling = 2;
    }

    /**
     * Constructor with custom timeout and polling values.
     *
     * @param timeout maximum wait duration in seconds
     * @param polling polling interval in seconds
     */
    public AuthAttemptWaitRequestDto(Integer timeout, Integer polling) {
        this.timeout = timeout;
        this.polling = polling;
    }

    /**
     * Gets the maximum wait duration in seconds.
     *
     * @return the timeout value in seconds
     */
    public Integer getTimeout() {
        return timeout;
    }

    /**
     * Sets the maximum wait duration in seconds.
     *
     * @param timeout the timeout value in seconds to set
     */
    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    /**
     * Gets the polling interval in seconds.
     *
     * @return the polling interval in seconds
     */
    public Integer getPolling() {
        return polling;
    }

    /**
     * Sets the polling interval in seconds.
     *
     * @param polling the polling interval in seconds to set
     */
    public void setPolling(Integer polling) {
        this.polling = polling;
    }

}
