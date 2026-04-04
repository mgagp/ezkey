/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.SystemTenantNotConfiguredException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.integration.exception.IntegrationCodeAlreadyExistsException;
import org.ezkey.integration.exception.IntegrationCreateValidationException;
import org.ezkey.integration.exception.IntegrationHasEnrollmentsException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.ezkey.integration.exception.SystemIntegrationLifecycleException;
import org.ezkey.integration.mapper.IntegrationServiceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class IntegrationService {

  private static final Logger logger = LoggerFactory.getLogger(IntegrationService.class);

  private final IntegrationRepository integrationRepository;
  private final IntegrationServiceMapper integrationServiceMapper;
  private final TenantRepository tenantRepository;
  private final EnrollmentRepository enrollmentRepository;

  /**
   * Constructs the service with the required repository and mapper.
   *
   * @param integrationRepository the repository for Integration entities
   * @param integrationServiceMapper the mapper for converting between DTOs and entities
   * @param tenantRepository the repository for Tenant entities
   * @param enrollmentRepository the repository for Enrollment entities (used for delete guard)
   */
  public IntegrationService(
      IntegrationRepository integrationRepository,
      IntegrationServiceMapper integrationServiceMapper,
      TenantRepository tenantRepository,
      EnrollmentRepository enrollmentRepository) {
    this.integrationRepository = integrationRepository;
    this.integrationServiceMapper = integrationServiceMapper;
    this.tenantRepository = tenantRepository;
    this.enrollmentRepository = enrollmentRepository;
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
   * <p><b>Tenant Scoping:</b> If tenantId is provided (non-null), results are filtered to only
   * include integrations belonging to that tenant. This enables tenant isolation for TenantAdmins
   * while allowing GlobalAdmins to see all integrations (by passing null).
   *
   * <p><b>Use Case:</b> Administrators managing integrations, searching for specific applications,
   * and compliance reporting.
   *
   * @param integrationName optional integration name filter (partial match, case-insensitive on
   *     integration_name)
   * @param lifecycleStatus optional exact lifecycle filter
   * @param includeRetired when true, default exclusion of retired integrations is disabled
   * @param createdAfter optional start of date range filter
   * @param createdBefore optional end of date range filter
   * @param tenantId optional tenant ID filter for tenant scoping (null = all tenants, for
   *     GlobalAdmin)
   * @param pageable pagination and sorting parameters
   * @return page of integrations matching criteria
   */
  @Transactional(readOnly = true)
  public Page<Integration> findByFilters(
      String integrationName,
      IntegrationLifecycleStatus lifecycleStatus,
      boolean includeRetired,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Integer tenantId,
      Pageable pageable) {

    Specification<Integration> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Filter by name (partial match, case-insensitive)
          if (integrationName != null && !integrationName.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("name")), "%" + integrationName.toLowerCase() + "%"));
          }

          if (lifecycleStatus != null) {
            predicates.add(cb.equal(root.get("lifecycleStatus"), lifecycleStatus));
          } else if (!includeRetired) {
            predicates.add(
                cb.notEqual(root.get("lifecycleStatus"), IntegrationLifecycleStatus.RETIRED));
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

          // Tenant scoping: filter by tenant if tenantId is provided
          if (tenantId != null) {
            predicates.add(cb.equal(root.get("tenant").get("tenantId"), tenantId));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return integrationRepository.findAll(spec, pageable);
  }

  /**
   * Creates a new Integration entity from a request.
   *
   * <p>This method creates an integration and automatically associates it with the appropriate
   * tenant based on the creating administrator:
   *
   * <ul>
   *   <li><b>Global Admin:</b> Integration is associated with the "Ezkey System" tenant
   *       (system-level integrations)
   *   <li><b>Tenant Admin:</b> Integration is associated with the admin's tenant (tenant-scoped
   *       integrations)
   * </ul>
   *
   * <p><b>Important:</b> Administrators cannot create integrations for other tenants (no
   * impersonation). If a global admin needs to create resources for a specific tenant, they must
   * become a tenant admin for that tenant.
   *
   * @param request the request containing integration data
   * @param createdByAdmin the administrator creating the integration (for tenant assignment and
   *     audit)
   * @return the created and saved Integration entity
   * @throws IntegrationCreateValidationException if the administrator context is invalid for
   *     creation
   * @throws SystemTenantNotConfiguredException if the system tenant row is missing (server
   *     misconfiguration)
   */
  @Transactional
  public IntegrationCreateResponse createIntegration(
      IntegrationCreateRequest request, EzkeyAdmin createdByAdmin) {
    logger.info(
        "Creating integration by admin: {} (type: {})",
        createdByAdmin.getUsername(),
        createdByAdmin.getAdminType());

    Integration integration = integrationServiceMapper.toEntity(request);
    integration.setLifecycleStatus(IntegrationLifecycleStatus.ACTIVE);
    integration.setCreatedAt(OffsetDateTime.now());
    integration.setCreatedByAdmin(createdByAdmin);

    // Determine tenant based on admin type
    Tenant tenant;
    if (createdByAdmin.getAdminType() == AdminType.GLOBAL_ADMIN) {
      // Global admins create integrations in the system tenant (identified by is_system_tenant)
      tenant =
          tenantRepository
              .findByIsSystemTenantTrue()
              .orElseThrow(
                  () ->
                      new SystemTenantNotConfiguredException(
                          "System tenant not found. Database may not be properly initialized."));
      logger.debug("Associating integration with system tenant: {}", tenant.getTenantName());
    } else if (createdByAdmin.getAdminType() == AdminType.TENANT_ADMIN) {
      // Tenant admins create integrations in their own tenant
      tenant = createdByAdmin.getTenant();
      if (tenant == null) {
        throw new IntegrationCreateValidationException(
            "Tenant admin must have an associated tenant. Admin: " + createdByAdmin.getUsername());
      }
      logger.debug(
          "Associating integration with tenant admin's tenant: {} (ID: {})",
          tenant.getTenantName(),
          tenant.getTenantId());
    } else {
      throw new IntegrationCreateValidationException(
          "Unsupported admin type for integration creation: " + createdByAdmin.getAdminType());
    }

    integration.setTenant(tenant);

    // Validate that integration code is unique per tenant
    if (integrationRepository.existsByCodeAndTenant(integration.getCode(), tenant)) {
      logger.warn(
          "Integration creation blocked: code '{}' already exists in tenant '{}'",
          integration.getCode(),
          tenant.getTenantName());
      throw new IntegrationCodeAlreadyExistsException(
          integration.getCode(), tenant.getTenantName());
    }

    // Security: Block integration creation for inactive tenants
    if (!tenant.getActive()) {
      logger.warn(
          "Integration creation blocked: tenant '{}' (ID: {}) is inactive",
          tenant.getTenantName(),
          tenant.getTenantId());
      throw new TenantInactiveException(
          "Cannot create integration for inactive tenant. Contact your Ezkey administrator.");
    }

    var domResponse = integrationRepository.save(integration);
    IntegrationCreateResponse response = integrationServiceMapper.toCreateResponse(domResponse);

    logger.info(
        "Integration created successfully - ID: {}, Tenant: {} (ID: {}), Admin: {}",
        domResponse.getId(),
        tenant.getTenantName(),
        tenant.getTenantId(),
        createdByAdmin.getUsername());

    return response;
  }

  /** Retires an integration while preserving its historical footprint. */
  @Transactional
  public Integration retire(Integer id) {
    Integration integration =
        integrationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Integration", id));

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      throw new SystemIntegrationLifecycleException(
          "System integration cannot be retired because it is reserved for administrator MFA.");
    }

    if (IntegrationLifecycleStatus.RETIRED.equals(integration.getLifecycleStatus())) {
      return integration;
    }

    integration.setLifecycleStatus(IntegrationLifecycleStatus.RETIRED);
    return integrationRepository.save(integration);
  }

  /**
   * Deletes an Integration entity by its unique identifier.
   *
   * <p>Deletion is exceptional and only allowed for already-retired integrations without
   * enrollments.
   */
  @Transactional
  public void delete(Integer id) {
    Integration integration =
        integrationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Integration", id));

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      throw new SystemIntegrationLifecycleException(
          "System integration cannot be deleted because it is reserved for administrator MFA.");
    }

    if (!IntegrationLifecycleStatus.RETIRED.equals(integration.getLifecycleStatus())) {
      throw new IntegrationLifecycleStateException(
          "Integration must be retired before it can be permanently deleted.");
    }

    if (enrollmentRepository.existsByIntegrationId(id)) {
      throw new IntegrationHasEnrollmentsException();
    }
    integrationRepository.deleteById(id);
  }
}
