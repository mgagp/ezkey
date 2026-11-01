//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2025 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Registers the Swift EzkeyCryptoModule with React Native (legacy module interop,
// exposed to JS as NativeModules.EzkeyCryptoModule like on Android).
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(EzkeyCryptoModule, NSObject)
@end
