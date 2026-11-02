//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2025 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyCryptoModule (iOS)
// Description: iOS counterpart of the Android EzkeyCryptoModule native bridge (same method names,
//              wire formats and EZK_* error codes). EC P-256 enrollment keys in the Secure Enclave
//              (software key on the simulator only, tier NONE), low-S DER signatures, Ed25519
//              integration-signature verification, proof tokens, build info, and LocalAuthentication
//              as a gate before signing (same model as Android BiometricPrompt).
//              Not implemented on purpose: sealSecret / unsealSecret / deleteAllSealKeys (iOS
//              secrets use the Keychain via react-native-keychain; JS fallbacks apply).
// Security Context: docs/MOBILE_CRYPTO_REFERENCE.md, docs/CRYPTO.md,
//              plans/mobile_ios_phase_4_native_crypto_parity.plan.md
//

import CryptoKit
import Foundation
import LocalAuthentication
import React
import Security

@objc(EzkeyCryptoModule)
final class EzkeyCryptoModule: NSObject {

  static let biometricPromptSubtitle =
    "Confirm with biometrics or your device PIN, pattern, or password"
  static let securityDowngradePromptSubtitle =
    "Use your device to turn off approval confirmation"

  private let store: EzkeyEnrollmentKeyStore
  private let canAuthenticate: () -> Bool
  private let infoDictionary: () -> [String: Any]?
  private let queue = DispatchQueue(label: "org.ezkey.mobile.crypto")

  override convenience init() {
    self.init(store: EzkeyEnrollmentKeyStore())
  }

  init(
    store: EzkeyEnrollmentKeyStore,
    canAuthenticate: @escaping () -> Bool = EzkeyCryptoModule.deviceOwnerAuthenticationAvailable,
    infoDictionary: @escaping () -> [String: Any]? = { Bundle.main.infoDictionary }
  ) {
    self.store = store
    self.canAuthenticate = canAuthenticate
    self.infoDictionary = infoDictionary
    super.init()
  }

  @objc static func requiresMainQueueSetup() -> Bool {
    false
  }

  /// Serial queue: every exported method runs on it (purge-once and Keychain access serialized).
  @objc var methodQueue: DispatchQueue {
    queue
  }

  /// `isDebugBuild` mirrors Android `BuildConfig.DEBUG`: true only for the Xcode Debug
  /// configuration (`DEBUG` Swift compilation condition). Test-only UI such as the F2a controlled
  /// enrollment bypass is gated on it (see app/config/buildFlavor.ts). Release builds report false.
  @objc func constantsToExport() -> [AnyHashable: Any] {
    #if DEBUG
    let isDebugBuild = true
    #else
    let isDebugBuild = false
    #endif
    return ["isDebugBuild": isDebugBuild]
  }

  // MARK: - Enrollment keys

  @objc(generateEnrollmentKeyPair:securityLevel:resolve:reject:)
  func generateEnrollmentKeyPair(
    _ enrollmentId: String, securityLevel: String?,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    // securityLevel is accepted and unused, as on Android.
    do {
      try store.generateIfAbsent(enrollmentId: enrollmentId)
      resolve(true)
    } catch {
      reject(EzkeyCryptoErrorCode.keyGeneration, "\(error)", error)
    }
  }

  @objc(getPublicKey:resolve:reject:)
  func getPublicKey(
    _ enrollmentId: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    withKey(enrollmentId, errorCode: EzkeyCryptoErrorCode.publicKey, reject: reject) { key in
      // X.509 SubjectPublicKeyInfo DER, standard Base64 (Android publicKey.encoded parity).
      resolve(key.publicKey.derRepresentation.base64EncodedString())
    }
  }

  @objc(getEnrollmentPrivateKeyStorageTier:resolve:reject:)
  func getEnrollmentPrivateKeyStorageTier(
    _ enrollmentId: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    withKey(enrollmentId, errorCode: EzkeyCryptoErrorCode.storageTier, reject: reject) { key in
      resolve(key.storageTier)
    }
  }

  @objc(sign:data:resolve:reject:)
  func sign(
    _ enrollmentId: String, data: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    withKey(enrollmentId, errorCode: EzkeyCryptoErrorCode.sign, reject: reject) { key in
      do {
        resolve(try Self.signLowS(key, data: data))
      } catch {
        reject(EzkeyCryptoErrorCode.sign, "\(error)", error)
      }
    }
  }

  @objc(deleteKeyPair:resolve:reject:)
  func deleteKeyPair(
    _ enrollmentId: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    do {
      try store.delete(enrollmentId: enrollmentId)
      resolve(true)
    } catch {
      reject(EzkeyCryptoErrorCode.delete, "\(error)", error)
    }
  }

  /// SHA256withECDSA over UTF-8, low-S normalized, DER, standard Base64 (Android parity).
  static func signLowS(_ key: EzkeyEnrollmentKey, data: String) throws -> String {
    let signature = try key.signature(for: Data(data.utf8))
    let lowS = EzkeyCryptoCodec.normalizeLowS(signature.rawRepresentation)
    return try P256.Signing.ECDSASignature(rawRepresentation: lowS).derRepresentation
      .base64EncodedString()
  }

  // MARK: - Local authentication (gate before signing, as on Android)

  @objc(canUseProtectedSigning:reject:)
  func canUseProtectedSigning(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    resolve(canAuthenticate())
  }

