/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: IntegrationControllerMapper
 * Description: MapStruct mapper for converting between Integration entities and DTOs in admin API.
 */

package org.ezkey.integration.mapper;

import java.util.List;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationCreateResponseDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between Integration entities and DTOs in admin API.
 *
 * <p>This mapper provides bidirectional conversion between JPA entities and API DTOs for the admin
 * API context. It ensures clean separation between the domain layer and the API layer while
 * supporting complete CRUD operations for integrations.
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Entity → Response DTO:</b> Integration → IntegrationResponseDto
 *   <li><b>Create Response → Entity:</b> IntegrationCreateResponse → Integration
 *   <li><b>Request DTO → Domain:</b> IntegrationCreateRequestDto → IntegrationCreateRequest
 *   <li><b>Collections:</b> List conversions for entity/DTO types
 * </ul>
 *
 * <p><b>Usage Context:</b> Used exclusively by the admin API controller to convert between domain
 * objects and DTOs for administrative operations on integrations.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Integration
 * @see IntegrationResponseDto
 * @see IntegrationCreateRequestDto
 * @see IntegrationCreateRequest
 * @see IntegrationCreateResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface IntegrationControllerMapper {

  /**
   * Converts a list of Integration entities to a list of IntegrationResponseDto.
   *
   * @param entities the list of Integration entities to convert
   * @return the corresponding list of IntegrationResponseDto
   */
  List<IntegrationResponseDto> toResponseList(List<Integration> entities);

  /**
   * Converts an IntegrationCreateRequestDto to an IntegrationCreateRequest domain object.
   *
   * @param request the IntegrationCreateRequestDto from the API layer
   * @return the corresponding IntegrationCreateRequest domain object
   */
  IntegrationCreateRequest toCreateRequest(IntegrationCreateRequestDto request);

  /**
   * Converts an Integration entity to an IntegrationResponseDto.
   *
   * @param integration the Integration entity to convert
   * @return the corresponding IntegrationResponseDto
   */
  @Mapping(source = "tenant.tenantId", target = "tenantId")
  @Mapping(
      target = "operational",
      expression =
          "java(integration.isOperational()"
              + " && (integration.getTenant() == null"
              + " || Boolean.TRUE.equals(integration.getTenant().getActive())))")
  IntegrationResponseDto toResponse(Integration integration);

  /**
   * Converts an IntegrationCreateResponse domain object to an IntegrationResponseDto.
   *
   * @param savedIntegration the IntegrationCreateResponse domain object to convert
   * @return the corresponding IntegrationResponseDto
   */
  IntegrationCreateResponseDto toCreateResponseDto(IntegrationCreateResponse savedIntegration);
}
