<!--
  Ezkey - Open Source Cryptographic MFA Platform
  Copyright (c) 2026 Ezkey contributors
  Licensed under the MIT License. See LICENSE file in the project root for full license information.
-->

# StrongBox manual checklist (physical Android)

**Finding:** MOB-006  
**Audience:** maintainer / QA with a StrongBox-capable phone  
**Not CI:** instrumentation tests on emulator assert only that the storage tier is one of
`NONE` / `STANDARD` / `STRONG`. They must **not** be treated as StrongBox proof.

## Why this checklist exists

`EzkeyCryptoModule` requests StrongBox for enrollment EC keys and the app seal AES key, then
falls back to regular Android Keystore when StrongBox is unavailable. Admin UI / product language
must not claim server-verified StrongBox. This checklist records **device-side** evidence only.

## Prerequisites

- Physical Android phone that advertises StrongBox (Pixel and many recent flagships).
- Debug APK built with JDK 17 via `ezkey_mobile` scripts (never JDK 25).
- `adb` connected (`adb devices -l`).
- Optional: logcat filter `EzkeyCrypto`.

## Checklist

1. **Install debug build** from `ezkey_mobile/`:

   ```bash
   ./scripts/build-install-debug-clean.sh
   ```

2. **Enroll** a fresh enrollment on the phone (QR or seed path). Complete bind/verify so a
   Keystore enrollment alias exists.

3. **Observe storage tier** (pick one):
   - After verify, confirm the app/backend path that surfaces `devicePrivateKeyStorageTier` shows
     `STRONG` on a StrongBox device when StrongBox placement succeeded; or
   - Run instrumentation on the **same physical device** and note the tier string printed by
     `getEnrollmentPrivateKeyStorageTier` (still client-reported, not attestation).

4. **StrongBox request + fallback** (when testing a non-StrongBox or restricted profile):
   - Confirm enrollment key generation still succeeds.
   - Confirm logcat may contain
     `StrongBox unavailable for enrollment key; falling back to regular Keystore`
     (or the seal-key equivalent).
   - Confirm tier is `STANDARD` or `NONE`, never a crash / stuck promise.

5. **Seal path smoke:** after enrollment, kill and relaunch the app; pending/respond still works
   (app seal key can decrypt stored `enrollmentProofToken` / `integrationPublicKey`).

6. **Record evidence** in the campaign note or PR (device model, Android version, observed tier,
   whether fallback log appeared). Do not claim CI or server attestation.

## Related automation

| Layer | Command / location | StrongBox claim? |
| --- | --- | --- |
| Emulator instrumentation | `yarn android:test:instrumented:crypto` | **No** — tier membership only |
| JVM unit tests | `./gradlew :app:testDebugUnitTest` | Envelope / Ed25519 only |
| Maestro approve pilot | `maestro/README.md` | UI path; does not assert tier |

## Non-claims

- Backend does **not** verify StrongBox or Key Attestation today.
- `STRONG` is client-reported telemetry for Auth API verify metadata.
- Passing emulator `androidTest` does **not** mean StrongBox works.

## Evidence log

| Date | Device | Android | Observed tier | Notes |
| --- | --- | --- | --- | --- |
| 2026-07-19 | Pixel 7 Pro (`cheetah`) | 17 | `STRONG` | logcat `EzkeyCryptoTest`: `MOB-006 storage tier evidence: STRONG`; 4/4 `connectedDebugAndroidTest`; no fallback log (StrongBox present). Campaign: `product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`. |
