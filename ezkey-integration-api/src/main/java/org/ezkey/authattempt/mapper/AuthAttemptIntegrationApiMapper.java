/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuthAttemptIntegrationApiMapper
 * Description: MapStruct mapper for converting between AuthAttempt entities and DTOs in Integration API.
 */

package org.ezkey.authattempt.mapper;

import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between AuthAttempt entities and DTOs in the
 * Integration API.
 *
 * <p>This mapper provides the conversions required by the Integration API (machine-to-machine)
 * controllers. It intentionally omits admin-specific mappings (e.g. create-request DTO → domain,
 * since the Integration API controller resolves the enrollment separately before constructing the
 * domain object directly).
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Entity → Response DTO:</b> AuthAttempt → AuthAttemptDto
 *   <li><b>Domain → Create Response DTO:</b> AuthAttemptCreateResponse →
 *       AuthAttemptCreateResponseDto
 *   <li><b>Request DTO → Domain:</b> AuthAttemptWaitRequestDto → AuthAttemptWaitRequest
 *   <li><b>Domain → Wait Response DTO:</b> AuthAttemptWaitResponse → AuthAttemptWaitResponseDto
 * </ul>
 *
 * <p><b>MapStruct Configuration:</b>
 *
 * <ul>
 *   <li><b>Component Model:</b> Spring integration for dependency injection
 *   <li><b>Unmapped Reporting:</b> WARN to surface unexpected gaps without failing the build
 *   <li><b>Type Safety:</b> Compile-time validation of all mapping configurations
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttempt
 * @see AuthAttemptDto
 * @see AuthAttemptCreateResponseDto
 * @see AuthAttemptWaitRequestDto
 * @see AuthAttemptWaitResponseDto
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = "spring")
public interface AuthAttemptIntegrationApiMapper {

  /**
   * Converts an AuthAttempt entity to an AuthAttemptDto.
   *
   * <p>Used in cancel and status responses to return the current state of an authentication attempt
   * to the integrating application.
   *
   * @param entity the AuthAttempt entity to convert
   * @return the corresponding AuthAttemptDto
   */
  AuthAttemptDto toDto(AuthAttempt entity);

  /**
   * Converts an AuthAttemptCreateResponse domain object to an AuthAttemptCreateResponseDto.
   *
   * <p>Used after a successful authentication attempt creation to build the HTTP 201 response.
   *
   * @param response the AuthAttemptCreateResponse domain object
   * @return the corresponding AuthAttemptCreateResponseDto
   */
  AuthAttemptCreateResponseDto toAuthAttemptCreateResponseDto(AuthAttemptCreateResponse response);

  /**
   * Converts an AuthAttemptWaitRequestDto to an AuthAttemptWaitRequest domain object.
   *
   * <p>Used to translate the query-parameter-based wait configuration into the domain object
   * consumed by {@code AuthAttemptService#waitForResponse}.
   *
   * @param request the AuthAttemptWaitRequestDto to convert
   * @return the corresponding AuthAttemptWaitRequest domain object
   */
  AuthAttemptWaitRequest toAuthAttemptWaitRequest(AuthAttemptWaitRequestDto request);

  /**
   * Converts an AuthAttemptWaitResponse domain object to an AuthAttemptWaitResponseDto.
   *
   * <p>Used to translate the service-layer wait result into the HTTP response body for the wait
   * endpoint, including calculated status, completion flags, and timing information.
   *
   * @param response the AuthAttemptWaitResponse domain object
   * @return the corresponding AuthAttemptWaitResponseDto
   */
  AuthAttemptWaitResponseDto toAuthAttemptWaitResponseDto(AuthAttemptWaitResponse response);
}
