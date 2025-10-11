/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminTempTokenTxHelper
 * Description: Transaction helper for admin temporary token operations requiring independent transactions.
 */

package org.ezkey.admin.service;

import org.ezkey.integration.domain.repository.AdminTempTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaction helper service for admin temporary token operations.
 * <p>
 * This service provides methods that require independent transaction management,
 * particularly for operations that must commit regardless of the calling method's
 * transaction outcome (e.g., invalidating temp tokens even when MFA is rejected).
 * </p>
 *
 * <p>
 * <b>Transaction Management:</b>
 * All methods use REQUIRES_NEW propagation to ensure they execute in a separate
 * transaction that commits independently of the caller's transaction state.
 * </p>
 *
 * <p>
 * <b>Pattern:</b>
 * This follows the same pattern as {@link org.ezkey.enrollment.service.EnrollmentTxHelper}
 * for handling operations that must commit even when the caller's transaction rolls back.
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
public class AdminTempTokenTxHelper {

    private static final Logger logger = LoggerFactory.getLogger(AdminTempTokenTxHelper.class);

    private final AdminTempTokenRepository tempTokenRepository;

    /**
     * Constructs the transaction helper with required dependencies.
     *
     * @param tempTokenRepository the repository for temp token operations
     */
    public AdminTempTokenTxHelper(AdminTempTokenRepository tempTokenRepository) {
        this.tempTokenRepository = tempTokenRepository;
    }

    /**
     * Invalidate a temporary token in an independent transaction.
     * <p>
     * This method commits immediately regardless of the calling method's transaction state.
     * This ensures temp tokens are invalidated even when the caller throws an exception
     * (e.g., MFA rejected, expired, or timed out).
     * </p>
     *
     * <p>
     * <b>Transaction Behavior:</b>
     * Uses REQUIRES_NEW propagation to create a new transaction that commits independently.
     * Even if the calling method's transaction rolls back (e.g., due to SecurityException),
     * this invalidation will persist in the database.
     * </p>
     *
     * @param tempToken the temporary token to invalidate
     * @param username the username for logging purposes
     * @return number of tokens invalidated (should be 1 if successful, 0 if not found)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int invalidateTempToken(String tempToken, String username) {
        int invalidated = tempTokenRepository.invalidateTempToken(tempToken);
        logger.debug("🔒 Temp token invalidated for admin: {} (rows affected: {})", username, invalidated);
        return invalidated;
    }
}

