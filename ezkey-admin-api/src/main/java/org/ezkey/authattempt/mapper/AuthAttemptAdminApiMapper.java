/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuthAttemptMapper
 * Description: MapStruct mapper for converting between AuthAttempt entities and DTOs in admin API.
 */

package org.ezkey.authattempt.mapper;

import java.util.List;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.Integration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between AuthAttempt entities and DTOs in admin API.
 *
 * <p>This mapper provides bidirectional conversion between JPA entities and API DTOs for the admin
 * API context. It ensures clean separation between the domain layer and the API layer while
 * supporting complete CRUD operations for authorization attempts. All mappings are type-safe and
 * validated at compile time.
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Entity → Response DTO:</b> AuthAttempt → AuthAttemptDto
 *   <li><b>Entity → Create Response:</b> AuthAttemptCreateResponse → AuthAttemptCreateResponseDto
 *   <li><b>Request DTO → Domain:</b> AuthAttemptCreateRequestDto → AuthAttemptCreateRequest
 *   <li><b>Request DTO → Domain:</b> AuthAttemptWaitRequestDto → AuthAttemptWaitRequest
 *   <li><b>Domain → Response DTO:</b> AuthAttemptWaitResponse → AuthAttemptWaitResponseDto
 *   <li><b>Wait Response:</b> AuthAttemptWaitResponseDto creation from components
 *   <li><b>Collections:</b> List conversions for all supported entity/DTO types
 * </ul>
 *
 * <p><b>Usage Context:</b> Used exclusively by the admin API to convert between domain objects and
 * DTOs for administrative operations on authorization attempts.
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttempt
 * @see AuthAttemptDto
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 * @see AuthAttemptWaitRequestDto
 * @see AuthAttemptWaitResponseDto
 * @see AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponse
 * @see AuthAttemptWaitRequest
 * @see AuthAttemptWaitResponse
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = "spring")
public interface AuthAttemptAdminApiMapper {

  /**
   * Converts an EzkeyAuthAttempt entity to an EzkeyAuthAttemptDto.
   *
   * <p>This method maps all fields from the JPA entity to the response DTO, excluding sensitive
   * information for security.
   *
   * @param entity the EzkeyAuthAttempt entity to convert
   * @return the corresponding EzkeyAuthAttemptDto
   * @see AuthAttempt
   * @see AuthAttemptDto
   */
  @Mapping(target = "integrationId", ignore = true)
  @Mapping(target = "integrationName", ignore = true)
  @Mapping(target = "enrollmentName", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "tenantName", ignore = true)
  AuthAttemptDto toDto(AuthAttempt entity);

  /**
   * Converts an auth attempt to a DTO with enrollment and integration display labels for admin list
   * and detail screens.
   *
   * @param entity the auth attempt entity
   * @param enrollment the enrollment for this attempt, or null
   * @param integration the integration for the enrollment, or null
   * @return DTO with optional enrichment fields populated when context is available
   */
  default AuthAttemptDto toDtoWithLabels(
      AuthAttempt entity, Enrollment enrollment, Integration integration) {
    AuthAttemptDto base = toDto(entity);
    String enrollmentName = enrollment != null ? enrollment.getEnrollmentName() : null;
    Integer integrationId = enrollment != null ? enrollment.getIntegrationId() : null;
    String integrationName = integration != null ? integration.getName() : null;
    Integer tenantId =
        integration != null && integration.getTenant() != null
            ? integration.getTenant().getTenantId()
            : null;
    String tenantName =
        integration != null && integration.getTenant() != null
            ? integration.getTenant().getTenantName()
            : null;
    return new AuthAttemptDto(
        base.authAttemptId(),
        base.enrollmentId(),
        base.authAttemptStatus(),
        base.authAttemptChallenge(),
        base.authAttemptProofToken(),
        base.createdAt(),
        base.expiresAt(),
        base.contextTitle(),
        base.contextMessage(),
        integrationId,
        integrationName,
        enrollmentName,
        tenantId,
        tenantName);
  }

