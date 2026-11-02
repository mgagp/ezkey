//
// Ezkey - Open Source Cryptographic MFA Platform
//
// Copyright (c) 2025 Ezkey contributors
// Licensed under the MIT License. See LICENSE file in the project root for full license information.
//
// Registers the Swift EzkeyCryptoModule with React Native (legacy module interop,
// exposed to JS as NativeModules.EzkeyCryptoModule like on Android). Method names and
// argument order match app/services/crypto/nativeCrypto.ts.
//

#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE(EzkeyCryptoModule, NSObject)

RCT_EXTERN_METHOD(generateEnrollmentKeyPair:(NSString *)enrollmentId
                  securityLevel:(NSString *)securityLevel
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getPublicKey:(NSString *)enrollmentId
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getEnrollmentPrivateKeyStorageTier:(NSString *)enrollmentId
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(sign:(NSString *)enrollmentId
                  data:(NSString *)data
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(signWithAuthentication:(NSString *)enrollmentId
                  data:(NSString *)data
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(deleteKeyPair:(NSString *)enrollmentId
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(canUseProtectedSigning:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(authenticateSecurityPreferenceDowngrade:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(verify:(NSString *)data
                  signatureBase64:(NSString *)signatureBase64
                  publicKeyBase64:(NSString *)publicKeyBase64
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(generateProofToken:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getBuildTimestamp:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

RCT_EXTERN_METHOD(getGitShortSha:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)

@end
