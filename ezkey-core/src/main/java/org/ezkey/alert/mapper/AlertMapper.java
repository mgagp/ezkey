/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AlertMapper
 * Description: MapStruct mapper for Alert entity → AlertResponseDto conversions.
 */

package org.ezkey.alert.mapper;

import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.dto.AlertResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for {@link Alert} entity to {@link AlertResponseDto} response DTO.
 *
 * @since 2026
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AlertMapper {

  /**
   * Converts an alert entity to its response DTO.
   *
   * @param alert the source entity (may be {@code null})
   * @return the corresponding DTO, or {@code null} when input is {@code null}
   */
  AlertResponseDto toResponseDto(Alert alert);
}
