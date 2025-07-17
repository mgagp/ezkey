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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.mapper.IntegrationServiceMapper;
import org.springframework.stereotype.Service;

/**
 * Service for managing {@link Integration} entities and their I18n
 * children.
 * <p>
 * This service provides CRUD operations for Integration entities using Spring
 * Data JPA. It ensures proper handling of the bidirectional relationship
 * between Integration and I18n.
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
public class IntegrationService {

    private final IntegrationRepository integrationRepository;

    private final IntegrationServiceMapper integrationServiceMapper;

    /**
     * Constructs the service with the required repository and mapper.
     *
     * @param integrationRepository the repository for Integration entities
     * @param integrationServiceMapper the mapper for converting between DTOs and
     * entities
     */
    public IntegrationService(IntegrationRepository integrationRepository,IntegrationServiceMapper integrationServiceMapper){
        this.integrationRepository = integrationRepository;
        this.integrationServiceMapper = integrationServiceMapper;
    }

    /**
     * Retrieves an Integration by its unique identifier.
     *
     * @param id the unique identifier of the Integration
     * @return an Optional containing the Integration if found, or empty if not
     * found
     */
    public Optional<Integration> getById(Integer id){
        return integrationRepository.findById(id);
    }

    /**
     * Retrieves all Integration entities.
     *
     * @return a list of all Integration entities
     */
    public List<Integration> getAll(){
        return integrationRepository.findAll();
    }

    /**
     * Creates a new Integration entity from a request.
     *
     * @param request the request containing integration data
     * @return the created and saved Integration entity
     */
    public IntegrationCreateResponse createIntegration(IntegrationCreateRequest request){
        Integration integration = integrationServiceMapper.toEntity(request);
        integration.setActive(true);
        integration.setCreatedAt(LocalDateTime.now());

        // Set the parent reference for each i18n if present
        if (integration.getI18n() != null){
            integration.getI18n().forEach(i18n -> i18n.setIntegration(integration));
        }
        var domResponse = integrationRepository.save(integration);
        IntegrationCreateResponse response = integrationServiceMapper.toCreateResponse(domResponse);
        return response;
    }

    /**
     * Deletes an Integration entity by its unique identifier.
     *
     * @param id the unique identifier of the Integration to delete
     */
    public void delete(Integer id){
        integrationRepository.deleteById(id);
    }

}