# Ezkey Mobile (React Native)

> Cross-platform companion app that manages Ezkey enrollments and handles MFA approvals through native cryptography.

## Overview

- **Stack**: React Native 0.76 + TypeScript with dedicated Android (Kotlin) and iOS (Swift/Obj-C++) native modules
- **Primary Flows**: Enrollment via QR, secure key generation, pending authentication approvals/denials, challenge handling
- **APIs Consumed**: `auth-api` endpoints documented in [`docs/ENDPOINT.md`](../docs/ENDPOINT.md)
- **Security Alignment**: Tracks the current guarantees and constraints documented in [`docs/CRYPTO.md`](../docs/CRYPTO.md) and [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md)

## Core Capabilities

- Guided enrollment wizard with QR scanning, challenge validation, and EC P-256 key provisioning
- Local enrollment catalogue with enrollment metadata storage, detail views, and pending-auth shortcuts
- Manual, user-driven polling for authentication attempts followed by approve/deny flows
- Shared Axios client with deterministic timeouts and error handling suitable for mobile networks
- Native crypto bridge (Kotlin/Swift) for EC P-256 key management, with the current Android implementation using `Android Keystore` and requesting `StrongBox` when available

## Security Posture

- Proof tokens and signatures are always handled in memory; sensitive values are stored via secure storage abstractions only
- Enrollment and authentication requests follow the pull-based model that avoids background polling to prevent enumeration or replay
- Device credentials use EC P-256 (ECDSA-SHA256) as specified in [`docs/CRYPTO.md`](../docs/CRYPTO.md): PKCS#8 private key, X.509 public key, and platform-keystore integration
- In the current Android implementation, each enrollment gets an EC P-256 key pair generated through `Android Keystore`; `StrongBox` is requested when available, and private key material is not exposed to application code
- Client-side documentation references the backend security analysis in [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md) to keep UI logic aligned with server-side guarantees

## Project Structure

```
ezkey_mobile/
├── app/
│   ├── components/              # Reusable UI (e.g., QR modal)
│   ├── hooks/                   # React Query + storage orchestration
│   ├── navigation/              # Stack navigator + types
│   ├── providers/               # App-wide context providers
│   ├── screens/                 # Feature screens (Home, Enrollment Wizard, Pending Auth, Diagnostics)
│   ├── services/
│   │   ├── api/                 # REST clients and DTOs
│   │   ├── crypto/              # Native crypto integration layer
│   │   └── storage/             # Secure + metadata storage abstractions
│   └── state/                   # Zustand stores
├── android/                     # Native Android project (Kotlin)
├── ios/                         # Native iOS project (Swift/Objective-C++)
├── docs/                        # Mobile-specific documentation (architecture, native modules, PRD)
└── package.json
```

## Prerequisites

- Node.js 20 LTS and Yarn 4 (Berry)
- Java 17+ and Android Studio Giraffe (SDK 34)
- Xcode 15.x with CocoaPods 1.15+ (macOS)
- Watchman (macOS), Git Bash or another POSIX shell on Windows
- Access to a running Ezkey backend (see [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) for endpoint details)

## Setup

### Install dependencies

```bash
yarn install
```

### Configure environment

Create `.env` inside `ezkey_mobile/` (see `.env.example`):

```
EZKEY_API_BASE_URL=http://127.0.0.1:8080
EZKEY_REQUEST_TIMEOUT=10000
```

**QR-first enrollment (recommended):** On the Admin API, set `ezkey.qr.auth-base-url` to the **public** Auth API base URL (scheme + host + port). Enrollment QR codes then include `authUrl` in the JSON, and the app uses that URL for bind/verify without relying on a fixed tunnel in `.env`. In Docker, map this with `EZKEY_QR_AUTH_BASE_URL` on the `admin-api` service (see `docker/docker-compose.yml`). `GET /api/v1/public/instance-info` exposes the same value as `authApiPublicBaseUrl` for operators.

> Run setup commands from Git Bash (or another POSIX-compatible shell) when working on Windows to avoid path issues.

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
yarn android:clean         # Clear Gradle outputs
yarn android:assemble:debug
yarn android:install:debug
yarn android:assemble:release
yarn android:bundle:release
```

### Install on your phone (standalone Android build)

The Android **debug** build is configured to **embed the JavaScript bundle in the APK** (`debuggableVariants = []` in `android/app/build.gradle`), so you can use the app on a physical device **without** running Metro on your machine.

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

**Alternative — build the APK, then push to the phone**

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
# yarn detox:test          # Optional end-to-end suite (requires Detox setup)
```

### Android build troubleshooting

If you see **"Error resolving plugin [id: 'com.facebook.react.settings']"** or **"Unsupported class file major version 69"**, the Android build is likely using JDK 25. React Native 0.76 requires **JDK 17 or 21**.

**Option 1 – Use the helper script (Git Bash or terminal):**
```bash
yarn android:jdk17
```

**Option 2 – Set JAVA_HOME manually:**
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

## Native Modules Summary

- `EzkeyCryptoModule` exposes EC P-256 key generation, retrieval, and signing; Android currently uses `Android Keystore`, while iOS native secure-hardware support is still being aligned
- `EzkeyQrFrameProcessorPlugin` (Kotlin) feeds `react-native-vision-camera` with decoded QR payloads
- iOS bridges live under `ios/EzkeyMobile/` and should adopt Xcode Quick Help (`///`) comments referencing the same security docs noted above
- Detailed design notes live in [`docs/NATIVE_MODULES.md`](docs/NATIVE_MODULES.md) *(created in this revision)*

## Documentation

- [`docs/MOBILE_ARCHITECTURE.md`](docs/MOBILE_ARCHITECTURE.md) – high-level architecture and directory conventions *(created in this revision)*
- [`docs/NATIVE_MODULES.md`](docs/NATIVE_MODULES.md) – native module responsibilities and communication flow *(created in this revision)*
- [`docs/PRD.md`](docs/PRD.md) – product requirements for the mobile experience
- [`docs/CRYPTO.md`](../docs/CRYPTO.md) – shared cryptographic reference
- [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md) – security analysis for pending/respond endpoints

---

Maintainers: Ezkey Mobile Squad – November 2025
