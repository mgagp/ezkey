# Ezkey Mobile — Agent Notes

For agents working in `ezkey_mobile/`.

## Purpose

Cross-platform companion app for Ezkey MFA: enrollment via QR, secure key management, and authentication approvals. Consumes the **Auth API** (port 8080). All content must be **in English**.

---

## Tech Stack (Brief)

| Layer | Technology |
|-------|------------|
| Framework | React Native 0.76 + TypeScript (strict) |
| Native | Android: Kotlin · iOS: Swift / Objective-C++ |
| Navigation | React Navigation (native-stack, stack) |
| State | Zustand + TanStack React Query v5 |
| HTTP | Axios (shared client, timeouts, error handling) |
| Camera / QR | react-native-vision-camera + custom frame processor |
| Secure Storage | react-native-keychain |
| Crypto | Native modules (EzkeyCryptoModule) — EC P-256, Android Keystore today, iOS secure-hardware-backed parity still in progress |
| Config | react-native-config (.env) |
| Package Manager | Yarn 4 (Berry) |

---

## Project Values (Mobile-Specific)

1. **Extremely simple, voluntarily minimalist** — The app must stay lightweight. Avoid feature creep, unnecessary screens, or complex flows. Every screen and interaction should justify its existence.
2. **Security first** — Proof tokens and keys are handled in memory or secure storage only. Native crypto must use the platform keystore path; describe Android as `StrongBox when available`, not as a universal hardware guarantee.
3. **Pull-based model** — Polling for auth attempts is user-initiated only. No background polling that could enable enumeration or replay.
4. **Pragmatic UX** — Focus on the core flows: enroll, list enrollments, approve/deny. No decorative complexity.

---

## Deprecated / Removed Concepts

- **Integration logo** — The notion of integration logo has been completely removed. Do not add logo display, `logoUri`, or `integrationLogo` to the mobile app. The Auth API may still return legacy fields; ignore them.

---

## Non-Negotiables

- All new/updated content **in English**.
- Never break Auth API contracts (`EnrollmentBindResponseDto`, `AuthAttemptPendingResponseDto`) without coordinating with backend.
- Proof tokens: read-once semantics; never cache in plaintext outside secure storage.
- EC P-256 keys: generated per enrollment through the native keystore path; for Android, prefer `Android Keystore` and `StrongBox when available` wording.

---

## Project Structure

```
app/
  components/         Reusable UI (e.g. EnrollmentScannerModal)
  hooks/              React Query + storage orchestration
  navigation/         Stack navigator + types
  providers/          App-wide context providers
  screens/            Home, EnrollmentWizard, PendingAuth, EnrollmentDetail, Diagnostics
  services/
    api/              REST clients (enrollments, authAttempts), httpClient
    crypto/           Native crypto integration layer
    storage/          Secure + metadata storage abstractions
  state/              Zustand stores (e.g. enrollmentStore)
  utils/              urlValidation, tenantGrouping
android/              Native Android (Kotlin) — EzkeyCryptoModule, EzkeyQrFrameProcessorPlugin
ios/                  Native iOS (Swift/Obj-C++) — EzkeyCryptoModule
```

---

## Key References

- [`docs/MOBILE_ARCHITECTURE.md`](docs/MOBILE_ARCHITECTURE.md) — Architecture and layer overview
- [`docs/NATIVE_MODULES.md`](docs/NATIVE_MODULES.md) — Native module responsibilities
- [`docs/ENDPOINT.md`](../docs/ENDPOINT.md) — Auth API contract
- [`docs/CRYPTO.md`](../docs/CRYPTO.md) — Cryptographic requirements

---

## Launcher icon (Android)

Regenerate `mipmap-*` PNGs from the repo root `logo.svg`:

```bash
pip install -r scripts/requirements-generate-icons.txt
python scripts/generate_android_launcher_icons.py
```

See [`scripts/README.md`](scripts/README.md).

---

## Running and Testing

```bash
yarn install
yarn ios          # iOS simulator
yarn android      # Android emulator/device
yarn start        # Metro bundler only
yarn lint
yarn typecheck
yarn test
```

Consider these rules if they affect your changes.
