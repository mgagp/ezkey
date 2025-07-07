/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: IntegrationMapper
 * Description: MapStruct mapper for converting between Integration entities and DTOs.
 */

package org.ezkey.integration.mapper;

import java.util.List;

import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.domain.entity.EzkeyIntegrationI18n;
import org.ezkey.integration.dto.request.IntegrationCreateDtoRequest;
import org.ezkey.integration.dto.response.IntegrationCreateDtoResponse;
import org.ezkey.integration.dto.response.IntegrationDtoResponse;
import org.ezkey.integration.dto.response.IntegrationI18nResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper interface for converting between Integration entities and DTOs.
 * <p>
 * This mapper provides bidirectional conversion between JPA entities and API DTOs,
 * ensuring clean separation between the domain layer and the API layer. It handles
 * both individual objects and collections, supporting the complete CRUD operations
 * for the Integration module.
 * </p>
 *
 * <p>
 * <b>Supported Conversions:</b>
 * <ul>
 *   <li><b>Entity ↔ Response:</b> EzkeyIntegration ↔ IntegrationResponse</li>
 *   <li><b>Entity ↔ Response:</b> EzkeyIntegrationI18n ↔ IntegrationI18nResponse</li>
 *   <li><b>Request → Entity:</b> IntegrationCreateRequest → EzkeyIntegration</li>
 *   <li><b>Collections:</b> List conversions for all supported types</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>MapStruct Features:</b>
 * <ul>
 *   <li><b>Spring Integration:</b> Automatically registered as a Spring component</li>
 *   <li><b>Automatic Mapping:</b> Field names are automatically matched</li>
 *   <li><b>Type Safety:</b> Compile-time validation of mapping configurations</li>
 *   <li><b>Performance:</b> Generated code for optimal runtime performance</li>
 * </ul>
 * </p>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Entity-DTO mapping for Integration API</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyIntegration
 * @see EzkeyIntegrationI18n
 * @see IntegrationResponse
 * @see IntegrationI18nResponse
 * @see IntegrationCreateRequest
 */
@Mapper(componentModel = "spring")
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
     * @see EzkeyIntegration
     * @see IntegrationResponse
     */
    IntegrationCreateDtoResponse toResponse(IntegrationCreateResponse entity);
  
    /**
     * Converts a list of EzkeyIntegration entities to a list of IntegrationResponse DTOs.
     * <p>
     * This method applies the individual entity-to-response mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyIntegration entities to convert
     * @return the corresponding list of IntegrationResponse DTOs
     * @see EzkeyIntegration
     * @see IntegrationResponse
     */
    List<IntegrationDtoResponse> toResponseList(List<EzkeyIntegration> entities);
    
    /**
     * Converts an EzkeyIntegrationI18n entity to an IntegrationI18nResponse DTO.
     * <p>
     * This method maps all fields from the i18n JPA entity to the response DTO,
     * including language code, name, and description.
     * </p>
     *
     * @param entity the EzkeyIntegrationI18n entity to convert
     * @return the corresponding IntegrationI18nResponse DTO
     * @see EzkeyIntegrationI18n
     * @see IntegrationI18nResponse
     */
    IntegrationI18nResponse toI18nResponse(EzkeyIntegrationI18n entity);
    
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
     * @see EzkeyIntegrationI18n
     */
    @Mapping(target = "integration", ignore = true)
    EzkeyIntegrationI18n toI18nEntity(IntegrationI18nResponse response);
    
    /**
     * Converts a list of EzkeyIntegrationI18n entities to a list of IntegrationI18nResponse DTOs.
     * <p>
     * This method applies the individual i18n entity-to-response mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyIntegrationI18n entities to convert
     * @return the corresponding list of IntegrationI18nResponse DTOs
     * @see EzkeyIntegrationI18n
     * @see IntegrationI18nResponse
     */
    List<IntegrationI18nResponse> toI18nResponseList(List<EzkeyIntegrationI18n> entities);
    
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
     * @see EzkeyIntegrationI18n
     */
    List<EzkeyIntegrationI18n> toI18nEntityList(List<IntegrationI18nResponse> responses);

    /**
     * Converts an IntegrationCreateRequest DTO to an EzkeyIntegration entity.
     * <p>
     * This method is used during the creation of new integrations, mapping
     * the request data to a new entity instance. The entity will typically
     * need to be persisted to the database after this conversion.
     * </p>
     *
     * @param request the IntegrationCreateRequest DTO to convert
     * @return the corresponding EzkeyIntegration entity
     * @see IntegrationCreateRequest
     * @see EzkeyIntegration
     */
    IntegrationCreateRequest toEntity(IntegrationCreateDtoRequest request);

	IntegrationCreateRequest toCreateRequest(IntegrationCreateDtoRequest request);

    IntegrationDtoResponse toResponse(EzkeyIntegration integration);

} 