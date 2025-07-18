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

import org.ezkey.authattempt.domain.AuthAttemptCompleteRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptInitiateRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.mapstruct.Mapper;
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

    AuthAttemptInitiateRequest toAuthAttemptPendingRequest(AuthAttemptPendingRequestDto request);

    AuthAttemptPendingResponseDto toAuthAttemptPendingResponseDto(AuthAttemptPendingResponse response);

    AuthAttemptCompleteRequest toAuthAttemptRespondRequest(AuthAttemptRespondRequestDto request);

    AuthAttemptRespondResponseDto toAuthAttemptRespondResponseDto(AuthAttemptRespondResponse response);

}