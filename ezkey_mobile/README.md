# Ezkey Mobile (React Native)

> Cross-platform companion app that manages Ezkey enrollments and handles MFA approvals through native cryptography.

## Overview

- **Stack**: React Native 0.76 + TypeScript with dedicated Android (Kotlin) and iOS (Swift/Obj-C++) native modules
- **Primary Flows**: Enrollment via QR, secure key generation, pending authentication approvals/denials, challenge handling
- **APIs Consumed**: `auth-api` endpoints documented in [`docs/ENDPOINT.md`](../docs/ENDPOINT.md)
- **Security Alignment**: Mirrors the guarantees detailed in [`docs/CRYPTO.md`](../docs/CRYPTO.md) and [`docs/features/AUTH_SECURITY.md`](../docs/features/AUTH_SECURITY.md)

## Core Capabilities

- Guided enrollment wizard with QR scanning, challenge validation, and EC P-256 key provisioning
- Local enrollment catalogue with enrollment metadata storage, detail views, and pending-auth shortcuts
- Manual, user-driven polling for authentication attempts followed by approve/deny flows
- Shared Axios client with deterministic timeouts and error handling suitable for mobile networks
- Native crypto bridge (Kotlin/Swift) that exposes EC P-256 key management with hardware-backed storage (Android Keystore StrongBox, iOS Secure Enclave)

## Security Posture

- Proof tokens and signatures are always handled in memory; sensitive values are stored via secure storage abstractions only
- Enrollment and authentication requests follow the pull-based model that avoids background polling to prevent enumeration or replay
- Device credentials use EC P-256 (ECDSA-SHA256) as specified in [`docs/CRYPTO.md`](../docs/CRYPTO.md): PKCS#8 private key, X.509 public key, hardware-backed in platform keystore
- Per-enrollment EC P-256 key pairs generated natively in hardware-backed storage (Android Keystore StrongBox, iOS Secure Enclave); private keys are non-extractable
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

Create `.env` inside `ezkey_mobile/`:

```
EZKEY_API_BASE_URL=https://goateed-katalina-monsoonal.ngrok-free.dev
EZKEY_REQUEST_TIMEOUT=10000
```

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

- `EzkeyCryptoModule` (Kotlin/Swift) exposes EC P-256 key generation, retrieval, and signing with hardware-backed storage (Android Keystore, iOS Secure Enclave)
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
