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

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.mapper.IntegrationServiceMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing {@link Integration} entities and their I18n children.
 *
 * <p>This service provides CRUD operations for Integration entities using Spring Data JPA. It
 * ensures proper handling of the bidirectional relationship between Integration and I18n.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
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
   * @param integrationServiceMapper the mapper for converting between DTOs and entities
   */
  public IntegrationService(
      IntegrationRepository integrationRepository,
      IntegrationServiceMapper integrationServiceMapper) {
    this.integrationRepository = integrationRepository;
    this.integrationServiceMapper = integrationServiceMapper;
  }

  /**
   * Retrieves an Integration by its unique identifier.
   *
   * @param id the unique identifier of the Integration
   * @return an Optional containing the Integration if found, or empty if not found
   */
  public Optional<Integration> getById(Integer id) {
    return integrationRepository.findById(id);
  }

  /**
   * Retrieves all Integration entities.
   *
   * @return a list of all Integration entities
   */
  public List<Integration> getAll() {
    return integrationRepository.findAll();
  }

  /**
   * Searches integrations with optional filters and pagination.
   *
   * <p>This method supports multi-criteria search for administrative and operational purposes. All
   * filter parameters are optional - if null, they are ignored in the query. Results are ordered by
   * creation date descending (newest first) by default.
   *
   * <p><b>Use Case:</b> Administrators managing integrations, searching for specific applications,
   * and compliance reporting.
   *
   * @param integrationName optional integration name filter (partial match, case-insensitive via
   *     i18n)
   * @param active optional active flag filter
   * @param createdAfter optional start of date range filter
   * @param createdBefore optional end of date range filter
   * @param pageable pagination and sorting parameters
   * @return page of integrations matching criteria
   */
  @Transactional(readOnly = true)
  public Page<Integration> findByFilters(
      String integrationName,
      Boolean active,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Pageable pageable) {

    Specification<Integration> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Filter by name via i18n join (partial match, case-insensitive)
          if (integrationName != null && !integrationName.isBlank()) {
            var i18nJoin = root.join("i18n");
            predicates.add(
                cb.like(cb.lower(i18nJoin.get("name")), "%" + integrationName.toLowerCase() + "%"));
            // Ensure distinct results due to join
            query.distinct(true);
          }

          if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
          }

          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }

          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
          }

          // Exclude system integrations from normal listing
          predicates.add(
              cb.or(
                  cb.isNull(root.get("isSystemIntegration")),
                  cb.equal(root.get("isSystemIntegration"), false)));

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return integrationRepository.findAll(spec, pageable);
  }

  /**
   * Creates a new Integration entity from a request.
   *
   * @param request the request containing integration data
   * @return the created and saved Integration entity
   */
  public IntegrationCreateResponse createIntegration(IntegrationCreateRequest request) {
    Integration integration = integrationServiceMapper.toEntity(request);
    integration.setActive(true);
    integration.setCreatedAt(OffsetDateTime.now());

    // Set the parent reference for each i18n if present
    if (integration.getI18n() != null) {
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
  public void delete(Integer id) {
    integrationRepository.deleteById(id);
  }
}
