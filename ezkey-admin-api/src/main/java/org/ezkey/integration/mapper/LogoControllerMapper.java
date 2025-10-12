/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: LogoControllerMapper
 * Description: MapStruct mapper for Logo controller DTOs.
 */

package org.ezkey.integration.mapper;

import java.util.List;

import org.ezkey.integration.domain.LogoCreateRequest;
import org.ezkey.integration.domain.LogoResponse;
import org.ezkey.integration.dto.LogoCreateRequestDto;
import org.ezkey.integration.dto.LogoResponseDto;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for Logo controller DTOs.
 * <p>
 * This mapper provides conversion methods between Logo service DTOs and
 * controller DTOs for the admin API.
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
public interface LogoControllerMapper {

    /**
     * Converts a LogoCreateRequestDto to a LogoCreateRequest.
     *
     * @param dto the controller request DTO
     * @return the service request DTO
     */
    LogoCreateRequest toCreateRequest(LogoCreateRequestDto dto);

    /**
     * Converts a LogoResponse to a LogoResponseDto.
     *
     * @param response the service response DTO
     * @return the controller response DTO
     */
    LogoResponseDto toResponseDto(LogoResponse response);

    /**
     * Converts a list of LogoResponse to a list of LogoResponseDto.
     *
     * @param responses the list of service response DTOs
     * @return the list of controller response DTOs
     */
    List<LogoResponseDto> toResponseDtoList(List<LogoResponse> responses);
}
