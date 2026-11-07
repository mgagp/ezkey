//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyAppearanceModule (iOS)
// Description: iOS counterpart of the Android EzkeyAppearanceModule (#1156, #1188): same module
//              and method names. Android hands the choice to the system (per-app night mode);
//              iOS has no such API, so the choice is kept in UserDefaults and SceneDelegate applies
//              it to the window before React Native starts, so the first frame already uses the
//              chosen palette. React Native's Appearance.setColorScheme handles the in-session
//              switch. The launch screen still follows the system appearance (iOS limit).
//

import Foundation
import React
import UIKit
import os

@objc(EzkeyAppearanceModule)
final class EzkeyAppearanceModule: NSObject {

  /// UserDefaults key for the saved choice (`system`, `light` or `dark`).
  static let preferenceKey = "org.ezkey.mobile.appearance"

  private static let logger = Logger(subsystem: "org.ezkey.mobile", category: "appearance")

  private let defaults: UserDefaults

  override convenience init() {
    self.init(defaults: .standard)
  }

  init(defaults: UserDefaults) {
    self.defaults = defaults
    super.init()
  }

  @objc static func requiresMainQueueSetup() -> Bool {
    false
  }

  /// Saves the choice for the next cold start. Fail-open: an unknown mode is logged and ignored,
  /// the in-app theme still switches through React Native.
  ///
  /// - Parameter mode: `system`, `light` or `dark`
  @objc(setApplicationNightMode:)
  func setApplicationNightMode(_ mode: String) {
    guard Self.interfaceStyle(for: mode) != nil else {
      Self.logger.warning("Unknown appearance mode: \(mode, privacy: .public)")
      return
    }
    defaults.set(mode, forKey: Self.preferenceKey)
  }

  /// Maps a saved mode to the window style; `nil` for an unknown value.
  static func interfaceStyle(for mode: String?) -> UIUserInterfaceStyle? {
    switch mode {
    case "light": return .light
    case "dark": return .dark
    case "system": return .unspecified
    default: return nil
    }
  }

  /// The window style for the saved choice; follows the system when nothing valid is saved.
  static func savedInterfaceStyle(defaults: UserDefaults = .standard) -> UIUserInterfaceStyle {
    interfaceStyle(for: defaults.string(forKey: preferenceKey)) ?? .unspecified
  }
}
