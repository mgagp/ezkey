/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: EventStatus
 * Description: Enumeration of audit event statuses for tracking operation outcomes.
 */

package org.ezkey.audit.domain;

/**
 * Enumeration of audit event statuses.
 *
 * <p>Defines the possible outcomes of audited events for security monitoring and analysis.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum EventStatus {
    /** Event completed successfully */
    SUCCESS,
    
    /** Event failed validation or authorization */
    FAILURE,
    
    /** Event encountered an unexpected error */
    ERROR
}
