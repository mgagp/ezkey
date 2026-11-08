//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//

import XCTest

@testable import EzkeyMobile

final class EzkeyLocaleModuleTests: XCTestCase {

  private let suiteName = "EzkeyLocaleModuleTests"
  private var defaults: UserDefaults!
  private var module: EzkeyLocaleModule!

  override func setUp() {
    super.setUp()
    defaults = UserDefaults(suiteName: suiteName)
    defaults.removePersistentDomain(forName: suiteName)
    module = EzkeyLocaleModule(defaults: defaults, domainName: suiteName, region: "CA")
  }

  override func tearDown() {
    defaults.removePersistentDomain(forName: suiteName)
    defaults = nil
    module = nil
    super.tearDown()
  }

  private func applicationLanguage() -> String? {
    var result: String?
    module.getApplicationLanguage({ result = $0 as? String }, reject: { _, _, _ in })
    return result
  }

  private func setLanguage(_ tag: String) {
    module.setApplicationLanguage(tag, resolve: { _ in }, reject: { _, _, _ in })
  }

  func testNothingSavedFollowsThePhone() {
    XCTAssertEqual(applicationLanguage(), "")
  }

  func testChoiceIsSavedWithThePhoneRegionLikeIosSettings() {
    setLanguage("fr")
    XCTAssertEqual(defaults.array(forKey: EzkeyLocaleModule.languagesKey) as? [String], ["fr-CA"])
    XCTAssertEqual(applicationLanguage(), "fr-CA")
  }

  func testChoiceWithoutARegionOnThePhoneStaysPlain() {
    module = EzkeyLocaleModule(defaults: defaults, domainName: suiteName, region: nil)
    setLanguage("fr")
    XCTAssertEqual(applicationLanguage(), "fr")
  }

  func testEmptyTagReturnsToThePhone() {
    setLanguage("fr")
    setLanguage("")
    XCTAssertEqual(applicationLanguage(), "")
  }

  /// The list iOS Settings writes: the choice first, then the other languages.
  func testReadsTheChoiceWrittenByIosSettings() {
    defaults.set(["en-CA", "fr-CA"], forKey: EzkeyLocaleModule.languagesKey)
    XCTAssertEqual(applicationLanguage(), "en-CA")
  }

  func testSystemLanguagesAreNeverEmpty() {
    var result: [String]?
    module.getSystemLanguages({ result = $0 as? [String] }, reject: { _, _, _ in })
    XCTAssertEqual(result?.isEmpty, false)
  }
}
