//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyEnrollmentKeyStore (iOS)
// Description: Enrollment EC P-256 keys. Secure Enclave on devices (tier STRONG); software key on
//              the simulator or when no Secure Enclave exists (tier NONE). Each key is a
//              Keychain generic-password item (WhenUnlockedThisDeviceOnly) whose value is a 1-byte
//              kind tag followed by the SE `dataRepresentation` or the software `rawRepresentation`.
// Security Context: Keychain items survive app uninstall. A lazy, fail-closed purge removes every
//              enrollment key left by a previous installation before the first key operation
//              (UserDefaults marker, wiped on uninstall). See plans/mobile_ios_phase_4_*.plan.md.
//

import CryptoKit
import Foundation
import Security

enum EzkeyKeyStoreError: Error, CustomStringConvertible {
  case keychain(OSStatus, String)
  case purgeFailed(OSStatus)
  case secureEnclave(Error)
  case corruptItem(String)

  var description: String {
    switch self {
    case .keychain(let status, let operation):
      return "Keychain \(operation) failed (OSStatus \(status))"
    case .purgeFailed(let status): return "Reinstall key purge failed (OSStatus \(status))"
    case .secureEnclave(let error): return "Secure Enclave error: \(error)"
    case .corruptItem(let account): return "Unreadable enrollment key item \(account)"
    }
  }
}

/// A loaded enrollment private key.
enum EzkeyEnrollmentKey {
  case secureEnclave(SecureEnclave.P256.Signing.PrivateKey)
  case software(P256.Signing.PrivateKey)

  var publicKey: P256.Signing.PublicKey {
    switch self {
    case .secureEnclave(let key): return key.publicKey
    case .software(let key): return key.publicKey
    }
  }

  /// Client-reported tier, matching Auth API DevicePrivateKeyStorageTier (plan D1/D2).
  var storageTier: String {
    switch self {
    case .secureEnclave: return "STRONG"
    case .software: return "NONE"
    }
  }

  func signature(for data: Data) throws -> P256.Signing.ECDSASignature {
    switch self {
    case .secureEnclave(let key): return try key.signature(for: data)
    case .software(let key): return try key.signature(for: data)
    }
  }
}

final class EzkeyEnrollmentKeyStore {
  static let defaultService = "org.ezkey.mobile.enrollment-key"
  static let installMarkerKey = "ezkeyCryptoInstallMarker"

  private static let kindSecureEnclave: UInt8 = 0x01
  private static let kindSoftware: UInt8 = 0x02

  let service: String
  private let defaults: UserDefaults
  private let secureEnclaveAvailable: Bool
  private let deleteAllItems: (String) -> OSStatus
  private let lock = NSLock()
  private var purged = false

  /// - Parameters:
  ///   - service: Keychain service holding the enrollment keys (tests use their own).
  ///   - defaults: holds the install marker (tests use their own suite).
  ///   - secureEnclaveAvailable: injectable for tests; production reads the hardware.
  ///   - deleteAllItems: injectable purge primitive (tests simulate failure).
  init(
    service: String = EzkeyEnrollmentKeyStore.defaultService,
    defaults: UserDefaults = .standard,
    secureEnclaveAvailable: Bool = EzkeyEnrollmentKeyStore.useSecureEnclave,
    deleteAllItems: @escaping (String) -> OSStatus = EzkeyEnrollmentKeyStore.deleteAllKeychainItems(
      service:)
  ) {
    self.service = service
    self.defaults = defaults
    self.secureEnclaveAvailable = secureEnclaveAvailable
    self.deleteAllItems = deleteAllItems
  }

  /// D2 (revised 2026-11-01): the iOS 27 simulator on Apple Silicon reports a working Secure
  /// Enclave, but a simulator must never claim tier STRONG. Simulator → software key, tier NONE
  /// (same honesty as the Android emulator). The SE path is still exercised by XCTest via injection.
  static var useSecureEnclave: Bool {
    #if targetEnvironment(simulator)
      return false
    #else
      return SecureEnclave.isAvailable
    #endif
  }

  static func account(for enrollmentId: String) -> String {
    "ezkey_enrollment_\(enrollmentId)"
  }

