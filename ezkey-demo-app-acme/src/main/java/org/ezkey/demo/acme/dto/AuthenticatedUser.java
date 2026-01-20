/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthenticatedUser
 * Description: Represents an authenticated user stored in HTTP session.
 */

package org.ezkey.demo.acme.dto;

/**
 * Represents an authenticated user stored in HTTP session.
 *
 * <p>This record contains minimal user information needed for the demo application after successful
 * EZKey authentication.
 *
 * @param username the username
 * @param displayName the display name
 * @param enrollmentId the enrollment ID used for authentication
 */
public record AuthenticatedUser(String username, String displayName, Integer enrollmentId) {}
