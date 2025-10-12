/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root
 * for full license information.
 *
 * Mapper: LogoServiceMapper
 * Description: MapStruct mapper for Logo entity conversions.
 */

package org.ezkey.integration.mapper;

import java.util.List;

import org.ezkey.integration.domain.LogoCreateRequest;
import org.ezkey.integration.domain.LogoResponse;
import org.ezkey.integration.domain.entity.Logo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Logo entity conversions.
 * <p>
 * This mapper provides conversion methods between Logo entities and their
 * corresponding DTOs for service layer operations.
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
 */
@Mapper(componentModel = "spring")
public interface LogoServiceMapper {

    /**
     * Converts a LogoCreateRequest to a Logo entity.
     *
     * @param request the logo creation request
     * @return the Logo entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Logo toEntity(LogoCreateRequest request);

    /**
     * Converts a Logo entity to a LogoResponse.
     *
     * @param logo the Logo entity
     * @return the logo response DTO
     */
    LogoResponse toResponse(Logo logo);

    /**
     * Converts a list of Logo entities to a list of LogoResponse DTOs.
     *
     * @param logos the list of Logo entities
     * @return the list of logo response DTOs
     */
    List<LogoResponse> toResponseList(List<Logo> logos);
}
