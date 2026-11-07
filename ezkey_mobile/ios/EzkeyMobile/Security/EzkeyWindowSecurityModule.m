//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Registers the Swift EzkeyWindowSecurityModule with React Native (legacy module interop, exposed
// to JS as NativeModules.EzkeyWindowSecurityModule like on Android).
// JS: app/services/security/sensitiveScreen.ts.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(EzkeyWindowSecurityModule, NSObject)

RCT_EXTERN_METHOD(setSensitiveScreen:(BOOL)enabled)
RCT_EXTERN_METHOD(setCaptureMessage:(NSString *)message)

@end
