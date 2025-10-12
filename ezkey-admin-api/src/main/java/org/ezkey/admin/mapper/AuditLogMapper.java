/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: AuditLogMapper
 * Description: MapStruct mapper for AuditLog entity to DTO conversions
 */

package org.ezkey.admin.mapper;

import org.ezkey.admin.dto.audit.AuditLogDto;
import org.ezkey.audit.domain.entity.AuditLog;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for converting AuditLog entities to DTOs.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    /**
     * Convert AuditLog entity to AuditLogDto.
     *
     * @param auditLog the entity
     * @return the DTO
     */
    AuditLogDto toDto(AuditLog auditLog);
}
