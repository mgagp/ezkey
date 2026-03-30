/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: EnrollmentAdminMapper
 * Description: MapStruct mapper for converting between Enrollment entities and DTOs in admin API.
 */

package org.ezkey.enrollment.mapper;

import java.util.List;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
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
 *   <li><b>Unmapped Reporting:</b> IGNORE for flexible mapping configuration
 *   <li><b>Type Safety:</b> Compile-time validation of all mapping configurations
 *   <li><b>Performance:</b> Generated implementation for optimal runtime performance
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = "spring")
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
  @Mapping(target = "isSystemIntegration", ignore = true)
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
    EnrollmentResponseDto base = toResponse(entity);
    String name = integration != null ? integration.getName() : null;
    Boolean isSystem =
        integration != null ? Boolean.TRUE.equals(integration.getIsSystemIntegration()) : null;
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
        base.verifiedAt(),
        base.expiresAt(),
        base.createdAt(),
        base.createdByAdminId(),
        base.lastUsedAt(),
        base.contactEmail(),
        base.userIdentifier(),
        base.deactivatedAt(),
        base.deactivatedByAdminId(),
        base.revokedAt(),
        base.revokedByAdminId(),
        name,
        isSystem);
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
   * @param request the EnrollmentCreateRequestDto from the API layer
   * @return the corresponding EnrollmentCreateRequest domain object
   * @see EnrollmentCreateRequestDto
   * @see EnrollmentCreateRequest
   */
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
}
