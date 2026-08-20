# Ezkey Mobile (React Native)

> Cross-platform companion app that manages Ezkey enrollments and handles MFA approvals through native cryptography.

## Overview

- **Current scope note**: The current mobile product and security posture are **Android-first**. iOS remains a later planned milestone and is **not** a short-term parity or release target. Review Android as the current implementation of record; do not treat missing iOS parity as a present defect unless documentation overclaims it.
- **Stack**: React Native 0.86.2, React 19.2.7, and TypeScript with dedicated Android (Kotlin) and iOS (Swift/Obj-C++) native modules
- **Primary Flows**: Enrollment via QR, secure key generation, pending authentication approvals/denials, challenge handling
- **APIs Consumed**: `auth-api` endpoints documented in [`docs/ENDPOINT.md`](../docs/ENDPOINT.md)
- **Security Alignment**: Tracks the current guarantees and constraints documented in [`docs/CRYPTO.md`](../docs/CRYPTO.md) and [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md)
- **Auth API Client Source**: OpenAPI-driven client generation via Orval from the versioned local [`openapi-spec.json`](openapi-spec.json)

## Core Capabilities

- Guided enrollment wizard with QR scanning, challenge validation, and EC P-256 key provisioning
- Local enrollment catalogue with enrollment metadata storage, detail views, and pending-auth shortcuts
- Manual, user-driven polling for authentication attempts followed by approve/deny flows
- Shared Axios client with deterministic timeouts and error handling suitable for mobile networks
- Native crypto bridge (Kotlin/Swift) for EC P-256 key management, with the current Android implementation using `Android Keystore` and requesting `StrongBox` when available

## Security Posture

- Current mobile-security conclusions in this repository are **Android-first**. iOS code in the workspace should be treated as future-milestone groundwork unless and until the docs explicitly say parity has been achieved.
- On Android, long-lived enrollment secrets such as `enrollmentProofToken` and `integrationPublicKey` are sealed at rest through a dedicated app-level `Android Keystore` AES key; signatures and one-time auth proof tokens remain in memory only
- Enrollment and authentication requests follow the pull-based model that avoids background polling to prevent enumeration or replay
- Device credentials use EC P-256 (ECDSA-SHA256) as specified in [`docs/CRYPTO.md`](../docs/CRYPTO.md): PKCS#8 private key, X.509 public key, and platform-keystore integration
- In the current Android implementation, each enrollment gets an EC P-256 key pair generated through `Android Keystore`; `StrongBox` is requested when available, and private key material is not exposed to application code
- The current Android implementation does **not** use the enrollment private key as a general-purpose wrapping key for all mobile secrets. Private keys remain in `Android Keystore`; long-lived application secrets such as `enrollmentProofToken` and `integrationPublicKey` are sealed separately and rehydrated only when needed by the flow
- Client-side documentation references the backend security analysis in [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md) to keep UI logic aligned with server-side guarantees

## Project Structure

```text
ezkey_mobile/
├── app/
│   ├── components/              # Reusable UI (e.g., QR modal)
│   ├── hooks/                   # React Query + storage orchestration
│   ├── navigation/              # Stack navigator + types
│   ├── providers/               # App-wide context providers
│   ├── screens/                 # Feature screens (Home, Enrollment Wizard, Pending Auth, Settings)
│   ├── services/
│   │   ├── api/                 # Generated Auth API client, wrappers, problems, DTOs
│   │   ├── crypto/              # Native crypto integration layer
│   │   └── storage/             # Secure + metadata storage abstractions
│   └── state/                   # Zustand stores
├── android/                     # Native Android project (Kotlin)
├── ios/                         # Native iOS project (Swift/Objective-C++)
├── docs/                        # Mobile-specific documentation (architecture, native modules, PRD)
└── package.json
```

## Prerequisites

- Node.js **20.19.4+** and Yarn 4 (Berry) — aligned with React Native 0.86 requirements
- JDK 17 and Android Studio with Android SDK 36 / build-tools 36.0.0 available
- Xcode 16.1+ with CocoaPods 1.16.x recommended (macOS)
- Watchman (macOS), Git Bash or another POSIX shell on Windows
- Access to a running Ezkey backend (see [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) for endpoint details)

## Setup

### Install dependencies

```bash
yarn install
```

### Configure environment

Create `.env` inside `ezkey_mobile/` (see `.env.example`):

```dotenv
EZKEY_API_BASE_URL=http://127.0.0.1:8080
EZKEY_REQUEST_TIMEOUT=10000
```

**QR-first enrollment (recommended):** On the Admin API, set `ezkey.qr.auth-base-url` to the **public** Auth API base URL (scheme + host + port). Enrollment QR codes then include `authUrl` in the JSON, and the app uses that URL for bind/verify without relying on a fixed tunnel in `.env`. In Docker, map this with `EZKEY_QR_AUTH_BASE_URL` on the `admin-api` and `auth-api` services so branding stays aligned (see `docker/docker-compose.yml`).

