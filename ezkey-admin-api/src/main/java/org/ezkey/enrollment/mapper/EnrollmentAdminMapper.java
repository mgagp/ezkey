/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: EnrollmentAdminMapper
 * Description: MapStruct mapper for converting between Enrollment entities and DTOs in admin API.
 */

package org.ezkey.enrollment.mapper;

import java.util.List;
import java.util.Map;
import org.ezkey.enrollment.domain.DevicePrivateKeyStorageTier;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.integration.domain.entity.Integration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between Enrollment entities and DTOs in admin API.
 *
 * <p>This mapper provides bidirectional conversion between JPA entities and API DTOs for the admin
 * API context. It ensures clean separation between the domain layer and the API layer while
 * supporting complete CRUD operations for enrollments. All mappings are type-safe and validated at
 * compile time.
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Entity → Response DTO:</b> Enrollment → EnrollmentResponseDto
 *   <li><b>Create Response → DTO:</b> EnrollmentCreateResponse → EnrollmentCreateResponseDto
 *   <li><b>Request DTO → Domain:</b> EnrollmentCreateRequestDto → EnrollmentCreateRequest
 *   <li><b>Collections:</b> List conversions for all supported entity/DTO types
 * </ul>
 *
 * <p><b>Usage Context:</b> Used exclusively by the admin API to convert between domain objects and
 * DTOs for administrative operations on enrollments.
 *
 * <p><b>MapStruct Configuration:</b>
 *
 * <ul>
 *   <li><b>Component Model:</b> Spring integration for dependency injection
 *   <li><b>Unmapped Reporting:</b> WARN so missing mappings surface at compile time; intentional
 *       gaps must use explicit {@code @Mapping(target = "...", ignore = true)}
 *   <li><b>Type Safety:</b> Compile-time validation of all mapping configurations
 *   <li><b>Performance:</b> Generated implementation for optimal runtime performance
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Enrollment
 * @see EnrollmentResponseDto
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 * @see EnrollmentCreateRequest
 * @see EnrollmentCreateResponse
 */
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.WARN,
    componentModel = "spring",
    imports = {EnrollmentStatus.class})
public interface EnrollmentAdminMapper {

  /**
   * Converts an Enrollment entity to an EnrollmentResponseDto.
   *
   * <p>This method maps all fields from the JPA entity to the response DTO, excluding sensitive
   * information like private keys for security.
   *
   * @param entity the Enrollment entity to convert
   * @return the corresponding EnrollmentResponseDto
   * @see Enrollment
   * @see EnrollmentResponseDto
   */
  @Mapping(source = "status", target = "enrollmentStatus")
  @Mapping(source = "active", target = "enrollmentActive")
  @Mapping(target = "integrationName", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "tenantName", ignore = true)
  @Mapping(target = "isSystemIntegration", ignore = true)
  @Mapping(target = "createdByAdminUsername", ignore = true)
  @Mapping(target = "deactivatedByAdminUsername", ignore = true)
  @Mapping(target = "revokedByAdminUsername", ignore = true)
  @Mapping(
      target = "operational",
      expression =
          "java(EnrollmentStatus.VERIFIED.equals(entity.getStatus())"
              + " && Boolean.TRUE.equals(entity.getActive()))")
  EnrollmentResponseDto toResponse(Enrollment entity);

  /**
   * Converts an Enrollment entity to an EnrollmentResponseDto with integration display fields
   * populated from the given Integration (used for GET by ID to support breadcrumb and
   * non-clickable system integration segment).
   *
   * @param entity the Enrollment entity to convert
   * @param integration the Integration for this enrollment, or null (integrationName and
   *     isSystemIntegration will be null)
   * @return the corresponding EnrollmentResponseDto with optional integrationName and
   *     isSystemIntegration set
   */
  default EnrollmentResponseDto toResponseWithIntegration(
      Enrollment entity, Integration integration) {
    return toResponseWithIntegrationAndAdminUsernames(entity, integration, Map.of());
  }

