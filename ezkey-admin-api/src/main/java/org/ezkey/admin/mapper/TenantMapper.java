/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Mapper: TenantMapper
 * Description: MapStruct mapper for converting between Tenant entities and DTOs.
 */

package org.ezkey.admin.mapper;

import java.util.List;
import org.ezkey.admin.dto.response.TenantResponseDto;
import org.ezkey.integration.domain.entity.Tenant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting between Tenant entities and DTOs.
 *
 * <p>This mapper provides type-safe, compile-time validated conversions between the {@link Tenant}
 * JPA entity and the admin API response DTO. It replaces the inline manual mapping previously done
 * in the controller.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Tenant
 * @see TenantResponseDto
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.WARN)
public interface TenantMapper {

  /**
   * Converts a Tenant entity to a TenantResponseDto.
   *
   * @param tenant the tenant entity
   * @return the response DTO
   */
  @Mapping(target = "operational", source = "active")
  TenantResponseDto toResponseDto(Tenant tenant);

  /**
   * Converts a list of Tenant entities to response DTOs.
   *
   * @param tenants the list of tenant entities
   * @return the list of response DTOs
   */
  List<TenantResponseDto> toResponseDtoList(List<Tenant> tenants);
}
