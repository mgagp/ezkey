/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyIntegrationI18nRepository
 * Description: Spring Data JPA repository for EzkeyIntegrationI18n entities.
 */

package org.ezkey.integration.domain.repository;

import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link IntegrationI18n} entities.
 * <p>
 * This repository provides standard CRUD operations for Integration I18n entities
 * and can be extended with custom query methods for language-specific operations.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Entity:</b> EzkeyIntegrationI18n</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface IntegrationI18nRepository extends JpaRepository<IntegrationI18n, Integer> {
    
    // Standard CRUD operations are inherited from JpaRepository:
    // - save(EzkeyIntegrationI18n entity)
    // - findById(Integer id)
    // - findAll()
    // - deleteById(Integer id)
    // - count()
    // - existsById(Integer id)
    // etc.
    
    // Custom query methods can be added here as needed:
    // Example:
    // List<EzkeyIntegrationI18n> findByIntegrationId(Integer integrationId);
    // List<EzkeyIntegrationI18n> findByLanguage(String language);
    // Optional<EzkeyIntegrationI18n> findByIntegrationIdAndLanguage(Integer integrationId, String language);
} 