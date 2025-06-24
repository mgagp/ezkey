/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyIntegrationRepository
 * Description: Spring Data JPA repository for EzkeyIntegration entities.
 */

package org.ezkey.integration.domain.repository;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link EzkeyIntegration} entities.
 * <p>
 * This repository provides standard CRUD operations for Integration entities
 * and can be extended with custom query methods as needed.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Entity:</b> EzkeyIntegration</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface EzkeyIntegrationRepository extends JpaRepository<EzkeyIntegration, Integer> {
    
    // Standard CRUD operations are inherited from JpaRepository:
    // - save(EzkeyIntegration entity)
    // - findById(Integer id)
    // - findAll()
    // - deleteById(Integer id)
    // - count()
    // - existsById(Integer id)
    // etc.
    
    // Custom query methods can be added here as needed:
    // Example:
    // List<EzkeyIntegration> findByActiveTrue();
    // Optional<EzkeyIntegration> findByCode(String code);
} 