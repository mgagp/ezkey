/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EzkeyIntegrationService
 * Description: Service layer for managing Integration entities and their I18n children using Spring Data JPA.
 */

package org.ezkey.integration.service;

import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.domain.entity.EzkeyIntegrationI18n;
import org.ezkey.integration.domain.repository.EzkeyIntegrationRepository;
import org.ezkey.integration.domain.repository.EzkeyIntegrationI18nRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link EzkeyIntegration} entities and their I18n children.
 * <p>
 * This service provides CRUD operations for Integration entities using Spring Data JPA.
 * It ensures proper handling of the bidirectional relationship between Integration and I18n.
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EzkeyIntegrationService {
	
	private final EzkeyIntegrationRepository integrationRepository;

	/**
	 * Constructs the service with the required repository.
	 *
	 * @param integrationRepository the repository for Integration entities
	 * @param i18nRepository the repository for I18n entities (not used directly here)
	 */
	@Autowired
	public EzkeyIntegrationService(EzkeyIntegrationRepository integrationRepository, EzkeyIntegrationI18nRepository i18nRepository) {
		this.integrationRepository = integrationRepository;
	}

	/**
	 * Retrieves an Integration by its unique identifier.
	 *
	 * @param id the unique identifier of the Integration
	 * @return an Optional containing the Integration if found, or empty if not found
	 */
	public Optional<EzkeyIntegration> getById(Integer id) {
		return integrationRepository.findById(id);
	}

	/**
	 * Retrieves all Integration entities.
	 *
	 * @return a list of all Integration entities
	 */
	public List<EzkeyIntegration> getAll() {
		return integrationRepository.findAll();
	}

	/**
	 * Saves or updates an Integration entity, ensuring proper parent reference for I18n children.
	 *
	 * @param integration the Integration entity to save or update
	 * @return the saved Integration entity
	 */
	public EzkeyIntegration save(EzkeyIntegration integration) {
		if (integration.getI18n() != null) {
			for (EzkeyIntegrationI18n i18n : integration.getI18n()) {
				i18n.setIntegration(integration);
			}
		}
		return integrationRepository.save(integration);
	}

	/**
	 * Deletes an Integration entity by its unique identifier.
	 *
	 * @param id the unique identifier of the Integration to delete
	 */
	public void delete(Integer id) {
		integrationRepository.deleteById(id);
	}

} 