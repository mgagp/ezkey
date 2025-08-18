/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between AuthAttempt entities and DTOs in admin API.
 * <p>
 * This mapper provides bidirectional conversion between JPA entities and API DTOs
 * for the admin API context. It ensures clean separation between the domain layer
 * and the API layer while supporting complete CRUD operations for authorization attempts.
 * All mappings are type-safe and validated at compile time.
 * </p>
 *
 * <p>
 * <b>Supported Conversions:</b>
 * <ul>
 * <li><b>Entity → Response DTO:</b> AuthAttempt → AuthAttemptDto</li>
 * <li><b>Entity → Create Response:</b> AuthAttemptCreateResponse → AuthAttemptCreateResponseDto</li>
 * <li><b>Request DTO → Domain:</b> AuthAttemptCreateRequestDto → AuthAttemptCreateRequest</li>
 * <li><b>Collections:</b> List conversions for all supported entity/DTO types</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used exclusively by the admin API to convert between
 * domain objects and DTOs for administrative operations on authorization attempts.
 * </p>
 *
 * <p>
 * <b>MapStruct Configuration:</b>
 * <ul>
 * <li><b>Component Model:</b> Spring integration for dependency injection</li>
 * <li><b>Unmapped Reporting:</b> IGNORE for flexible mapping configuration</li>
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
 * @see AuthAttempt
 * @see AuthAttemptDto
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 * @see AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponse
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

    /**
     * Converts an EzkeyAuthAttemptCreateRequestDto to an EzkeyAuthAttemptCreateRequest domain object.
     * <p>
     * This method maps all fields from the request DTO to the domain object,
     * preparing it for processing in the service layer.
     * </p>
     *
     * @param request the EzkeyAuthAttemptCreateRequestDto to convert
     * @return the corresponding EzkeyAuthAttemptCreateRequest domain object
     * @see AuthAttemptCreateRequestDto
     * @see AuthAttemptCreateRequest
     */
    AuthAttemptCreateRequest toAuthAttemptCreateRequest(AuthAttemptCreateRequestDto request);

    /**
     * Converts an EzkeyAuthAttemptCreateResponse domain object to an EzkeyAuthAttemptCreateResponseDto.
     * <p>
     * This method maps all fields from the domain object to the response DTO,
     * preparing it for return to the API client.
     * </p>
     *
     * @param response the EzkeyAuthAttemptCreateResponse to convert
     * @return the corresponding EzkeyAuthAttemptCreateResponseDto
     * @see AuthAttemptCreateResponse
     * @see AuthAttemptCreateResponseDto
     */
    AuthAttemptCreateResponseDto toAuthAttemptCreateResponseDto(AuthAttemptCreateResponse response);

}