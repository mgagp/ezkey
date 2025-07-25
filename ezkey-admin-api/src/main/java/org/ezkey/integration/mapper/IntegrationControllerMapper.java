/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import org.ezkey.integration.domain.IntegrationI18nResponse;
import org.ezkey.integration.domain.IntegrationResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.IntegrationI18n;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between Integration entities and DTOs in admin API.
 * <p>
 * This mapper provides bidirectional conversion between JPA entities and API DTOs
 * for the admin API context. It ensures clean separation between the domain layer
 * and the API layer while supporting complete CRUD operations for integrations.
 * All mappings are type-safe and validated at compile time, including complex
 * nested objects like internationalization data.
 * </p>
 *
 * <p>
 * <b>Supported Conversions:</b>
 * <ul>
 * <li><b>Entity → Response DTO:</b> Integration → IntegrationResponseDto</li>
 * <li><b>Entity → Domain Response:</b> Integration → IntegrationResponse</li>
 * <li><b>Create Response → Entity:</b> IntegrationCreateResponse → Integration</li>
 * <li><b>Request DTO → Domain:</b> IntegrationCreateRequestDto → IntegrationCreateRequest</li>
 * <li><b>Collections:</b> List conversions for all supported entity/DTO types</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used exclusively by the admin API controller to convert between
 * domain objects and DTOs for administrative operations on integrations. Handles
 * complex mapping scenarios including internationalization data.
 * </p>
 *
 * <p>
 * <b>MapStruct Configuration:</b>
 * <ul>
 * <li><b>Component Model:</b> Spring integration for dependency injection</li>
 * <li><b>Unmapped Reporting:</b> WARN to identify potential mapping issues</li>
 * <li><b>Type Safety:</b> Compile-time validation of all mapping configurations</li>
 * <li><b>Performance:</b> Generated implementation for optimal runtime performance</li>
 * </ul>
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
 * @see Integration
 * @see IntegrationResponseDto
 * @see IntegrationCreateRequestDto
 * @see IntegrationCreateRequest
 * @see IntegrationCreateResponse
 * @see IntegrationResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface IntegrationControllerMapper {

    /**
     * Converts an EzkeyIntegration entity to an IntegrationResponse DTO.
     * <p>
     * This method maps all fields from the JPA entity to the response DTO,
     * including the nested i18n collection if present.
     * </p>
     *
     * @param entity the EzkeyIntegration entity to convert
     * @return the corresponding IntegrationResponse DTO
     * @see Integration
     * @see IntegrationResponse
     */
    IntegrationCreateResponse toResponse(IntegrationCreateResponse entity);

    /**
     * Converts a list of EzkeyIntegration entities to a list of IntegrationResponse DTOs.
     * <p>
     * This method applies the individual entity-to-response mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyIntegration entities to convert
     * @return the corresponding list of IntegrationResponse DTOs
     * @see Integration
     * @see IntegrationResponse
     */
    List<IntegrationResponseDto> toResponseList(List<Integration> entities);

    /**
     * Converts an EzkeyIntegrationI18n entity to an IntegrationI18nResponse DTO.
     * <p>
     * This method maps all fields from the i18n JPA entity to the response DTO,
     * including language code, name, and description.
     * </p>
     *
     * @param entity the EzkeyIntegrationI18n entity to convert
     * @return the corresponding IntegrationI18nResponse DTO
     * @see IntegrationI18n
     * @see IntegrationI18nResponse
     */
    IntegrationI18nResponse toI18nResponse(IntegrationI18n entity);

    /**
     * Converts an IntegrationI18nResponse DTO to an EzkeyIntegrationI18n entity.
     * <p>
     * This method maps all fields from the i18n response DTO to the JPA entity.
     * Note that this conversion may not preserve all entity-specific fields
     * such as audit timestamps or database-generated values.
     * </p>
     *
     * @param response the IntegrationI18nResponse DTO to convert
     * @return the corresponding EzkeyIntegrationI18n entity
     * @see IntegrationI18nResponse
     * @see IntegrationI18n
     */
    @Mapping(target = "integration",ignore = true)
    IntegrationI18n toI18nEntity(IntegrationI18nResponse response);

    /**
     * Converts a list of EzkeyIntegrationI18n entities to a list of IntegrationI18nResponse DTOs.
     * <p>
     * This method applies the individual i18n entity-to-response mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyIntegrationI18n entities to convert
     * @return the corresponding list of IntegrationI18nResponse DTOs
     * @see IntegrationI18n
     * @see IntegrationI18nResponse
     */
    List<IntegrationI18nResponse> toI18nResponseList(List<IntegrationI18n> entities);

    /**
     * Converts a list of IntegrationI18nResponse DTOs to a list of EzkeyIntegrationI18n entities.
     * <p>
     * This method applies the individual i18n response-to-entity mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param responses the list of IntegrationI18nResponse DTOs to convert
     * @return the corresponding list of EzkeyIntegrationI18n entities
     * @see IntegrationI18nResponse
     * @see IntegrationI18n
     */
    List<IntegrationI18n> toI18nEntityList(List<IntegrationI18nResponse> responses);

    IntegrationCreateRequest toCreateRequest(IntegrationCreateRequestDto request);

    IntegrationResponse toResponse(Integration integration);

}