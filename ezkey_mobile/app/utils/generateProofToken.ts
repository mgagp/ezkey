/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: generateProofToken
 * Description: Device proof token for auth flows (e.g. pending poll), generated in native code.
 */

import {nativeCrypto} from '../services/crypto/nativeCrypto';

/**
 * Resolves with a device proof token using the same algorithm and wire format as {@code
 * SignatureService.generateProofToken()} in ezkey-core (32 random bytes + 16 salt bytes, URL-safe
 * Base64 without padding, {@code randomPart + "." + saltPart}).
 *
 * <p>Randomness comes from the platform CSPRNG via {@link EzkeyCryptoModule#generateProofToken}
 * (Android {@code SecureRandom}, iOS {@code SecRandomCopyBytes}), not from {@code
 * react-native-get-random-values}.
 *
 * @return A non-empty proof token string.
 * @see ../../../docs/CRYPTO.md (Proof tokens — canonical description)
 */
export async function generateProofToken(): Promise<string> {
  return nativeCrypto.generateProofToken();
}
