//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2026 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Registers the Swift EzkeyLocaleModule with React Native (legacy module interop, exposed to JS as
// NativeModules.EzkeyLocaleModule like on Android). JS: app/services/locale/appLanguage.ts.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(EzkeyLocaleModule, NSObject)

RCT_EXTERN_METHOD(getApplicationLanguage:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(setApplicationLanguage:(NSString *)tag
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getSystemLanguages:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end
