//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Registers the Swift EzkeyAppearanceModule with React Native (legacy module interop, exposed to
// JS as NativeModules.EzkeyAppearanceModule like on Android). JS: app/services/appearance/appearance.ts.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(EzkeyAppearanceModule, NSObject)

RCT_EXTERN_METHOD(setApplicationNightMode:(NSString *)mode)

@end
