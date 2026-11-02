//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Reinstall purge probe on the REAL enrollment-key service (plan step 4, proof ladder). Skipped
// unless TEST_RUNNER_EZKEY_PURGE_PROBE=seed|check is set on xcodebuild. Procedure (docs/IOS_SETUP.md):
//   1. TEST_RUNNER_EZKEY_PURGE_PROBE=seed  xcodebuild test ... -only-testing:EzkeyMobileTests/EzkeyReinstallPurgeProbeTests
//   2. xcrun simctl uninstall <udid> org.ezkey.mobile      (Keychain survives, UserDefaults does not)
//   3. TEST_RUNNER_EZKEY_PURGE_PROBE=check xcodebuild test ... (same filter)
//

import XCTest

@testable import EzkeyMobile

final class EzkeyReinstallPurgeProbeTests: XCTestCase {

  private let probeId = "purge-probe"
  private let unrelatedService = "org.ezkey.mobile.tests.rn-keychain-like"

  private func itemExists(service: String, account: String) -> Bool {
    SecItemCopyMatching(
      [
        kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
        kSecAttrAccount as String: account,
      ] as CFDictionary, nil) == errSecSuccess
  }

  func testReinstallPurgeProbe() throws {
    let phase = ProcessInfo.processInfo.environment["EZKEY_PURGE_PROBE"]
    try XCTSkipUnless(
      phase == "seed" || phase == "check", "set TEST_RUNNER_EZKEY_PURGE_PROBE=seed|check")
    let account = EzkeyEnrollmentKeyStore.account(for: probeId)
    let service = EzkeyEnrollmentKeyStore.defaultService

    if phase == "seed" {
      try EzkeyEnrollmentKeyStore().generateIfAbsent(enrollmentId: probeId)
      SecItemDelete(
        [
          kSecClass as String: kSecClassGenericPassword,
          kSecAttrService as String: unrelatedService,
        ] as CFDictionary)
      XCTAssertEqual(
        SecItemAdd(
          [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: unrelatedService,
            kSecAttrAccount as String: "secret", kSecValueData as String: Data([7]),
          ] as CFDictionary, nil), errSecSuccess)
      XCTAssertTrue(itemExists(service: service, account: account))
      print("EZKEY_PURGE_PROBE seed: enrollment key + unrelated item written")
      return
    }

    let marker = UserDefaults.standard.string(forKey: EzkeyEnrollmentKeyStore.installMarkerKey)
    let survived = itemExists(service: service, account: account)
    print("EZKEY_PURGE_PROBE check: marker=\(marker ?? "absent") keySurvivedUninstall=\(survived)")
    XCTAssertNil(marker, "UserDefaults should be fresh after uninstall")
    XCTAssertTrue(survived, "Keychain item should survive uninstall (the problem the purge solves)")

    // The first key operation of the process triggers the purge.
    XCTAssertNil(try EzkeyEnrollmentKeyStore().loadKey(enrollmentId: probeId))
    XCTAssertFalse(itemExists(service: service, account: account))
    XCTAssertNotNil(UserDefaults.standard.string(forKey: EzkeyEnrollmentKeyStore.installMarkerKey))
    XCTAssertTrue(
      itemExists(service: unrelatedService, account: "secret"), "other services untouched")
    print("EZKEY_PURGE_PROBE check: purged=true unrelatedIntact=true")
    SecItemDelete(
      [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: unrelatedService]
        as CFDictionary)
  }
}
