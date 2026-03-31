/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: EnrollmentAuthMapper
 * Description: MapStruct mapper for converting between Enrollment entities and DTOs in auth API.
 */

package org.ezkey.enrollment.mapper;

import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between Enrollment entities and DTOs in auth API.
 *
 * <p>This mapper provides conversion between domain objects and mobile API DTOs for the auth API
 * context. It supports the mobile enrollment flow where devices bind to enrollments and complete
 * verification to activate their MFA capabilities. All mappings are type-safe and validated at
 * compile time.
 *
 * <p><b>Supported Conversions:</b>
 *
 * <ul>
 *   <li><b>Bind Request:</b> EnrollmentBindRequestDto → EnrollmentBindRequest
 *   <li><b>Bind Response:</b> EnrollmentBindResponse → EnrollmentBindResponseDto
 *   <li><b>Verify Request:</b> EnrollmentVerifyRequestDto → EnrollmentVerifyRequest
 *   <li><b>Verify Response:</b> EnrollmentVerifyResponse → EnrollmentVerifyResponseDto
 * </ul>
 *
 * <p><b>Usage Context:</b> Used exclusively by the auth API to convert between domain objects and
 * DTOs for mobile enrollment operations. Handles the complete mobile enrollment flow from initial
 * binding to final verification.
 *
 * <p><b>Mobile Enrollment Support:</b> Supports the enrollment process where mobile devices scan QR
 * codes, bind to enrollments, generate cryptographic keys, and verify their enrollment to become
 * active MFA devices.
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 * @see EnrollmentBindResponseDto
 * @see EnrollmentVerifyRequestDto
 * @see EnrollmentVerifyResponseDto
 * @see EnrollmentBindResponse
 * @see EnrollmentVerifyRequest
 * @see EnrollmentVerifyResponse
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface EnrollmentAuthMapper {

  /**
   * Converts a bind request DTO to domain request for enrollment binding.
   *
   * <p>Maps mobile device binding requests to domain objects for processing enrollment binding.
   * Handles enrollment ID, proof token, and language preferences for secure enrollment access.
   *
   * @param request the mobile bind request DTO
   * @return the corresponding domain bind request
   */
  EnrollmentBindRequest toEnrollmentBindRequest(EnrollmentBindRequestDto request);

  /**
   * Converts a domain bind response to DTO for mobile consumption.
   *
   * <p>Maps enrollment binding information to mobile-friendly DTOs containing cryptographic keys
   * and enrollment codes data needed for mobile devices to complete enrollment verification.
   *
   * @param response the domain bind response
   * @return the corresponding mobile response DTO
   */
  EnrollmentBindResponseDto toEnrollmentBindResponseDto(EnrollmentBindResponse response);

  /**
   * Converts a verify request DTO to domain request for enrollment completion.
   *
   * <p>Maps mobile device verification submissions to domain objects for processing enrollment
   * completion. Handles device public keys, signed enrollment codes, and challenge responses that
   * activate the enrollment.
   *
   * @param request the mobile verify request DTO
   * @return the corresponding domain verify request
   */
  EnrollmentVerifyRequest toEnrollmentVerifyRequest(EnrollmentVerifyRequestDto request);

  /**
   * Converts a domain verify response to DTO for mobile feedback.
   *
   * <p>Maps enrollment verification results to mobile-friendly DTOs providing confirmation of
   * enrollment activation status and readiness for authentication.
   *
   * @param response the domain verify response
   * @return the corresponding mobile response DTO
   */
  EnrollmentVerifyResponseDto toEnrollmentVerifyResponseDto(EnrollmentVerifyResponse response);
}
