/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EncryptionLifecycleConfigDto
 * Description: Thin encryption lifecycle enable flags for Admin UI honesty chrome.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Thin encryption lifecycle flags for the Encryption Keys page (disable Rotate / re-encrypt when
 * off). Dual source: flags come from config, not from the product runtime profile name.
 *
 * @param rotationEnabled whether {@code ezkey.encryption.rotation.enabled} is on
 * @param reencryptionEnabled whether {@code ezkey.encryption.reencryption.enabled} is on
 * @since 2026
 */
@Schema(
    description =
        "Encryption lifecycle enable flags (non-secret). Global Admin only. Not a job matrix.")
public record EncryptionLifecycleConfigDto(
    @Schema(description = "Whether encryption key rotation is enabled") boolean rotationEnabled,
    @Schema(description = "Whether re-encryption is enabled") boolean reencryptionEnabled) {}
