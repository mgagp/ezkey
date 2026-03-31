/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: Ed25519KeyPair
 * Description: Immutable container for integration Ed25519 keys (JDK-only).
 */

package org.ezkey.signature;

/**
 * Immutable container for a Base64-encoded Ed25519 key pair used for integration signing.
 *
 * <p>The private key is PKCS#8 DER, Base64 (standard alphabet). The public key is the raw 32-byte
 * Ed25519 public key, Base64URL without padding (wire format for APIs and mobile clients).
 *
 * @param base64PrivateKey PKCS#8 private key, standard Base64
 * @param base64UrlPublicKey raw 32-byte public key, Base64URL without padding
 * @since 2025
 */
public record Ed25519KeyPair(String base64PrivateKey, String base64UrlPublicKey) {}
