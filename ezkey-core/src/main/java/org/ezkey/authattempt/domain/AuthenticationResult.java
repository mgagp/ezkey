package org.ezkey.authattempt.domain;

/**
 * Enum representing the result of an authentication attempt response.
 * <p>
 * Provides clear, unambiguous states for authentication results:
 * - APPROVED: User approved the authentication
 * - DENIED: User denied the authentication  
 * - FAILED: Technical error occurred
 * - EXPIRED: Authentication attempt expired before response
 * </p>
 */
public enum AuthenticationResult {
    APPROVED,
    DENIED, 
    FAILED,
    EXPIRED
}
