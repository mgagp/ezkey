//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//

import UIKit
import XCTest

@testable import EzkeyMobile

final class EzkeyAppearanceModuleTests: XCTestCase {

  private let suiteName = "EzkeyAppearanceModuleTests"
  private var defaults: UserDefaults!

  override func setUp() {
    super.setUp()
    defaults = UserDefaults(suiteName: suiteName)
    defaults.removePersistentDomain(forName: suiteName)
  }

  override func tearDown() {
    defaults.removePersistentDomain(forName: suiteName)
    defaults = nil
    super.tearDown()
  }

  func testNothingSavedFollowsTheSystem() {
    XCTAssertEqual(EzkeyAppearanceModule.savedInterfaceStyle(defaults: defaults), .unspecified)
  }

  func testSavedModesMapToWindowStyles() {
    let module = EzkeyAppearanceModule(defaults: defaults)
    let expected: [(String, UIUserInterfaceStyle)] = [
      ("light", .light), ("dark", .dark), ("system", .unspecified),
    ]
    for (mode, style) in expected {
      module.setApplicationNightMode(mode)
      XCTAssertEqual(defaults.string(forKey: EzkeyAppearanceModule.preferenceKey), mode)
      XCTAssertEqual(EzkeyAppearanceModule.savedInterfaceStyle(defaults: defaults), style, mode)
    }
  }

  func testUnknownModeKeepsThePreviousChoice() {
    let module = EzkeyAppearanceModule(defaults: defaults)
    module.setApplicationNightMode("dark")
    module.setApplicationNightMode("sepia")
    XCTAssertEqual(EzkeyAppearanceModule.savedInterfaceStyle(defaults: defaults), .dark)
  }

  func testCorruptValueFollowsTheSystem() {
    defaults.set("sepia", forKey: EzkeyAppearanceModule.preferenceKey)
    XCTAssertEqual(EzkeyAppearanceModule.savedInterfaceStyle(defaults: defaults), .unspecified)
  }
}
