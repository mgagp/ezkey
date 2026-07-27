/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for audit log entity-DTO conversions.
 *
 * <p>Provides clean mapping between AuditLog entity and DTOs for API responses.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
  @Mapping(target = "adminUsername", ignore = true)
  @Mapping(target = "targetAdminUsername", ignore = true)
  @Mapping(target = "integrationName", ignore = true)
  @Mapping(target = "enrollmentName", ignore = true)
  @Mapping(target = "tenantName", ignore = true)
  AuditLogResponseDto toResponseDto(AuditLog auditLog);

  /**
   * Convert list of AuditLog entities to response DTOs.
   *
   * @param auditLogs the entities to convert
   * @return list of response DTOs
   */
  List<AuditLogResponseDto> toResponseDtoList(List<AuditLog> auditLogs);

  /**
   * Converts an audit log entity to a response DTO with optional display labels for admin
   * investigation surfaces.
   *
   * @param auditLog the entity to convert
   * @param adminUsername actor admin username, or null
   * @param targetAdminUsername subject admin username, or null
   * @param integrationName integration display name, or null
   * @param enrollmentName enrollment display name, or null
   * @param tenantName tenant display name, or null
   * @return response DTO with enrichment fields set when provided
   */
  default AuditLogResponseDto toResponseDtoWithLabels(
      AuditLog auditLog,
      String adminUsername,
      String targetAdminUsername,
      String integrationName,
      String enrollmentName,
      String tenantName) {
    AuditLogResponseDto dto = toResponseDto(auditLog);
    dto.setAdminUsername(adminUsername);
    dto.setTargetAdminUsername(targetAdminUsername);
    dto.setIntegrationName(integrationName);
    dto.setEnrollmentName(enrollmentName);
    dto.setTenantName(tenantName);
    return dto;
  }
}
