/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptContext
 * Description: Optional business context metadata for an authentication attempt.
 */

package org.ezkey.sdk;

/**
 * Optional business context metadata to attach to an authentication attempt.
 *
 * <p>Contextual authentication transforms EZKey from a binary approve/deny MFA into a
 * business-context-aware request by surfacing a title and message on the mobile device, so the user
 * understands <em>exactly</em> what they are approving before tapping the button.
 *
 * <p>All fields are optional (null-safe). When no context is provided the mobile device displays
 * the standard approval screen. When context is provided it is rendered as a card with a title and
 * message.
 *
 * <h2>Usage example</h2>
 *
 * <pre>{@code
 * // Payment batch sign-off — structured context message
 * var context = new AuthAttemptContext(
 *     "Payment Approval",
 *     "Authorize payment batch #1497 to Acme Corp for $1,400.");
 *
 * client.createAuthAttempt(enrollmentId, false, context);
 *
 * // Simple document sign-off
 * var context = new AuthAttemptContext(
 *     "Document Signature Request",
 *     "Please approve the signing of the NDA with partner Acme Corp.");
 *
 * client.createAuthAttemptByUserIdentifier(username, false, context);
 * }</pre>
 *
 * @param contextTitle short heading displayed as the card title on the mobile device (max 200
 *     chars). {@code null} if no title context is needed.
 * @param contextMessage descriptive message explaining what the user is approving (max 2000 chars).
 *     {@code null} if no message context is needed.
 * @since 2025
 */
public record AuthAttemptContext(String contextTitle, String contextMessage) {}
