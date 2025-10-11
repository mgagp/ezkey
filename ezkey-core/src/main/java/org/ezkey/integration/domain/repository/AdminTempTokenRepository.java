/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AdminTempTokenRepository
 * Description: Spring Data repository for temporary admin MFA tokens.
 */

package org.ezkey.integration.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.entity.AdminTempToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AdminTempToken} persistence operations.
 *
 * @since 2025
 */
@Repository
public interface AdminTempTokenRepository extends JpaRepository<AdminTempToken, Integer> {

    /**
     * Find active temp token by token string.
     *
     * @param tempToken token string
     * @return optional temp token
     */
    Optional<AdminTempToken> findByTempTokenAndActiveTrue(String tempToken);

    /**
     * Find all active temp tokens for an admin.
     *
     * @param adminId admin identifier
     * @return active temp tokens
     */
    List<AdminTempToken> findByAdminAdminIdAndActiveTrue(Integer adminId);

    /**
     * Invalidate a specific temp token by token string.
     * <p>
     * Sets active=false for the temp token matching the given token string.
     * Used for single-use temp token invalidation during MFA flows.
     * </p>
     *
     * @param tempToken token string to invalidate
     * @return count updated (should be 1 if token exists and is active, 0 otherwise)
     */
    @Modifying
    @Query("UPDATE AdminTempToken t SET t.active = false WHERE t.tempToken = :tempToken AND t.active = true")
    int invalidateTempToken(@Param("tempToken") String tempToken);

    /**
     * Deactivate expired temp tokens.
     *
     * @param cutoff cutoff timestamp
     * @return count updated
     */
    @Modifying
    @Query("UPDATE AdminTempToken t SET t.active = false WHERE t.expiresAt < :cutoff AND t.active = true")
    int deactivateExpiredTokens(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete expired and inactive temp tokens.
     *
     * @param cutoff cutoff timestamp
     * @return count deleted
     */
    @Modifying
    @Query("DELETE FROM AdminTempToken t WHERE t.expiresAt < :cutoff AND t.active = false")
    int deleteExpiredTokens(@Param("cutoff") LocalDateTime cutoff);
}