**Public instance metadata:** `GET /api/v1/public/instance-info` on the **Auth API** (same path and JSON as on the Admin API) returns `authApiPublicBaseUrl` and organization fields. Mobile should call this endpoint on the same Auth base URL used for bind/verify (no Admin API required).

> Run setup commands from Git Bash (or another POSIX-compatible shell) when working on Windows to avoid path issues. For iOS, the current React Native baseline expects a minimum deployment target of iOS 15.1 and an Xcode 16.1-class toolchain on macOS.

### Run the app

```bash
# iOS simulator
yarn ios

# Android emulator/device
yarn android

# Start Metro bundler only
yarn start
```

### Useful scripts

```bash
yarn generate:api         # Regenerate the local Auth API client from openapi-spec.json
yarn android:clean         # Clear Gradle outputs
yarn android:assemble:debug
yarn android:install:debug
yarn android:assemble:release
yarn android:bundle:release
```

## Auth API spec and code generation policy

- `ezkey_mobile/openapi-spec.json` is the mobile project's local, versioned copy of the Auth API spec.
- This file exists so the mobile sub-project remains autonomous and reproducible.
- Do not edit `openapi-spec.json` manually.
- The only supported way to refresh this file is the centralized update script at [`scripts/update-specs.sh`](../scripts/update-specs.sh).
- The expected workflow is human-driven: start the Docker stack cleanly, wait for the APIs to be up, then run the centralized spec update script to fetch, format, and dispatch the latest specs into each sub-project.
- After the mobile spec has been refreshed, regenerate the local client with `yarn generate:api`.
- The generated models under `app/services/api/generated/auth-api/model/` are the contract source of truth for Auth API DTOs.
- `app/services/api/types.ts` is intentionally thin: it keeps local mobile domain types such as `EnrollmentSummary` and wrapper input shapes that still accept UI-friendly string values before normalization.
- Coding assistants working in this project must follow the same rule: never hand-edit `openapi-spec.json`; always rely on the centralized update script and then regenerate.

### Install on your phone (standalone Android build)

The Android **debug** build is configured in two ways so it can run **without Metro**:

1. **`debuggableVariants = []`** in `android/app/build.gradle` — Gradle **embeds** `index.android.bundle` in the APK when you run `assembleDebug` / `installDebug`.
2. **`ezkey.useMetroInDebug=false`** in `android/gradle.properties` (default) — `MainApplication` sets `getUseDeveloperSupport()` from `BuildConfig.USE_DEVELOPER_SUPPORT`, so the app **does not** look for the packager or enable the RN dev menu on a **debug** install.

To work with **Metro + fast refresh** on a debug build, set `ezkey.useMetroInDebug=true`, rebuild, and use `yarn start` (for a physical device, `adb reverse tcp:8081 tcp:8081`).

1. On the phone, enable **Developer options → USB debugging** and connect via USB (accept the computer’s RSA prompt when prompted).
2. Confirm the device is visible:

    ```bash
    adb devices
    ```

