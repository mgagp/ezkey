/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the project root for full
 * license information.
 *
 * IntegrationService
 * Mapper Description: Mapper for integration service.
 */

package org.ezkey.integration.mapper;

import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.entity.Integration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between Integration domain objects and entities.
 *
 * <p>This interface defines mapping methods for:
 *
 * <ul>
 *   <li>Mapping {@link IntegrationCreateRequest} to {@link Integration}
 *   <li>Mapping {@link Integration} to {@link IntegrationCreateResponse}
 * </ul>
 *
 * <p>The generated implementation will be a Spring bean if componentModel is set to "spring".
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Mapper(componentModel = "spring")
public interface IntegrationServiceMapper {

  /**
   * Maps create request → entity. Ignores {@code id}, {@code createdAt}, {@code lifecycleStatus};
   * those use field init / service assignment. {@code tenant}, {@code isSystemIntegration}, {@code
   * createdByAdmin} set by service.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "active", ignore = true)
  @Mapping(target = "lifecycleStatus", ignore = true)
  @Mapping(target = "tenant", ignore = true)
  @Mapping(target = "isSystemIntegration", ignore = true)
  @Mapping(target = "createdByAdmin", ignore = true)
  Integration toEntity(IntegrationCreateRequest dto);

  /**
   * Maps a saved EzkeyIntegration entity to a response DTO.
   *
   * @param integrationEntity the saved EzkeyIntegration entity
   * @return the response DTO containing the integration's information
   */
  IntegrationCreateResponse toCreateResponse(Integration integrationEntity);
}
