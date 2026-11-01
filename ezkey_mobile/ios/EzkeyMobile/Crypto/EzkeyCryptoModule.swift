//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2025 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Module: EzkeyCryptoModule (iOS)
// Description: iOS counterpart of the Android EzkeyCryptoModule native bridge.
//              Step 3 of the iOS rebuild (#152): constants only. Key generation, signing,
//              public-key export and deletion (Secure Enclave, EC P-256) land in step 4.
//              Until then, JS crypto calls fail because the methods do not exist (fail closed).
//

import Foundation

@objc(EzkeyCryptoModule)
final class EzkeyCryptoModule: NSObject {

  @objc static func requiresMainQueueSetup() -> Bool {
    false
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
}
