/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.enrollment.domain;

/**
 * Client-reported tier describing how the device private key material is protected on the client.
 *
 * <p>Asserted by the device at enrollment verification time (not cryptographically attested by the
 * server in the current model). Maps naturally to Android Keystore with optional StrongBox; iOS can
 * map Keychain vs Secure Enclave to {@link #STANDARD} vs {@link #STRONG}.
 */
public enum DevicePrivateKeyStorageTier {

  /**
   * Private key is not stored in hardware-backed keystore (e.g. demo device, simulators, or
   * explicit software storage).
   */
  NONE,

  /**
   * Private key in platform hardware-backed keystore without isolated StrongBox / Secure Enclave
   * class protection.
   */
  STANDARD,

  /**
   * Private key in StrongBox (Android) or equivalent strongly isolated hardware (e.g. Secure
   * Enclave on iOS when mapped here).
   */
  STRONG
}
