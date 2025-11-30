/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: Ed25519KeyPair
 * Description: Immutable container for Base64-encoded Ed25519 key pair.
 */

package org.ezkey.signature;

/**
 * Immutable container for a Base64-encoded Ed25519 key pair.
 *
 * <p>The private key is a 32-byte seed (Ed25519 standard) and the public key is a 32-byte public
 * key (Ed25519 standard). Both values are encoded using standard Base64.
 *
 * @param base64PrivateKey the Base64-encoded Ed25519 private key seed (32 bytes raw)
 * @param base64PublicKey the Base64-encoded Ed25519 public key (32 bytes raw)
 * @since 2025
 */
public record Ed25519KeyPair(String base64PrivateKey, String base64PublicKey) {}

