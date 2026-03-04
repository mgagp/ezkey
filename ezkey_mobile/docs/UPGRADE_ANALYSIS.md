# Ezkey Mobile – Upgrade Analysis

> Analysis of current dependencies and target versions for React Native 0.83/0.84 migration.  
> Reference: [React Native Versions](https://reactnative.dev/versions)

---

## Current state (RN 0.76 – archived)

React Native **0.76** is [archived](https://reactnative.dev/versions) and no longer supported. The oldest supported versions are **0.83** (Active) and **0.84** (Latest).

---

## Target versions


| Version  | Status | Release Date | Notes                               |
| -------- | ------ | ------------ | ----------------------------------- |
| **0.84** | Latest | Feb 2026     | Hermes V1 default, Node 22 required |
| **0.83** | Active | Dec 2025     | React 19.2, no breaking changes     |


**Recommended migration path:** 0.76 → 0.83 → 0.84 (incremental steps).

---

## Component inventory

### 1. Core framework


| Package                             | Current | Latest     | Notes                    |
| ----------------------------------- | ------- | ---------- | ------------------------ |
| **react-native**                    | 0.76.0  | **0.84.0** | Target: 0.83 or 0.84     |
| **react**                           | 18.3.1  | **19.2.x** | RN 0.83+ uses React 19.2 |
| **@react-native/babel-preset**      | 0.76.0  | 0.84.x     | Match RN version         |
| **@react-native/metro-config**      | 0.76.0  | 0.84.x     | Match RN version         |
| **@react-native/typescript-config** | 0.76.0  | 0.84.x     | Match RN version         |
| **@react-native/eslint-config**     | 0.76.0  | 0.84.x     | Match RN version         |


### 2. Camera (Vision Camera)


| Package                        | Current | Latest    | Notes                         |
| ------------------------------ | ------- | --------- | ----------------------------- |
| **react-native-vision-camera** | ^4.2.3  | **4.7.3** | QR scanning, Frame Processors |
| **react-native-worklets-core** | ^1.6.2  | **1.6.3** | Required by Vision Camera     |


**Usage:** `EnrollmentScannerModal.tsx`, `EnrollmentWizardScreen.tsx` – QR scanning, Frame Processors.

**Compatibility:** Vision Camera 4.7.x supports RN 0.81+; 0.83/0.84 should be tested. [npm](https://www.npmjs.com/package/react-native-vision-camera)
[Vision Camera 5.0.0-beta](https://www.npmjs.com/package/react-native-vision-camera/v/5.0.0-beta.3) exists but is experimental.

### 3. Crypto & secure storage


| Package                   | Current | Latest     | Notes                          |
| ------------------------- | ------- | ---------- | ------------------------------ |
| **react-native-keychain** | ^10.0.0 | **10.0.0** | Up to date                     |
| **EzkeyCryptoModule**     | Native  | N/A        | Custom Kotlin module, EC P-256 |


**Usage:** `secureStorage.ts` (Keychain), `cryptoService.ts` + native modules.

**Note:** react-native-keychain 10.0.0 is current; no upgrade needed for RN 0.83/0.84.

### 4. Navigation


| Package                            | Current | Latest     | Notes                   |
| ---------------------------------- | ------- | ---------- | ----------------------- |
| **@react-navigation/native**       | ^7.1.19 | **7.x**    | RN 0.83/0.84 compatible |
| **@react-navigation/native-stack** | ^7.1.19 | **7.x**    |                         |
| **@react-navigation/stack**        | ^7.6.2  | **7.x**    |                         |
| **react-native-screens**           | 3.30.1  | **4.19.0** | Major bump              |
| **react-native-gesture-handler**   | 2.14.1  | **2.25.0** |                         |
| **react-native-safe-area-context** | ^5.6.2  | **5.x**    |                         |


**Note:** React Navigation 8 is in alpha; 7.x is the stable choice for RN 0.83/0.84.

### 5. State & data


| Package                   | Current | Latest      | Notes |
| ------------------------- | ------- | ----------- | ----- |
| **@tanstack/react-query** | ^5.90.7 | **5.90.21** | Minor |
| **zustand**               | ^5.0.8  | **5.x**     |       |


### 6. Storage & config


| Package                                       | Current | Latest    | Notes             |
| --------------------------------------------- | ------- | --------- | ----------------- |
| **@react-native-async-storage/async-storage** | ^1.23.1 | **3.0.1** | Major (v2→v3)     |
| **react-native-config**                       | ^1.5.9  | **1.6.1** | RN 0.73+ required |


**Usage:** `enrollmentStorage.ts` (AsyncStorage), `env.ts` (Config).

**AsyncStorage v3:** Breaking changes; API changes possible. [npm](https://www.npmjs.com/package/@react-native-async-storage/async-storage)

### 7. Network


| Package   | Current | Latest     | Notes |
| --------- | ------- | ---------- | ----- |
| **axios** | ^1.13.2 | **1.13.x** | OK    |


### 8. CLI & tooling


| Package                                          | Current        | Latest | Notes    |
| ------------------------------------------------ | -------------- | ------ | -------- |
| **@react-native-community/cli**                  | 15.0.0-alpha.2 | 15.x   | Match RN |
| **@react-native-community/cli-platform-android** | 15.0.0-alpha.2 | 15.x   |          |
| **@react-native-community/cli-platform-ios**     | 15.0.0-alpha.2 | 15.x   |          |


### 9. Native Android


| Package                               | Current | Latest     | Notes |
| ------------------------------------- | ------- | ---------- | ----- |
| **com.google.mlkit:barcode-scanning** | 17.2.0  | **17.3.0** | Minor |


**Usage:** `EzkeyQrFrameProcessorPlugin.kt` – QR decoding via Vision Camera + ML Kit.

### 10. Native iOS


| Pod                             | Current       | Notes                    |
| ------------------------------- | ------------- | ------------------------ |
| **GoogleMLKit/BarcodeScanning** | (via Podfile) | Match RN / Vision Camera |


---

## Risk matrix


| Component                             | Risk       | Notes                             |
| ------------------------------------- | ---------- | --------------------------------- |
| RN 0.76 → 0.83                        | **High**   | Gradle, New Architecture, Hermes  |
| React 18 → 19                         | **Medium** | API changes                       |
| Vision Camera 4.2 → 4.7               | **Medium** | Frame Processors, Reanimated      |
| AsyncStorage 1.x → 3.x                | **Medium** | API changes                       |
| react-native-screens 3.x → 4.x        | **Medium** | API changes                       |
| react-native-keychain                 | **Low**    | Already current                   |
| react-native-config                   | **Low**    | Minor                             |
| @tanstack/react-query                 | **Low**    | Patch                             |
| Navigation 7.x                        | **Low**    | Patch                             |
| Native modules (EzkeyCrypto, EzkeyQr) | **Medium** | May need RN 0.83/0.84 adjustments |


---

## Migration order (recommended)

1. **Phase 1 – Base**
  - Update react-native 0.76 → 0.83
  - Update React 18 → 19.2
  - Update @react-native/* packages
  - Fix Gradle / Android build
  - Fix iOS Pods
2. **Phase 2 – Core libs**
  - react-native-screens
  - react-native-gesture-handler
  - react-native-safe-area-context
  - react-native-config
3. **Phase 3 – Camera**
  - react-native-vision-camera 4.2 → 4.7
  - react-native-worklets-core
4. **Phase 4 – Storage**
  - @react-native-async-storage/async-storage (1.x → 2.x or 3.x)
  - Verify react-native-keychain
5. **Phase 5 – Optional**
  - RN 0.83 → 0.84 (Node 22, Hermes V1)
  - ML Kit 17.2 → 17.3

---

## Environment requirements (RN 0.83/0.84)


| Requirement | RN 0.83 | RN 0.84    |
| ----------- | ------- | ---------- |
| Node.js     | 18.18+  | **22.11+** |
| JDK         | 17      | 17         |
| Xcode       | 15.x    | 15.x       |
| Android SDK | 34      | 34         |


---

## References

- [React Native Versions](https://reactnative.dev/versions)
- [React Native 0.83 Blog](https://reactnative.dev/blog/2025/12/10/react-native-0.83)
- [React Native 0.84 Blog](https://reactnative.dev/blog/2026/02/11/react-native-0.84)
- [Vision Camera npm](https://www.npmjs.com/package/react-native-vision-camera)
- [React Navigation upgrade](https://reactnavigation.org/docs/8.x/upgrading-from-7.x)

