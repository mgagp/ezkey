/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminAuthAttemptTxHelper
 * Description: Transaction helper for creating auth attempts in independent transactions.
 */

package org.ezkey.admin.service;

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction helper for creating auth attempts in independent transactions.
 * <p>
 * This service ensures that auth attempts are created and committed to the database
 * in a separate transaction before the passwordless authentication flow begins waiting.
 * This is critical because AuthAttemptWaitService uses NOT_SUPPORTED propagation and
 * needs to see the committed auth attempt.
 * </p>
 * <p>
 * <b>Transaction Flow:</b>
 * <ol>
 * <li>Parent transaction: AdminAuthService.authenticatePasswordless() [@Transactional]</li>
 * <li>Helper transaction: createAuthAttempt() [REQUIRES_NEW - commits immediately]</li>
 * <li>No transaction: AuthAttemptWaitService.waitForResponse() [NOT_SUPPORTED]</li>
 * </ol>
 * </p>
 * <p>
 * Without this helper, the wait service cannot see the auth attempt because the parent
 * transaction hasn't committed yet, and NOT_SUPPORTED propagation suspends the parent.
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
 */
@Service
public class AdminAuthAttemptTxHelper {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuthAttemptTxHelper.class);

    private final AuthAttemptService authAttemptService;

    public AdminAuthAttemptTxHelper(AuthAttemptService authAttemptService) {
        this.authAttemptService = authAttemptService;
    }

    /**
     * Create auth attempt in a new transaction that commits immediately.
     * <p>
     * This method creates an authentication attempt in a REQUIRES_NEW transaction
     * that commits before returning. This ensures the auth attempt is visible to
     * the waitForResponse() method which uses NOT_SUPPORTED propagation.
     * </p>
     *
     * @param request the auth attempt creation request
     * @return the created auth attempt response with ID and challenge
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuthAttemptCreateResponse createAuthAttempt(AuthAttemptCreateRequest request) {
        logger.debug("🔧 Creating auth attempt in independent transaction (enrollment: {})", 
            request.getEnrollmentId());
        
        AuthAttemptCreateResponse response = authAttemptService.create(request);
        
        logger.debug("✅ Auth attempt created and committed (ID: {}, challenge: {})", 
            response.getAuthAttemptId(), response.getAuthAttemptChallenge());
        
        // Transaction commits here when method returns
        return response;
    }
}

