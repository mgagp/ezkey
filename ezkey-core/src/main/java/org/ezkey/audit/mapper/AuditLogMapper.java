/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuditLogMapper
 * Description: MapStruct mapper for audit log entity-DTO conversions.
 */

package org.ezkey.audit.mapper;

import java.util.List;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for audit log entity-DTO conversions.
 *
 * <p>Provides clean mapping between AuditLog entity and DTOs for API responses.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuditLogMapper {

  /**
   * Convert AuditLog entity to response DTO.
   *
   * @param auditLog the entity to convert
   * @return response DTO
   */
  AuditLogResponseDto toResponseDto(AuditLog auditLog);

  /**
   * Convert list of AuditLog entities to response DTOs.
   *
   * @param auditLogs the entities to convert
   * @return list of response DTOs
   */
  List<AuditLogResponseDto> toResponseDtoList(List<AuditLog> auditLogs);
}
