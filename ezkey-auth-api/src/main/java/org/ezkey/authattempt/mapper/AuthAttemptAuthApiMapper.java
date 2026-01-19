/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuthAttemptMapper
 * Description: MapStruct mapper for converting between AuthAttempt entities and DTOs in auth API.
 */

package org.ezkey.authattempt.mapper;

import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between AuthAttempt entities and DTOs in auth API.
 *
 * <p>This mapper provides conversion between domain objects and mobile API DTOs for the auth API
 * context. It supports the mobile authentication flow where devices check for pending
 * authentication attempts and submit responses. All mappings are type-safe and validated at compile
 * time.
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Pending Request:</b> AuthAttemptPendingRequestDto → AuthAttemptInitiateRequest
 *   <li><b>Pending Response:</b> AuthAttemptPendingResponse → AuthAttemptPendingResponseDto
 *   <li><b>Respond Request:</b> AuthAttemptRespondRequestDto → AuthAttemptCompleteRequest
 *   <li><b>Respond Response:</b> AuthAttemptRespondResponse → AuthAttemptRespondResponseDto
 * </ul>
 *
 * <p><b>Usage Context:</b> Used exclusively by the auth API to convert between domain objects and
 * DTOs for mobile authentication operations. Handles the complete mobile authentication flow from
 * pending checks to response submissions.
 *
 * <p><b>Mobile Flow Support:</b> Supports the pull-based authentication model where mobile devices
 * poll for pending requests and submit cryptographically signed responses.
 *
 * <p><b>MapStruct Configuration:</b>
 *
 * <ul>
 *   <li><b>Component Model:</b> Spring integration for dependency injection
 *   <li><b>Unmapped Reporting:</b> WARN to identify potential mapping issues
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
 * @see AuthAttempt
 * @see AuthAttemptPendingRequestDto
 * @see AuthAttemptPendingResponseDto
 * @see AuthAttemptRespondRequestDto
 * @see AuthAttemptRespondResponseDto
 * @see AuthAttemptPendingRequest
 * @see AuthAttemptPendingResponse
 * @see AuthAttemptRespondRequest
 * @see AuthAttemptRespondResponse
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN, componentModel = "spring")
public interface AuthAttemptMapper {

  /**
   * Converts a pending request DTO to domain request for mobile authentication polling.
   *
   * <p>Maps mobile device polling requests to domain objects for processing pending authentication
   * attempts. Handles cryptographic signature validation data.
   *
   * @param request the mobile pending request DTO
   * @return the corresponding domain initiate request
   */
  AuthAttemptPendingRequest toAuthAttemptPendingRequest(AuthAttemptPendingRequestDto request);

  /**
   * Converts a domain pending response to DTO for mobile consumption.
   *
   * <p>Maps domain pending response data to mobile-friendly DTOs containing authentication
   * challenge information and cryptographic codes.
   *
   * @param response the domain pending response
   * @return the corresponding mobile response DTO
   */
  AuthAttemptPendingResponseDto toAuthAttemptPendingResponseDto(
      AuthAttemptPendingResponse response);

  /**
   * Converts a respond request DTO to domain request for authentication completion.
   *
   * <p>Maps mobile device response submissions to domain objects for processing authentication
   * attempt completions. Handles user decisions and cryptographic proofs.
   *
   * @param request the mobile respond request DTO
   * @return the corresponding domain complete request
   */
  AuthAttemptRespondRequest toAuthAttemptRespondRequest(AuthAttemptRespondRequestDto request);

  /**
   * Converts a domain respond response to DTO for mobile feedback.
   *
   * <p>Maps domain response results to mobile-friendly DTOs providing confirmation of
   * authentication response processing and status feedback.
   *
   * @param response the domain respond response
   * @return the corresponding mobile response DTO
   */
  AuthAttemptRespondResponseDto toAuthAttemptRespondResponseDto(
      AuthAttemptRespondResponse response);

  /**
   * Maps AuthenticationResult enum to String for DTO serialization.
   *
   * @param result the authentication result enum
   * @return the string representation
   */
  default String mapAuthenticationResultToString(
      org.ezkey.authattempt.domain.AuthenticationResult result) {
    return result != null ? result.name() : null;
  }
}
