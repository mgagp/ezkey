//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Bridge-level tests: call the @objc methods exactly as React Native does (resolve/reject blocks)
// against an isolated Keychain service and UserDefaults suite. Also proves the reinstall purge,
// the build-info Run Script, and prints a vector for the Crypto API /validate evidence (plan D6).
//

import CryptoKit
import React
import XCTest

@testable import EzkeyMobile

final class EzkeyCryptoModuleTests: XCTestCase {

  private let service = "org.ezkey.mobile.enrollment-key.tests"
  private let suiteName = "org.ezkey.mobile.tests.crypto"
  private var defaults: UserDefaults!

  private enum Outcome: Equatable {
    case resolved(String)
    case rejected(String)
  }

  override func setUpWithError() throws {
    try super.setUpWithError()
    defaults = try XCTUnwrap(UserDefaults(suiteName: suiteName))
    defaults.removePersistentDomain(forName: suiteName)
    _ = EzkeyEnrollmentKeyStore.deleteAllKeychainItems(service: service)
  }

  override func tearDown() {
    _ = EzkeyEnrollmentKeyStore.deleteAllKeychainItems(service: service)
    defaults.removePersistentDomain(forName: suiteName)
    super.tearDown()
  }

  private func makeModule(
    deleteAll: @escaping (String) -> OSStatus = EzkeyEnrollmentKeyStore.deleteAllKeychainItems(
      service:)
  ) -> EzkeyCryptoModule {
    let store = EzkeyEnrollmentKeyStore(
      service: service, defaults: defaults, secureEnclaveAvailable: false, deleteAllItems: deleteAll
    )
    return EzkeyCryptoModule(store: store, canAuthenticate: { true })
  }

  /// Runs a bridge call whose resolve/reject fire synchronously.
  private func call(
    _ invoke: (@escaping RCTPromiseResolveBlock, @escaping RCTPromiseRejectBlock) -> Void
  ) -> Outcome? {
    var outcome: Outcome?
    invoke(
      { value in outcome = .resolved("\(value ?? "nil")") },
      { code, _, _ in outcome = .rejected(code ?? "nil") })
    return outcome
  }

  // MARK: Missing key semantics (RED before step 4: methods did not exist)

  func testMissingKeyCodes() {
    let module = makeModule()
    XCTAssertEqual(
      call { module.getPublicKey("absent", resolve: $0, reject: $1) },
      .rejected("EZK_KEY_NOT_FOUND"))
    XCTAssertEqual(
      call { module.getEnrollmentPrivateKeyStorageTier("absent", resolve: $0, reject: $1) },
      .rejected("EZK_KEY_NOT_FOUND"))
    XCTAssertEqual(
      call { module.sign("absent", data: "x", resolve: $0, reject: $1) },
      .rejected("EZK_KEY_NOT_FOUND"))
    // Never prompts for a missing key: rejects before LAContext.
    XCTAssertEqual(
      call { module.signWithAuthentication("absent", data: "x", resolve: $0, reject: $1) },
      .rejected("EZK_KEY_NOT_FOUND"))
    XCTAssertEqual(
      call { module.deleteKeyPair("absent", resolve: $0, reject: $1) }, .resolved("true"))
  }

  func testAuthenticationUnavailableRejectsBeforeKeyLookup() {
    let store = EzkeyEnrollmentKeyStore(
      service: service, defaults: defaults, secureEnclaveAvailable: false)
    let module = EzkeyCryptoModule(store: store, canAuthenticate: { false })
    XCTAssertEqual(call { module.canUseProtectedSigning($0, reject: $1) }, .resolved("false"))
    XCTAssertEqual(
      call { module.signWithAuthentication("absent", data: "x", resolve: $0, reject: $1) },
      .rejected("EZK_AUTH_UNAVAILABLE"))
    XCTAssertEqual(
      call { module.authenticateSecurityPreferenceDowngrade($0, reject: $1) },
      .rejected("EZK_AUTH_UNAVAILABLE"))
  }

  func testVerifyNeverRejectsOnMalformedInput() {
    let module = makeModule()
    XCTAssertEqual(
      call {
        module.verify("d", signatureBase64: "@@", publicKeyBase64: "##", resolve: $0, reject: $1)
      },
      .resolved("false"))
  }

  // MARK: Key lifecycle

