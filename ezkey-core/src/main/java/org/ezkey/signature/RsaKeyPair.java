/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: RsaKeyPair
 * Description: Immutable container for Base64-encoded RSA key pair.
 */

package org.ezkey.signature;

/**
 * Immutable container for a Base64-encoded RSA key pair.
 *
 * <p>The private key is encoded in PKCS#8 format and the public key in X.509 format. Both values
 * are encoded using standard Base64.
 *
 * @param base64PrivateKey the Base64-encoded PKCS#8 private key
 * @param base64PublicKey the Base64-encoded X.509 public key
 * @since 2025
 */
public record RsaKeyPair(String base64PrivateKey, String base64PublicKey) {}