  @objc(signWithAuthentication:data:resolve:reject:)
  func signWithAuthentication(
    _ enrollmentId: String, data: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    // Order (Android parity): availability → key exists → prompt → sign. Never prompt for a
    // missing key.
    guard canAuthenticate() else {
      reject(EzkeyCryptoErrorCode.authUnavailable,
             "Device confirmation is not available on this phone", nil)
      return
    }
    withKey(enrollmentId, errorCode: EzkeyCryptoErrorCode.sign, reject: reject) { _ in
      LAContext().evaluatePolicy(
        .deviceOwnerAuthentication, localizedReason: Self.biometricPromptSubtitle
      ) { [weak self] success, error in
        guard let self else { return }
        self.queue.async {
          guard success else {
            reject(EzkeyCryptoCodec.authErrorCode(for: error), error?.localizedDescription, error)
            return
          }
          // Reload after the prompt: the key may have been deleted meanwhile.
          self.withKey(enrollmentId, errorCode: EzkeyCryptoErrorCode.sign, reject: reject) { key in
            do {
              resolve(try Self.signLowS(key, data: data))
            } catch {
              reject(EzkeyCryptoErrorCode.sign, "\(error)", error)
            }
          }
        }
      }
    }
  }

  @objc(authenticateSecurityPreferenceDowngrade:reject:)
  func authenticateSecurityPreferenceDowngrade(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    guard canAuthenticate() else {
      reject(EzkeyCryptoErrorCode.authUnavailable,
             "Device confirmation is not available on this phone", nil)
      return
    }
    LAContext().evaluatePolicy(
      .deviceOwnerAuthentication, localizedReason: Self.securityDowngradePromptSubtitle
    ) { success, error in
      if success {
        resolve(true)
      } else {
        reject(EzkeyCryptoCodec.authErrorCode(for: error), error?.localizedDescription, error)
      }
    }
  }

  static func deviceOwnerAuthenticationAvailable() -> Bool {
    var error: NSError?
    return LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: &error)
  }

  // MARK: - Integration signature, proof token, build info

  /// Ed25519 over UTF-8. Resolves false (never rejects) on malformed input, as on Android.
  @objc(verify:signatureBase64:publicKeyBase64:resolve:reject:)
  func verify(
    _ data: String, signatureBase64: String, publicKeyBase64: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    resolve(Self.verifyEd25519(data: data, signatureBase64: signatureBase64,
                               publicKeyBase64: publicKeyBase64))
  }

  static func verifyEd25519(data: String, signatureBase64: String, publicKeyBase64: String) -> Bool {
    guard let pub = EzkeyCryptoCodec.decodeFlexibleBase64(publicKeyBase64),
          let sig = EzkeyCryptoCodec.decodeFlexibleBase64(signatureBase64),
          pub.count == 32, sig.count == 64,
          let key = try? Curve25519.Signing.PublicKey(rawRepresentation: pub)
    else {
      return false
    }
    return key.isValidSignature(sig, for: Data(data.utf8))
  }

  /// Same wire format as SignatureService.generateProofToken(): base64url(32 random) "." base64url(16 salt).
  @objc(generateProofToken:reject:)
  func generateProofToken(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    var random = [UInt8](repeating: 0, count: 32)
    var salt = [UInt8](repeating: 0, count: 16)
    let s1 = SecRandomCopyBytes(kSecRandomDefault, random.count, &random)
    let s2 = SecRandomCopyBytes(kSecRandomDefault, salt.count, &salt)
    guard s1 == errSecSuccess, s2 == errSecSuccess else {
      reject(EzkeyCryptoErrorCode.proofToken, "SecRandomCopyBytes failed", nil)
      return
    }
    resolve(EzkeyCryptoCodec.base64UrlNoPadding(Data(random)) + "."
            + EzkeyCryptoCodec.base64UrlNoPadding(Data(salt)))
  }

  /// Written into the built Info.plist by the "Ezkey build info" Run Script phase.
  @objc(getBuildTimestamp:reject:)
  func getBuildTimestamp(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    resolveInfoValue("EzkeyBuildTimestamp", errorCode: EzkeyCryptoErrorCode.buildTimestamp,
                     resolve: resolve, reject: reject)
  }

  @objc(getGitShortSha:reject:)
  func getGitShortSha(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    resolveInfoValue("EzkeyGitShortSha", errorCode: EzkeyCryptoErrorCode.gitSha,
                     resolve: resolve, reject: reject)
  }

  // MARK: - Helpers

  private func resolveInfoValue(
    _ key: String, errorCode: String,
    resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock
  ) {
    guard let value = infoDictionary()?[key] as? String, !value.isEmpty else {
      reject(errorCode, "\(key) missing from the app Info.plist", nil)
      return
    }
    resolve(value)
  }

  /// Loads the key or rejects: EZK_KEY_NOT_FOUND when absent, `errorCode` on any other failure
  /// (including a reinstall purge that has not succeeded — fail closed).
  private func withKey(
    _ enrollmentId: String, errorCode: String, reject: RCTPromiseRejectBlock,
    _ body: (EzkeyEnrollmentKey) -> Void
  ) {
    let key: EzkeyEnrollmentKey?
    do {
      key = try store.loadKey(enrollmentId: enrollmentId)
    } catch {
      reject(errorCode, "\(error)", error)
      return
    }
    guard let key else {
      reject(EzkeyCryptoErrorCode.notFound, "Key pair not found for enrollment \(enrollmentId)", nil)
      return
    }
    body(key)
  }
}