3. From `ezkey_mobile/`, install the debug APK on the connected device:

    ```bash
    yarn android:install:debug
    ```

    On Windows, if Gradle fails with a JDK version error, set `JAVA_HOME` to Android Studio’s bundled JBR (JDK 17), then run the command again — see [Android build troubleshooting](#android-build-troubleshooting).

### Alternative: build the APK, then push to the phone

```bash
yarn android:assemble:debug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

`-r` replaces an existing install. Copy the APK to another machine or share it if you prefer to install without a USB cable (same `adb install` over Wi‑Fi debugging works once paired).

### Quality gates

```bash
yarn lint                  # ESLint + TypeScript checks
yarn typecheck             # tsc --noEmit
yarn test                  # Jest unit/component tests
yarn validate:ci           # lint + typecheck + test --runInBand (same as GitHub Actions)
```

Device UI evidence uses Maestro (`maestro/README.md`), not Detox. See [`docs/MOBILE_TEST_STRATEGY.md`](docs/MOBILE_TEST_STRATEGY.md).

On Windows, prefer **Corepack** so Yarn matches `package.json` (`yarn@4.10.3`):

```bash
corepack enable
corepack yarn validate:ci
```

Android JVM unit tests (crypto helpers on the native side):

```bash
cd android && ./gradlew :app:testDebugUnitTest --no-daemon
```

### Continuous integration (GitHub)

Pull requests that touch `ezkey_mobile/**` run the workflow [`.github/workflows/ezkey-mobile-unit-tests.yml`](../.github/workflows/ezkey-mobile-unit-tests.yml):

| Job | What it runs |
|-----|----------------|
| **js-validate** | `yarn validate:ci` on Ubuntu (Node 20.19.4, Yarn 4 via Corepack) |
| **android-jvm-unit-tests** | `./gradlew :app:testDebugUnitTest` (JDK 17, Android SDK) |

Before opening or updating a mobile PR, run the same commands locally when possible. A green check on GitHub means the branch passes on a clean runner, not only on your workstation.

### Android build troubleshooting

If you see **"Error resolving plugin [id: 'com.facebook.react.settings']"** or **"Unsupported class file major version 69"**, the Android build is likely using JDK 25. React Native 0.86.2 and the current Android toolchain require **JDK 17 or 21**; on this workstation, use **JDK 17**.

**Option 1 – Canonical clean install (Git Bash, from `ezkey_mobile/`):**

```bash
adb devices -l
./scripts/build-install-debug-clean.sh
```

Same as `yarn android:install:debug:clean`. JDK 17/21 is resolved automatically (`scripts/resolve-android-jdk.sh`); do not point Gradle at JDK 25.

**Option 2 – Run on device via Metro (helper):**

```bash
yarn android:jdk17
```

**Option 3 – Set JAVA_HOME manually:**

```powershell
# Windows (Android Studio bundled JBR)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
yarn android
```

```bash
# macOS
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
yarn android
```

### When to suspect stale build or dependency artifacts

Use a targeted reset early when all three of these are true:

- the problem started after a branch switch, dependency change, or reinstall;
- the crash is in React Native infrastructure code such as `react-native-screens`, `gesture-handler`, or another startup library;
- the failure appears before the app reaches the Ezkey business flow you actually changed.

In that situation, prefer a short rebuild-reset before a long investigation:

```bash
yarn install --immutable
cd android
./gradlew clean
cd ..
yarn android:install:debug
```

On Windows PowerShell, use:

```powershell
Set-Location android
.\gradlew.bat clean
Set-Location ..
yarn android:install:debug
```

If the device still behaves inconsistently, uninstall the app before reinstalling:

```bash
adb uninstall org.ezkey.mobile
```

This is the preferred first-line reset for mobile dependency/build drift. Do this before assuming a recent TypeScript or API-layer change caused a native startup regression.

## Native Modules Summary

- `EzkeyCryptoModule` exposes EC P-256 key generation, retrieval, and signing; Android currently uses `Android Keystore`, while iOS native secure-hardware support is still being aligned
- Enrollment QR uses VisionCamera 5 + `react-native-vision-camera-barcode-scanner` (`useBarcodeScannerOutput`, ML Kit on Android and iOS)
- iOS bridges live under `ios/EzkeyMobile/` and should adopt Xcode Quick Help (`///`) comments referencing the same security docs noted above
- Detailed design notes live in [`docs/NATIVE_MODULES.md`](docs/NATIVE_MODULES.md) *(created in this revision)*

## Documentation

Start with the local mobile corpus:

- [`docs/README.md`](docs/README.md) - primary index and reading order for the mobile conceptual documentation set
- [`docs/MOBILE_API_MAPPINGS.md`](docs/MOBILE_API_MAPPINGS.md) - mapping of Auth API fields, screens, local models, and trust checks
- [`docs/MOBILE_FUNCTIONAL_FLOWS.md`](docs/MOBILE_FUNCTIONAL_FLOWS.md) - nominal and exception flows for enrollment and authentication
- [`docs/MOBILE_DATA_MODEL.md`](docs/MOBILE_DATA_MODEL.md) - conceptual internal entities, persistence rules, and source-of-truth boundaries
- [`docs/MOBILE_SCREENS_AND_WIREFLOWS.md`](docs/MOBILE_SCREENS_AND_WIREFLOWS.md) - primary screens, navigation model, and data visibility
- [`docs/MOBILE_STACK_AND_ARCHITECTURE.md`](docs/MOBILE_STACK_AND_ARCHITECTURE.md) - technical structure, runtime layers, and generation workflow
- [`docs/MOBILE_POSITIONING.md`](docs/MOBILE_POSITIONING.md) - mobile product positioning and scope boundaries

Supporting mobile-specific references:

- [`docs/MOBILE_ARCHITECTURE.md`](docs/MOBILE_ARCHITECTURE.md) - earlier high-level architecture and directory conventions
- [`docs/NATIVE_MODULES.md`](docs/NATIVE_MODULES.md) - native module responsibilities and communication flow
- [`docs/MOBILE_CRYPTO_REFERENCE.md`](docs/MOBILE_CRYPTO_REFERENCE.md) - mobile-specific crypto wording guardrails and storage-tier caveats
- [`docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`](docs/MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) - focused audit of current Play release readiness from the workspace state
- [`docs/MOBILE_RELEASE_DECISION_MEMO.md`](docs/MOBILE_RELEASE_DECISION_MEMO.md) - decision framing for release-now versus upgrade-first on the current stack
- [`PRD.md`](PRD.md) - product requirements for the mobile experience

Shared repository-level references:

- [`../docs/MOBILE_DEVELOPER_GUIDE.md`](../docs/MOBILE_DEVELOPER_GUIDE.md) - shared protocol implementation guide for mobile clients beyond the reference app
- [`../docs/CRYPTO.md`](../docs/CRYPTO.md) - shared cryptographic reference
- [`../docs/ENDPOINT.md`](../docs/ENDPOINT.md) - canonical Auth API endpoint semantics
- [`../docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md) - security analysis for pending/respond endpoints

---

Maintainers: Ezkey Mobile Squad – November 2025
