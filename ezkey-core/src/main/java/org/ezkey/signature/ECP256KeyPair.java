/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: ECP256KeyPair
 * Description: Immutable container for Base64-encoded EC P-256 key pair.
 */

package org.ezkey.signature;

/**
 * Immutable container for a Base64-encoded EC P-256 key pair.
 *
 * <p>The private key is in PKCS#8 format and the public key is in X.509 format
 * (SubjectPublicKeyInfo). Both values are encoded using standard Base64.
 *
 * @param base64PrivateKey the Base64-encoded EC P-256 private key (PKCS#8 format)
 * @param base64PublicKey the Base64-encoded EC P-256 public key (X.509 format)
 * @since 2025
 */
public record ECP256KeyPair(String base64PrivateKey, String base64PublicKey) {}
