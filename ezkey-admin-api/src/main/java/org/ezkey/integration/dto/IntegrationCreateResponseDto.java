/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationCreateResponseDto
 * Description: Response DTO for integration creation in admin API.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for integration creation in admin API.
 *
 * <p>This DTO represents the response data returned when an integration is successfully created
 * through the admin API. It contains the unique identifier of the newly created integration,
 * allowing clients to reference or further interact with the integration resource.
 *
 * <p><b>Usage Context:</b> Returned by admin API when creating integrations. The integration ID can
 * be used for subsequent operations such as creating API keys, enrollments, or managing integration
 * settings.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>
 * {
 *   "id": 42
 * }
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param code Unique business identifier code for the integration (required, must be unique per
 *     tenant)
 * @param id Unique identifier of the newly created integration used to reference this integration
 *     in subsequent operations
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.IntegrationCreateResponse
 * @see IntegrationCreateRequestDto
 */
@Schema(description = "Response DTO containing created integration details")
public record IntegrationCreateResponseDto(
    @Schema(
            description = "Unique business identifier code for the integration",
            example = "web-portal")
        String code,
    @Schema(description = "Unique identifier of the newly created integration", example = "42")
        Integer id) {}
