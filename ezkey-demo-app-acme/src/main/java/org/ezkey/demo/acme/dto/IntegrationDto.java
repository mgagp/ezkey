/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: IntegrationDto
 * Description: Data Transfer Object for Integration entities from Ezkey Admin API.
 */

package org.ezkey.demo.acme.dto;

/**
 * Data Transfer Object for Integration entities from Ezkey Admin API.
 * <p>
 * This DTO represents an integration record as returned by the Ezkey Admin API.
 * An integration defines a protected application or system that uses Ezkey
 * for multi-factor authentication.
 * </p>
 *
 * <p>
 * <b>Integration Concept:</b> Represents a specific application or service
 * that has been configured to use Ezkey MFA. Each integration has its own
 * cryptographic keys and can have multiple enrollments (users/devices).
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class IntegrationDto {

    // DTO supprimé, utiliser org.ezkey.demo.acme.generated.dto.IntegrationResponseDto à la place
}