  /**
   * Same as {@link #toResponseWithIntegration(Enrollment, Integration)} with optional admin
   * username labels for detail surfaces (GET/PATCH by ID).
   *
   * @param entity the enrollment entity
   * @param integration the integration context, or null
   * @param adminUsernamesById map of adminId → username from a batch lookup (may be empty)
   * @return enriched enrollment response DTO
   */
  default EnrollmentResponseDto toResponseWithIntegrationAndAdminUsernames(
      Enrollment entity, Integration integration, Map<Integer, String> adminUsernamesById) {
    EnrollmentResponseDto base = toResponse(entity);
    String name = integration != null ? integration.getName() : null;
    Integer tenantId =
        integration != null && integration.getTenant() != null
            ? integration.getTenant().getTenantId()
            : null;
    String tenantName =
        integration != null && integration.getTenant() != null
            ? integration.getTenant().getTenantName()
            : null;
    Boolean isSystem =
        integration != null ? Boolean.TRUE.equals(integration.getIsSystemIntegration()) : null;
    // Full-chain operational: VERIFIED + active + integration ACTIVE + tenant active
    boolean localOp =
        EnrollmentStatus.VERIFIED.equals(entity.getStatus())
            && Boolean.TRUE.equals(entity.getActive());
    boolean integrationOp =
        integration != null
            && integration.isOperational()
            && (integration.getTenant() == null
                || Boolean.TRUE.equals(integration.getTenant().getActive()));
    Boolean operational = localOp && integrationOp;
    Map<Integer, String> usernames = adminUsernamesById != null ? adminUsernamesById : Map.of();
    return new EnrollmentResponseDto(
        base.enrollmentId(),
        base.version(),
        base.integrationId(),
        base.enrollmentName(),
        base.enrollmentStatus(),
        base.enrollmentActive(),
        base.enrollmentChallenge(),
        base.enrollmentProofToken(),
        base.authAttemptChallengeRequired(),
        base.integrationPublicKey(),
        base.devicePublicKey(),
        base.devicePrivateKeyStorageTier(),
        base.verifiedAt(),
        base.expiresAt(),
        base.createdAt(),
        base.createdByAdminId(),
        usernameFor(usernames, base.createdByAdminId()),
        base.lastUsedAt(),
        base.contactEmail(),
        base.contactPhoneNumber(),
        base.userIdentifier(),
        base.deactivatedAt(),
        base.deactivatedByAdminId(),
        usernameFor(usernames, base.deactivatedByAdminId()),
        base.revokedAt(),
        base.revokedByAdminId(),
        usernameFor(usernames, base.revokedByAdminId()),
        name,
        tenantId,
        tenantName,
        isSystem,
        operational);
  }

  /**
   * Resolves a username from a batch lookup map.
   *
   * @param adminUsernamesById admin id to username map
   * @param adminId foreign key, may be null
   * @return username or null when id is null or not in map
   */
  static String usernameFor(Map<Integer, String> adminUsernamesById, Integer adminId) {
    if (adminId == null) {
      return null;
    }
    return adminUsernamesById.get(adminId);
  }

  /**
   * Converts a list of Enrollment entities to a list of EnrollmentResponseDto.
   *
   * <p>This method applies the individual entity-to-response mapping to each element in the input
   * list, maintaining the order of elements.
   *
   * @param entities the list of Enrollment entities to convert
   * @return the corresponding list of EnrollmentResponseDto
   * @see Enrollment
   * @see EnrollmentResponseDto
   */
  List<EnrollmentResponseDto> toResponseList(List<Enrollment> entities);

  /**
   * Converts an EnrollmentCreateRequestDto to an EnrollmentCreateRequest domain object.
   *
   * <p>This method maps the request DTO from the API layer to the domain request object used by the
   * service layer for enrollment creation operations.
   *
   * <p>{@code createdByAdminId} is not part of the public DTO; the admin API controller sets it
   * from the authenticated principal after mapping (see {@code EnrollmentController}).
   *
   * @param request the EnrollmentCreateRequestDto from the API layer
   * @return the corresponding EnrollmentCreateRequest domain object
   * @see EnrollmentCreateRequestDto
   * @see EnrollmentCreateRequest
   */
  @Mapping(target = "createdByAdminId", ignore = true)
  EnrollmentCreateRequest toCreateRequest(EnrollmentCreateRequestDto request);

  /**
   * Converts an EnrollmentCreateResponse domain object to an EnrollmentCreateResponseDto.
   *
   * <p>This method maps the domain response object from the service layer to the response DTO
   * returned by the API layer for enrollment creation operations.
   *
   * @param response the EnrollmentCreateResponse domain object from the service layer
   * @return the corresponding EnrollmentCreateResponseDto for the API layer
   * @see EnrollmentCreateResponse
   * @see EnrollmentCreateResponseDto
   */
  EnrollmentCreateResponseDto toCreateResponseDto(EnrollmentCreateResponse response);

  default String devicePrivateKeyStorageTierToString(DevicePrivateKeyStorageTier tier) {
    if (tier == null) {
      return null;
    }
    return tier.name();
  }
}