  func testKeyLifecycle() throws {
    let module = makeModule()
    XCTAssertEqual(
      call { module.generateEnrollmentKeyPair("e1", securityLevel: nil, resolve: $0, reject: $1) },
      .resolved("true"))
    guard case .resolved(let pub1) = call({ module.getPublicKey("e1", resolve: $0, reject: $1) })
    else {
      return XCTFail("public key not resolved")
    }
    // Exists → true, same key.
    XCTAssertEqual(
      call {
        module.generateEnrollmentKeyPair(
          "e1", securityLevel: "confirm-before-approvals", resolve: $0, reject: $1)
      }, .resolved("true"))
    XCTAssertEqual(call { module.getPublicKey("e1", resolve: $0, reject: $1) }, .resolved(pub1))
    XCTAssertEqual(
      call { module.getEnrollmentPrivateKeyStorageTier("e1", resolve: $0, reject: $1) },
      .resolved("NONE"))

    guard
      case .resolved(let sigB64) = call({
        module.sign("e1", data: "héllo|payload", resolve: $0, reject: $1)
      })
    else { return XCTFail("sign did not resolve") }
    let publicKey = try P256.Signing.PublicKey(
      derRepresentation: XCTUnwrap(Data(base64Encoded: pub1)))
    let sig = try P256.Signing.ECDSASignature(
      derRepresentation: XCTUnwrap(Data(base64Encoded: sigB64)))
    XCTAssertTrue(publicKey.isValidSignature(sig, for: Data("héllo|payload".utf8)))
    XCTAssertTrue(EzkeyCryptoCodec.isLowS(sig.rawRepresentation))

    XCTAssertEqual(call { module.deleteKeyPair("e1", resolve: $0, reject: $1) }, .resolved("true"))
    XCTAssertEqual(
      call { module.getPublicKey("e1", resolve: $0, reject: $1) }, .rejected("EZK_KEY_NOT_FOUND"))
  }

  /// Production policy (D2 revised): never the Secure Enclave on the simulator.
  func testSimulatorPolicyUsesSoftwareKey() {
    #if targetEnvironment(simulator)
      XCTAssertFalse(EzkeyEnrollmentKeyStore.useSecureEnclave)
      let module = EzkeyCryptoModule(
        store: EzkeyEnrollmentKeyStore(service: service, defaults: defaults))
      XCTAssertEqual(
        call {
          module.generateEnrollmentKeyPair("sim", securityLevel: nil, resolve: $0, reject: $1)
        },
        .resolved("true"))
      XCTAssertEqual(
        call { module.getEnrollmentPrivateKeyStorageTier("sim", resolve: $0, reject: $1) },
        .resolved("NONE"))
    #endif
  }

  /// Exercises the Secure Enclave path (forced by injection) wherever the runtime has one.
  func testSecureEnclavePathWhenAvailable() throws {
    try XCTSkipUnless(SecureEnclave.isAvailable, "no Secure Enclave on this runtime")
    let store = EzkeyEnrollmentKeyStore(
      service: service, defaults: defaults, secureEnclaveAvailable: true)
    let module = EzkeyCryptoModule(store: store, canAuthenticate: { true })
    let generated = call {
      module.generateEnrollmentKeyPair("se1", securityLevel: nil, resolve: $0, reject: $1)
    }
    XCTAssertEqual(generated, .resolved("true"))
    XCTAssertEqual(
      call { module.getEnrollmentPrivateKeyStorageTier("se1", resolve: $0, reject: $1) },
      .resolved("STRONG"))
    guard case .resolved(let pub) = call({ module.getPublicKey("se1", resolve: $0, reject: $1) }),
      case .resolved(let sigB64) = call({
        module.sign("se1", data: "se-payload", resolve: $0, reject: $1)
      })
    else { return XCTFail("SE public key / sign failed") }
    let publicKey = try P256.Signing.PublicKey(
      derRepresentation: XCTUnwrap(Data(base64Encoded: pub)))
    let sig = try P256.Signing.ECDSASignature(
      derRepresentation: XCTUnwrap(Data(base64Encoded: sigB64)))
    XCTAssertTrue(publicKey.isValidSignature(sig, for: Data("se-payload".utf8)))
    XCTAssertTrue(EzkeyCryptoCodec.isLowS(sig.rawRepresentation))
  }

  // MARK: Reinstall purge

  func testPurgeRemovesPreviousInstallationKeysAndSparesOtherServices() {
    // Previous installation: a key exists, then UserDefaults is wiped (uninstall).
    let first = makeModule()
    XCTAssertEqual(
      call { first.generateEnrollmentKeyPair("e1", securityLevel: nil, resolve: $0, reject: $1) },
      .resolved("true"))
    defaults.removePersistentDomain(forName: suiteName)

    let otherService = "org.ezkey.mobile.tests.unrelated"
    SecItemDelete(
      [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: otherService]
        as CFDictionary)
    XCTAssertEqual(
      SecItemAdd(
        [
          kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: otherService,
          kSecAttrAccount as String: "a", kSecValueData as String: Data([1]),
        ] as CFDictionary, nil), errSecSuccess)
    defer {
      SecItemDelete(
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: otherService]
          as CFDictionary)
    }

    let reinstalled = makeModule()
    XCTAssertEqual(
      call { reinstalled.getPublicKey("e1", resolve: $0, reject: $1) },
      .rejected("EZK_KEY_NOT_FOUND"))
    XCTAssertNotNil(defaults.string(forKey: EzkeyEnrollmentKeyStore.installMarkerKey))

