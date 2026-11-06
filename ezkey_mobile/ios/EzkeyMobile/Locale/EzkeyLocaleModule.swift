//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyLocaleModule (iOS)
// Description: iOS counterpart of the Android EzkeyLocaleModule (#1157): same module and method
//              names. iOS Settings → Ezkey → Language stores the per-app language as AppleLanguages
//              in the app's own defaults, so reading and writing that key keeps Settings and the
//              in-app picker in sync. iOS relaunches the app when the choice changes in Settings.
//              Native text (permission prompts) follows an in-app change at the next launch.
//

import Foundation
import React

@objc(EzkeyLocaleModule)
final class EzkeyLocaleModule: NSObject {

  static let languagesKey = "AppleLanguages"

  private let defaults: UserDefaults
  private let domainName: String
  private let region: String?

  override convenience init() {
    self.init(
      defaults: .standard,
      domainName: Bundle.main.bundleIdentifier ?? "",
      region: (Locale.current as NSLocale).countryCode
    )
  }

  init(defaults: UserDefaults, domainName: String, region: String?) {
    self.defaults = defaults
    self.domainName = domainName
    self.region = region
    super.init()
  }

  @objc static func requiresMainQueueSetup() -> Bool {
    false
  }

  /// Resolves the first per-app language tag, `""` when the app follows the phone. Reads the app's
  /// own domain only: `defaults.array(forKey:)` would also return the phone's list.
  ///
  /// - Parameters:
  ///   - resolve: receives the tag
  ///   - reject: unused, the read cannot fail
  @objc(getApplicationLanguage:reject:)
  func getApplicationLanguage(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    let languages = defaults.persistentDomain(forName: domainName)?[Self.languagesKey] as? [String]
    resolve(languages?.first ?? "")
  }

  /// Sets the per-app language; `""` returns Ezkey to the phone's language.
  ///
  /// - Parameters:
  ///   - tag: BCP 47 language tag, or empty
  ///   - resolve: resolved once saved
  ///   - reject: unused, the write cannot fail
  @objc(setApplicationLanguage:resolve:reject:)
  func setApplicationLanguage(
    _ tag: String,
    resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    if tag.isEmpty {
      defaults.removeObject(forKey: Self.languagesKey)
    } else {
      defaults.set([regional(tag)], forKey: Self.languagesKey)
    }
    resolve(nil)
  }

  /// Resolves the phone's language list. iOS cannot read it past a per-app language, and returns
  /// that language instead: `regional` keeps its region so dates keep the regional format.
  ///
  /// - Parameters:
  ///   - resolve: receives the language tags in preference order
  ///   - reject: unused, the read cannot fail
  @objc(getSystemLanguages:reject:)
  func getSystemLanguages(
    _ resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock
  ) {
    resolve(Locale.preferredLanguages)
  }

  /// Adds the phone's region, as iOS Settings does (`fr` on a Canadian phone is saved as `fr-CA`).
  private func regional(_ tag: String) -> String {
    guard let region, !tag.contains("-") else { return tag }
    return "\(tag)-\(region)"
  }
}
