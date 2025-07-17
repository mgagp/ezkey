/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: EnrollmentMapper
 * Description: MapStruct mapper for converting between Enrollment entities and DTOs.
 */

package org.ezkey.enrollment.mapper;

import java.util.List;

import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between Enrollment entities and DTOs.
 * <p>
 * This mapper provides bidirectional conversion between JPA entities and API DTOs,
 * ensuring clean separation between the domain layer and the API layer. It handles
 * both individual objects and collections, supporting the complete CRUD operations
 * for the Enrollment module.
 * </p>
 *
 * <p>
 * <b>Supported Conversions:</b>
 * <ul>
 * <li><b>Entity ↔ Response:</b> EzkeyEnrollment ↔ EnrollmentResponse</li>
 * <li><b>Request → Entity:</b> EnrollmentCreateRequest → EzkeyEnrollment</li>
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
 * <b>Usage:</b> Entity-DTO mapping for Enrollment API
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Enrollment
 * @see EnrollmentResponseDto
 * @see EnrollmentCreateRequestDto
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.WARN,componentModel = "spring")
public interface EnrollmentAdminMapper {

    /**
     * Converts an EzkeyEnrollment entity to an EnrollmentResponse DTO.
     * <p>
     * This method maps all fields from the JPA entity to the response DTO,
     * excluding sensitive information like private keys for security.
     * </p>
     *
     * @param entity the EzkeyEnrollment entity to convert
     * @return the corresponding EnrollmentResponse DTO
     * @see Enrollment
     * @see EnrollmentResponseDto
     */
    EnrollmentResponseDto toResponse(Enrollment entity);

    /**
     * Converts an EzkeyEnrollment entity to an EnrollmentCreateResponse DTO.
     * <p>
     * This method maps all fields from the JPA entity to the create response DTO,
     * used specifically for enrollment creation operations.
     * </p>
     *
     * @param entity the EzkeyEnrollment entity to convert
     * @return the corresponding EnrollmentCreateResponse DTO
     * @see Enrollment
     * @see EnrollmentCreateResponseDto
     */
    EnrollmentCreateResponseDto toCreateResponse(Enrollment entity);

    /**
     * Converts an EnrollmentResponse DTO to an EzkeyEnrollment entity.
     * <p>
     * This method maps all fields from the response DTO to the JPA entity.
     * Note that this conversion may not preserve all entity-specific fields
     * such as audit timestamps or database-generated values.
     * </p>
     *
     * @param response the EnrollmentResponse DTO to convert
     * @return the corresponding EzkeyEnrollment entity
     * @see EnrollmentResponse
     * @see EzkeyEnrollment
     */
    // EzkeyEnrollment toEntity(EnrollmentResponse response);

    /**
     * Converts a list of EzkeyEnrollment entities to a list of EnrollmentResponse DTOs.
     * <p>
     * This method applies the individual entity-to-response mapping to each
     * element in the input list, maintaining the order of elements.
     * </p>
     *
     * @param entities the list of EzkeyEnrollment entities to convert
     * @return the corresponding list of EnrollmentResponse DTOs
     * @see Enrollment
     * @see EnrollmentResponseDto
     */
    List<EnrollmentResponseDto> toResponseList(List<Enrollment> entities);

    EnrollmentCreateRequest toCreateRequest(EnrollmentCreateRequestDto request);

    EnrollmentCreateResponseDto toCreateResponseDto(EnrollmentCreateResponse response);

}