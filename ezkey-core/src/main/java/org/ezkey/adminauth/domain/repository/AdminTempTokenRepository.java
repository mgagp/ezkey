/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AdminTempTokenRepository
 * Description: Spring Data JPA repository for AdminTempToken entity operations.
 */

package org.ezkey.adminauth.domain.repository;

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
 * Spring Data JPA repository for AdminTempToken entity operations.
 * <p>
 * This repository provides data access methods for managing temporary tokens
 * used during the MFA authentication flow in the Ezkey system. It includes
 * methods for finding tokens by various criteria and managing token lifecycle.
 * </p>
 *
 * <p>
 * <b>MFA Token Operations:</b>
 * <ul>
 * <li><b>MFA Flow:</b> Find tokens by temp token string for MFA verification</li>
 * <li><b>Administrator Tokens:</b> Find all temp tokens for a specific administrator</li>
 * <li><b>Token Cleanup:</b> Find and remove expired temporary tokens</li>
 * <li><b>Security:</b> Find active temp tokens for security monitoring</li>
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
 * @see AdminTempToken
 */
@Repository
public interface AdminTempTokenRepository extends JpaRepository<AdminTempToken, Integer> {

    /**
     * Finds a temporary token by its token string.
     * <p>
     * This method is used during the MFA authentication flow to find
     * the temporary token record by its token string.
     * </p>
     *
     * @param tempToken the temporary token string
     * @return Optional containing the token if found, empty otherwise
     */
    Optional<AdminTempToken> findByTempToken(String tempToken);

    /**
     * Finds an active temporary token by its token string.
     * <p>
     * This method is used during the MFA authentication flow to find
     * only active temporary tokens by their token string.
     * </p>
     *
     * @param tempToken the temporary token string
     * @return Optional containing the active token if found, empty otherwise
     */
    Optional<AdminTempToken> findByTempTokenAndActiveTrue(String tempToken);

    /**
     * Finds all temporary tokens for a specific administrator.
     * <p>
     * This method is used to find all temporary tokens belonging to a specific
     * administrator for token management and security purposes.
     * </p>
     *
     * @param adminId the ID of the administrator
     * @return list of temporary tokens belonging to the administrator
     */
    List<AdminTempToken> findByAdminAdminId(Integer adminId);

    /**
     * Finds active temporary tokens for a specific administrator.
     * <p>
     * This method is used to find only active temporary tokens belonging to a specific
     * administrator for security monitoring and token management.
     * </p>
     *
     * @param adminId the ID of the administrator
     * @return list of active temporary tokens belonging to the administrator
     */
    List<AdminTempToken> findByAdminAdminIdAndActiveTrue(Integer adminId);

    /**
     * Finds temporary tokens that require MFA.
     * <p>
     * This method is used to find temporary tokens that require MFA
     * verification for security monitoring and management.
     * </p>
     *
     * @return list of temporary tokens that require MFA
     */
    List<AdminTempToken> findByMfaRequiredTrue();

    /**
     * Finds active temporary tokens that require MFA.
     * <p>
     * This method is used to find only active temporary tokens that require MFA
     * verification for security monitoring.
     * </p>
     *
     * @return list of active temporary tokens that require MFA
     */
    List<AdminTempToken> findByMfaRequiredTrueAndActiveTrue();

    /**
     * Finds temporary tokens that do not require MFA.
     * <p>
     * This method is used to find temporary tokens that do not require MFA
     * verification for security monitoring and management.
     * </p>
     *
     * @return list of temporary tokens that do not require MFA
     */
    List<AdminTempToken> findByMfaRequiredFalse();

    /**
     * Finds active temporary tokens that do not require MFA.
     * <p>
     * This method is used to find only active temporary tokens that do not require MFA
     * verification for security monitoring.
     * </p>
     *
     * @return list of active temporary tokens that do not require MFA
     */
    List<AdminTempToken> findByMfaRequiredFalseAndActiveTrue();

    /**
     * Finds expired temporary tokens.
     * <p>
     * This method is used for token cleanup to find temporary tokens that have
     * expired and should be removed from the system.
     * </p>
     *
     * @param currentTime the current timestamp
     * @return list of expired temporary tokens
     */
    List<AdminTempToken> findByExpiresAtBefore(LocalDateTime currentTime);

    /**
     * Finds expired active temporary tokens.
     * <p>
     * This method is used for token cleanup to find active temporary tokens that have
     * expired and should be deactivated.
     * </p>
     *
     * @param currentTime the current timestamp
     * @return list of expired active temporary tokens
     */
    List<AdminTempToken> findByExpiresAtBeforeAndActiveTrue(LocalDateTime currentTime);

    /**
     * Finds temporary tokens created before a specific time.
     * <p>
     * This method is used for token cleanup to find temporary tokens that were
     * created before a specific time and may need to be cleaned up.
     * </p>
     *
     * @param createdBefore the timestamp before which tokens were created
     * @return list of temporary tokens created before the specified time
     */
    List<AdminTempToken> findByCreatedAtBefore(LocalDateTime createdBefore);

    /**
     * Deactivates expired temporary tokens.
     * <p>
     * This method is used for token cleanup to deactivate temporary tokens that have
     * expired, marking them as inactive for security purposes.
     * </p>
     *
     * @param currentTime the current timestamp
     * @return the number of temporary tokens deactivated
     */
    @Modifying
    @Query("UPDATE AdminTempToken t SET t.active = false WHERE t.expiresAt < :currentTime AND t.active = true")
    int deactivateExpiredTempTokens(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Deactivates all temporary tokens for a specific administrator.
     * <p>
     * This method is used for security purposes to deactivate all temporary tokens
     * belonging to a specific administrator, effectively canceling their MFA flow.
     * </p>
     *
     * @param adminId the ID of the administrator
     * @return the number of temporary tokens deactivated
     */
    @Modifying
    @Query("UPDATE AdminTempToken t SET t.active = false WHERE t.admin.adminId = :adminId AND t.active = true")
    int deactivateTempTokensByAdmin(@Param("adminId") Integer adminId);

    /**
     * Counts active temporary tokens for a specific administrator.
     * <p>
     * This method is used for security monitoring to count how many
     * active temporary tokens a specific administrator has.
     * </p>
     *
     * @param adminId the ID of the administrator
     * @return the count of active temporary tokens for the administrator
     */
    long countByAdminAdminIdAndActiveTrue(Integer adminId);

    /**
     * Counts temporary tokens that require MFA.
     * <p>
     * This method is used for security monitoring to count how many
     * temporary tokens require MFA verification.
     * </p>
     *
     * @return the count of temporary tokens that require MFA
     */
    long countByMfaRequiredTrue();

    /**
     * Counts active temporary tokens that require MFA.
     * <p>
     * This method is used for security monitoring to count how many
     * active temporary tokens require MFA verification.
     * </p>
     *
     * @return the count of active temporary tokens that require MFA
     */
    long countByMfaRequiredTrueAndActiveTrue();

    /**
     * Counts expired temporary tokens.
     * <p>
     * This method is used for system monitoring to count how many
     * temporary tokens have expired and need cleanup.
     * </p>
     *
     * @param currentTime the current timestamp
     * @return the count of expired temporary tokens
     */
    long countByExpiresAtBefore(LocalDateTime currentTime);

    /**
     * Counts temporary tokens created before a specific time.
     * <p>
     * This method is used for system monitoring to count how many
     * temporary tokens were created before a specific time.
     * </p>
     *
     * @param createdBefore the timestamp before which tokens were created
     * @return the count of temporary tokens created before the specified time
     */
    long countByCreatedAtBefore(LocalDateTime createdBefore);
}
