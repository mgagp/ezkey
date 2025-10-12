/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: LogoRepository
 * Description: Spring Data JPA repository for Logo entity operations.
 */

package org.ezkey.integration.domain.repository;

import java.util.Optional;

import org.ezkey.integration.domain.entity.Logo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Logo entity operations.
 * <p>
 * This repository provides CRUD operations and custom queries for managing
 * logos in the Ezkey system. Logos can be referenced by integrations to
 * provide consistent branding across the application.
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
@Repository
public interface LogoRepository extends JpaRepository<Logo, Integer> {

    /**
     * Finds a logo by its unique name.
     *
     * @param name the logo name
     * @return Optional containing the logo if found, empty otherwise
     */
    Optional<Logo> findByName(String name);

    /**
     * Checks if a logo with the given name exists.
     *
     * @param name the logo name
     * @return true if a logo with the name exists, false otherwise
     */
    boolean existsByName(String name);
}
