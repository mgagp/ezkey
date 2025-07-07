/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import org.ezkey.integration.domain.IntegrationI18nCreate;
import org.ezkey.integration.domain.entity.EzkeyIntegration;
import org.ezkey.integration.domain.entity.EzkeyIntegrationI18n;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting between Integration domain objects, DTOs, and entities.
 * <p>
 * This interface defines mapping methods for:
 * <ul>
 * <li>Mapping {@link IntegrationI18nCreate} to {@link EzkeyIntegrationI18n}</li>
 * <li>Mapping {@link IntegrationCreateRequest} to {@link EzkeyIntegration}</li>
 * <li>Mapping {@link EzkeyIntegration} to {@link IntegrationCreateResponse}</li>
 * </ul>
 * <p>
 * The generated implementation will be a Spring bean if componentModel is set to "spring".
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
public interface IntegrationServiceMapper{

    /**
     * Maps a domain I18n create object to its entity representation.
     *
     * @param integrationI18nCreate the domain I18n create object
     * @return the mapped entity with the parent integration ignored
     */
    @Mapping(target = "integration",ignore = true)
    EzkeyIntegrationI18n map(IntegrationI18nCreate integrationI18nCreate);

    /**
     * Maps a create request DTO to the EzkeyIntegration entity.
     *
     * @param dto the create request DTO
     * @return the mapped EzkeyIntegration entity (id, createdAt, and active are ignored)
     */
    @Mapping(target = "id",ignore = true)
    @Mapping(target = "createdAt",ignore = true)
    @Mapping(target = "active",ignore = true)
    EzkeyIntegration toEntity(IntegrationCreateRequest dto);

    /**
     * Maps a saved EzkeyIntegration entity to a response DTO.
     *
     * @param domResponse the saved EzkeyIntegration entity
     * @return the response DTO containing the integration's information
     */
    IntegrationCreateResponse toCreateResponse(EzkeyIntegration domResponse);
}