  /**
   * Converts a list of EzkeyAuthAttempt entities to a list of EzkeyAuthAttemptDto objects.
   *
   * <p>This method applies the individual entity-to-DTO mapping to each element in the input list,
   * maintaining the order of elements.
   *
   * @param entities the list of EzkeyAuthAttempt entities to convert
   * @return the corresponding list of EzkeyAuthAttemptDto objects
   * @see AuthAttempt
   * @see AuthAttemptDto
   */
  List<AuthAttemptDto> toDtoList(List<AuthAttempt> entities);

  /**
   * Converts an EzkeyAuthAttemptCreateRequestDto to an EzkeyAuthAttemptCreateRequest domain object.
   *
   * <p>This method maps all fields from the request DTO to the domain object, preparing it for
   * processing in the service layer.
   *
   * @param request the EzkeyAuthAttemptCreateRequestDto to convert
   * @return the corresponding EzkeyAuthAttemptCreateRequest domain object
   * @see AuthAttemptCreateRequestDto
   * @see AuthAttemptCreateRequest
   */
  AuthAttemptCreateRequest toAuthAttemptCreateRequest(AuthAttemptCreateRequestDto request);

  /**
   * Converts an EzkeyAuthAttemptCreateResponse domain object to an
   * EzkeyAuthAttemptCreateResponseDto.
   *
   * <p>This method maps all fields from the domain object to the response DTO, preparing it for
   * return to the API client.
   *
   * @param response the EzkeyAuthAttemptCreateResponse to convert
   * @return the corresponding EzkeyAuthAttemptCreateResponseDto
   * @see AuthAttemptCreateResponse
   * @see AuthAttemptCreateResponseDto
   */
  AuthAttemptCreateResponseDto toAuthAttemptCreateResponseDto(AuthAttemptCreateResponse response);

  /**
   * Converts an AuthAttemptWaitRequestDto to an AuthAttemptWaitRequest domain object.
   *
   * <p>This method maps all fields from the request DTO to the domain object, preparing it for
   * processing in the service layer.
   *
   * @param request the AuthAttemptWaitRequestDto to convert
   * @return the corresponding AuthAttemptWaitRequest domain object
   * @see AuthAttemptWaitRequestDto
   * @see AuthAttemptWaitRequest
   */
  AuthAttemptWaitRequest toAuthAttemptWaitRequest(AuthAttemptWaitRequestDto request);

  /**
   * Converts an AuthAttemptWaitResponse domain object to an AuthAttemptWaitResponseDto.
   *
   * <p>This method maps all fields from the domain object to the response DTO, preparing it for
   * return to the API client.
   *
   * @param response the AuthAttemptWaitResponse to convert
   * @return the corresponding AuthAttemptWaitResponseDto
   * @see AuthAttemptWaitResponse
   * @see AuthAttemptWaitResponseDto
   */
  AuthAttemptWaitResponseDto toAuthAttemptWaitResponseDto(AuthAttemptWaitResponse response);

  /**
   * Creates an AuthAttemptWaitResponseDto from its components.
   *
   * <p>This method constructs the wait response DTO from the authentication attempt data and
   * calculated status information. It provides a clean way to build the response for the wait
   * endpoint.
   *
   * @param authAttempt the authentication attempt DTO
   * @param status the calculated status string
   * @param completed whether authentication is complete
   * @param timeoutReached whether timeout was reached
   * @param waitDuration actual wait duration in seconds
   * @param completedAt timestamp when wait completed
   * @return the constructed AuthAttemptWaitResponseDto
   * @see AuthAttemptWaitResponseDto
   * @see AuthAttemptDto
   */
  AuthAttemptWaitResponseDto buildAuthAttemptWaitResponseDto(
      AuthAttemptDto authAttempt,
      String status,
      Boolean completed,
      Boolean timeoutReached,
      Integer waitDuration,
      java.time.OffsetDateTime completedAt);
}
