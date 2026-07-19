# Handoff — MOB-006 Native Keystore / StrongBox test coverage

**Status:** implemented — PR [#386](https://github.com/mgagp/ezkey/pull/386)  
**Finding:** MOB-006 (P2)  

**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

JVM unit tests cover `SealedSecretEnvelope` and `IntegrationKeyVerifier` only. There is no
`androidTest` coverage for `EzkeyCryptoModule` (key gen, StrongBox fallback, tier reporting,
delete, seal via module, biometric callbacks). Maestro pilots UI approve paths but do not assert
hardware tier.

## In scope

1. Add focused Android instrumentation tests under `ezkey_mobile/android/app/src/androidTest/` for:
   - enrollment key alias create + `getPublicKey` + `sign` round-trip (emulator OK),
   - `deleteKeyPair` removes alias,
   - `sealSecret` / `unsealSecret` via module (Keystore AES),
   - `getEnrollmentPrivateKeyStorageTier` returns one of NONE/STANDARD/STRONG.
2. Document a **manual StrongBox checklist** for physical devices (request StrongBox, observe tier,
   fallback when unavailable).
3. Wire Gradle/`package.json` script notes in `ezkey_mobile/AGENTS.md` if a new test task is added.
4. Do **not** claim CI StrongBox; mark hardware assertions as manual/device-labeled.

## Out of scope

- Implementing CryptoObject (MOB-001 Track B)
- Full Maestro enrollment QR automation
- iOS XCTests

## Read first

- `ezkey_mobile/AGENTS.md` (JDK 17 for Android — not JDK 25)
- `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/EzkeyCryptoModule.kt`
- `ezkey_mobile/android/app/src/test/kotlin/.../SealedSecretEnvelopeTest.kt`
- `ezkey_mobile/maestro/README.md`
- `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md` (if present)

## Validation

| Layer | Required? |
| --- | --- |
| Emulator instrumentation | **Yes** for Keystore basics |
| Physical StrongBox phone | **Yes** for STRONG tier / StrongBox fallback evidence |
| Docker functional | No |

## Acceptance

- `androidTest` suite runs on emulator for non-StrongBox assertions.
- Checklist exists for StrongBox-capable phone evidence.
- AGENTS.md mentions how to run the new tests with the JDK 17 resolver scripts.

## Suggested session opening message (copy-paste)

```
Implement MOB-006 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Add Android instrumentation coverage for EzkeyCryptoModule Keystore lifecycle and a StrongBox
manual checklist. Use JDK 17 via ezkey_mobile scripts — never JDK 25. Read
product-docs/global/backlog/handoffs/HANDOFF-mob-006-native-keystore-tests.md and ezkey_mobile/AGENTS.md.
```