  // MARK: - Reinstall purge (lazy, once per process, fail closed)

  func ensurePurged() throws {
    lock.lock()
    defer { lock.unlock() }
    if purged { return }
    if defaults.string(forKey: Self.installMarkerKey) == nil {
      let status = deleteAllItems(service)
      guard status == errSecSuccess || status == errSecItemNotFound else {
        throw EzkeyKeyStoreError.purgeFailed(status)
      }
      defaults.set(ISO8601DateFormatter().string(from: Date()), forKey: Self.installMarkerKey)
    }
    purged = true
  }

  static func deleteAllKeychainItems(service: String) -> OSStatus {
    SecItemDelete(
      [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
      ] as CFDictionary)
  }

  // MARK: - Key lifecycle

  /// Creates the key unless it already exists ("exists → true", Android parity).
  func generateIfAbsent(enrollmentId: String) throws {
    try ensurePurged()
    if try loadItem(enrollmentId: enrollmentId) != nil { return }

    var value = Data()
    if secureEnclaveAvailable {
      // D2: on hardware with a Secure Enclave, failure is fatal (no silent software downgrade).
      do {
        var cfError: Unmanaged<CFError>?
        guard
          let access = SecAccessControlCreateWithFlags(
            nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, .privateKeyUsage, &cfError)
        else {
          if let error = cfError?.takeRetainedValue() {
            throw error as Error
          }
          throw EzkeyKeyStoreError.keychain(errSecParam, "access control")
        }
        let key = try SecureEnclave.P256.Signing.PrivateKey(accessControl: access)
        value.append(Self.kindSecureEnclave)
        value.append(key.dataRepresentation)
      } catch {
        throw EzkeyKeyStoreError.secureEnclave(error)
      }
    } else {
      value.append(Self.kindSoftware)
      value.append(P256.Signing.PrivateKey().rawRepresentation)
    }

    let status = SecItemAdd(
      [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: Self.account(for: enrollmentId),
        kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        kSecValueData as String: value,
      ] as CFDictionary, nil)
    // A concurrent generate already stored a key: same "exists" semantics.
    guard status == errSecSuccess || status == errSecDuplicateItem else {
      throw EzkeyKeyStoreError.keychain(status, "add")
    }
  }

  /// Returns nil when no key exists for the enrollment.
  func loadKey(enrollmentId: String) throws -> EzkeyEnrollmentKey? {
    try ensurePurged()
    guard let item = try loadItem(enrollmentId: enrollmentId) else { return nil }
    let account = Self.account(for: enrollmentId)
    guard let kind = item.first else { throw EzkeyKeyStoreError.corruptItem(account) }
    let body = item.dropFirst()
    do {
      switch kind {
      case Self.kindSecureEnclave:
        return .secureEnclave(try SecureEnclave.P256.Signing.PrivateKey(dataRepresentation: body))
      case Self.kindSoftware:
        return .software(try P256.Signing.PrivateKey(rawRepresentation: body))
      default:
        throw EzkeyKeyStoreError.corruptItem(account)
      }
    } catch let error as EzkeyKeyStoreError {
      throw error
    } catch {
      throw EzkeyKeyStoreError.corruptItem(account)
    }
  }

  /// Deletes the key if present. Missing key is success.
  func delete(enrollmentId: String) throws {
    try ensurePurged()
    let status = SecItemDelete(
      [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: Self.account(for: enrollmentId),
      ] as CFDictionary)
    guard status == errSecSuccess || status == errSecItemNotFound else {
      throw EzkeyKeyStoreError.keychain(status, "delete")
    }
  }

  private func loadItem(enrollmentId: String) throws -> Data? {
    var result: CFTypeRef?
    let status = SecItemCopyMatching(
      [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrService as String: service,
        kSecAttrAccount as String: Self.account(for: enrollmentId),
        kSecReturnData as String: true,
        kSecMatchLimit as String: kSecMatchLimitOne,
      ] as CFDictionary, &result)
    if status == errSecItemNotFound { return nil }
    guard status == errSecSuccess, let data = result as? Data else {
      throw EzkeyKeyStoreError.keychain(status, "read")
    }
    return data
  }
}