    var out: CFTypeRef?
    XCTAssertEqual(
      SecItemCopyMatching(
        [
          kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: otherService,
          kSecAttrAccount as String: "a", kSecReturnData as String: true,
        ] as CFDictionary, &out), errSecSuccess)
  }

  func testPurgeFailureFailsClosedAndRetries() {
    var failures = 1
    let module = makeModule(deleteAll: { service in
      if failures > 0 {
        failures -= 1
        return errSecInteractionNotAllowed
      }
      return EzkeyEnrollmentKeyStore.deleteAllKeychainItems(service: service)
    })
    XCTAssertEqual(
      call { module.generateEnrollmentKeyPair("e1", securityLevel: nil, resolve: $0, reject: $1) },
      .rejected("EZK_KEY_GENERATION_ERROR"))
    XCTAssertNil(defaults.string(forKey: EzkeyEnrollmentKeyStore.installMarkerKey))
    // Next operation retries the purge, which now succeeds.
    XCTAssertEqual(
      call { module.generateEnrollmentKeyPair("e1", securityLevel: nil, resolve: $0, reject: $1) },
      .resolved("true"))
    XCTAssertNotNil(defaults.string(forKey: EzkeyEnrollmentKeyStore.installMarkerKey))
  }

  // MARK: Proof token, build info, constants

  func testProofTokenFormat() throws {
    let module = makeModule()
    guard case .resolved(let firstToken) = call({ module.generateProofToken($0, reject: $1) }),
      case .resolved(let secondToken) = call({ module.generateProofToken($0, reject: $1) })
    else { return XCTFail("proof token not resolved") }
    XCTAssertNotEqual(firstToken, secondToken)
    let parts = firstToken.split(separator: ".")
    XCTAssertEqual(parts.count, 2)
    XCTAssertFalse(firstToken.contains("=") || firstToken.contains("+") || firstToken.contains("/"))
    XCTAssertEqual(try XCTUnwrap(EzkeyCryptoCodec.decodeFlexibleBase64(String(parts[0]))).count, 32)
    XCTAssertEqual(try XCTUnwrap(EzkeyCryptoCodec.decodeFlexibleBase64(String(parts[1]))).count, 16)
  }

  /// Proves the "Ezkey build info" Run Script wrote the host app's built Info.plist.
  func testBuildInfoFromRunScript() {
    let module = EzkeyCryptoModule(
      store: EzkeyEnrollmentKeyStore(service: service, defaults: defaults))
    guard case .resolved(let timestamp) = call({ module.getBuildTimestamp($0, reject: $1) }),
      case .resolved(let sha) = call({ module.getGitShortSha($0, reject: $1) })
    else { return XCTFail("build info not resolved") }
    XCTAssertNotNil(
      timestamp.range(of: #"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$"#, options: .regularExpression),
      timestamp)
    XCTAssertTrue(
      sha == "unknown" || sha.range(of: #"^[0-9a-f]{7}$"#, options: .regularExpression) != nil, sha)

    let empty = EzkeyCryptoModule(
      store: EzkeyEnrollmentKeyStore(service: service, defaults: defaults), infoDictionary: { [:] })
    XCTAssertEqual(
      call { empty.getBuildTimestamp($0, reject: $1) }, .rejected("EZK_BUILD_TIMESTAMP_ERROR"))
    XCTAssertEqual(call { empty.getGitShortSha($0, reject: $1) }, .rejected("EZK_GIT_SHA_ERROR"))
  }

  func testIsDebugBuildConstantUnchanged() {
    XCTAssertEqual(
      makeModule().constantsToExport() as NSDictionary, ["isDebugBuild": true] as NSDictionary)
  }

  // MARK: Crypto API evidence vector (plan D6)

  func testPrintServerValidationVector() throws {
    let key = P256.Signing.PrivateKey()
    let data = "ezkey-ios-step4|enrollment-verify|42"
    let signature = try EzkeyCryptoModule.signLowS(.software(key), data: data)
    let raw = try P256.Signing.ECDSASignature(
      derRepresentation: XCTUnwrap(Data(base64Encoded: signature))
    ).rawRepresentation
    let highS = try P256.Signing.ECDSASignature(rawRepresentation: EzkeyCryptoCodec.flipS(raw))
      .derRepresentation.base64EncodedString()
    let pub = key.publicKey.derRepresentation.base64EncodedString()
    print("EZKEY_VECTOR_LOW " + Self.vectorJSON(data: data, signature: signature, publicKey: pub))
    print("EZKEY_VECTOR_HIGH " + Self.vectorJSON(data: data, signature: highS, publicKey: pub))
  }

  /// Body for POST /api/v1/crypto/validate (Crypto API), printed as PR evidence.
  private static func vectorJSON(data: String, signature: String, publicKey: String) -> String {
    let body = ["data": data, "signature": signature, "publicKey": publicKey]
    let json = try? JSONSerialization.data(withJSONObject: body, options: [.sortedKeys])
    return json.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
  }
}
