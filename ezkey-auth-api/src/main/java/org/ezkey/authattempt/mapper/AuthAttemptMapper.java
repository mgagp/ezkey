/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuthAttemptMapper
 * Description: MapStruct mapper for converting between AuthAttempt entities and DTOs.
 */

package org.ezkey.authattempt.mapper;

import java.util.List;

import org.ezkey.authattempt.domain.AuthAttemptCompleteRequest;
import org.ezkey.authattempt.domain.AuthAttemptCompleteResponse;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptInitiateRequest;
import org.ezkey.authattempt.domain.AuthAttemptInitiateResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between AuthAttempt entities and DTOs.
 * <p>
 * This mapper provides bidirectional conversion between JPA entities and API DTOs,
 * ensuring clean separation between the domain layer and the API layer. It handles
 * both individual objects and collections, supporting the complete CRUD operations
 * for the AuthAttempt module.
 * </p>
 *
 * <p>
 * <b>Supported Conversions:</b>
 * <ul>
 * <li><b>Entity ↔ Response:</b> EzkeyAuthAttempt ↔ EzkeyAuthAttemptDto</li>
 * <li><b>Entity ↔ Response:</b> EzkeyAuthAttempt ↔ EzkeyAuthAttemptCreateDtoResponse</li>
 * <li><b>Request → Entity:</b> EzkeyAuthAttemptCreateDtoRequest → EzkeyAuthAttempt</li>
 * <li><b>Collections:</b> List conversions for all supported types</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>MapStruct Features:</b>
 * <ul>
 * <li><b>Spring Integration:</b> Automatically registered as a Spring component</li>
 * <li><b>Automatic Mapping:</b> Field names are automatically matched</li>
 * <li><b>Type Safety:</b> Compile-time validation of mapping configurations</li>
 * <li><b>Performance:</b> Generated code for optimal runtime performance</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Entity-DTO mapping for AuthAttempt API
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttempt
 * @see AuthAttemptDto
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN,componentModel = "spring")
public interface AuthAttemptMapper {

    /**
     * Converts an EzkeyAuthAttempt entity to an EzkeyAuthAttemptDto.
     * <p>
     * This method maps all fields from the JPA entity to the response DTO,
     * excluding sensitive information for security.
     * </p>
     *
     * @param entity the EzkeyAuthAttempt entity to convert
     * @return the corresponding EzkeyAuthAttemptDto
     * @see AuthAttempt
     * @see AuthAttemptDto
     */
    AuthAttemptDto toDto(AuthAttempt entity);

    /**
     * Converts an EzkeyAuthAttempt entity to an EzkeyAuthAttemptCreateDtoResponse.
     * <p>
     * This method maps all fields from the JPA entity to the create response DTO,
     * used specifically for authorization attempt creation operations.
     * </p>
     *
     * @param entity the EzkeyAuthAttempt entity to convert
     * @return the corresponding EzkeyAuthAttemptCreateDtoResponse
     * @see AuthAttempt
     * @see AuthAttemptCreateResponseDto
     */
    @Mapping(target = "simulationAuthAttemptEnrolleeCode",ignore = true)
    @Mapping(target = "simulationAuthAttemptChallengeResponse",ignore = true)
    @Mapping(target = "simulationAuthAttemptEnrolleeCodeSigned",ignore = true)
    AuthAttemptCreateResponseDto toCreateResponse(AuthAttempt entity);

    /**
     * Converts a list of EzkeyAuthAttempt entities to a list of EzkeyAuthAttemptDto objects.
     * <p>
     * This method applies the individual entity-to-DTO mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyAuthAttempt entities to convert
     * @return the corresponding list of EzkeyAuthAttemptDto objects
     * @see AuthAttempt
     * @see AuthAttemptDto
     */
    List<AuthAttemptDto> toDtoList(List<AuthAttempt> entities);

    AuthAttemptCreateRequest toAuthAttemptCreateRequest(AuthAttemptCreateRequestDto request);

    AuthAttemptCreateResponseDto toAuthAttemptCreateResponseDto(AuthAttemptCreateResponse response);

    AuthAttemptInitiateRequest toAuthAttemptInitiateRequest(AuthAttemptInitiateRequestDto request);

    AuthAttemptInitiateResponseDto toAuthAttemptInitiateResponseDto(AuthAttemptInitiateResponse response);

    AuthAttemptCompleteRequest toAuthAttemptCompleteRequest(AuthAttemptCompleteRequestDto request);

    AuthAttemptCompleteResponseDto toAuthAttemptCompleteResponseDto(AuthAttemptCompleteResponse response);

}