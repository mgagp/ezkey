/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuditChainCheckpointMapper
 * Description: MapStruct mapper for audit chain checkpoint entity to response DTO.
 */

package org.ezkey.audit.mapper;

import org.ezkey.audit.dto.AuditChainCheckpointResponseDto;
import org.ezkey.audit.dto.CheckpointType;
import org.ezkey.audit.integrity.AuditChainCheckpoint;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for audit chain checkpoint entity to response DTO.
 *
 * <p>Maps {@link AuditChainCheckpoint} (entity with {@code checkpointType} as String) to {@link
 * AuditChainCheckpointResponseDto} (API with {@link CheckpointType} enum). Explicit conversion for
 * checkpointType is required per MapStruct enum rules.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditChainCheckpointMapper {

  /**
   * Converts an audit chain checkpoint entity to the response DTO.
   *
   * @param checkpoint the entity to convert
   * @return the response DTO
   */
  AuditChainCheckpointResponseDto toResponseDto(AuditChainCheckpoint checkpoint);

  /**
   * Converts the entity's checkpoint_type string to the API enum.
   *
   * @param checkpointType the string value from the entity (e.g. "REGULAR", "ARCHIVE_SEAL")
   * @return the corresponding enum, or null if input is null
   */
  default CheckpointType stringToCheckpointType(String checkpointType) {
    if (checkpointType == null) {
      return null;
    }
    return CheckpointType.valueOf(checkpointType);
  }
}
