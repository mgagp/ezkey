# Mobile Pending "Unable to load request" — Debug plan

## Context

- **Change**: Cryptographic binding of context (contextTitle, contextMessage) and respond value (authAttemptAccepted) per [AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md). Admin UI + demo device work; mobile shows "Unable to load request" with `OpenSSLX509CertificateFactory$ParsingException`.
- **Scope**: Base app `ezkey_mobile` only (not the revamp app). Enrollment works; failure happens when checking pending auth.

## Hypotheses (to confirm or reject via logs)

1. **A – verify() fails parsing integration public key**  
   Kotlin `verify()` uses `X509EncodedKeySpec` + `KeyFactory.getInstance("EC")`. If `integrationPublicKey` is not valid SubjectPublicKeyInfo (e.g. certificate, corrupted base64, or wrong encoding), Conscrypt can throw a parsing exception.

2. **B – Failure during HTTP call to pending**  
   TLS or certificate validation when calling the Auth API (e.g. custom trust or pinning) triggers certificate parsing and throws.

3. **C – Failure in ensureEnrollmentKeyPair or sign()**  
   Device key path (KeyStore/certificate) throws before we reach the pending API or verify.

4. **D – integrationPublicKey corrupted in storage**  
   Value saved at enrollment (from bind response) is truncated or re-encoded when stored/loaded (e.g. AsyncStorage/JSON). Compare **integrationPublicKey length** in the Debug box to the expected Base64 SPKI length from bind.

5. **E – Backend or API returns different key format**  
   Auth API returns something other than Base64 SubjectPublicKeyInfo (e.g. PEM, or full X.509 certificate); unchanged backend makes this less likely but possible if format changed elsewhere.

## Instrumentation

- **On-screen debug (no server needed)**: When "Unable to load request" is shown, a **Debug (for support)** box appears below the error with:
  - **Last step**: which step was last reached (`start`, `after_ensure`, `after_pending`, `before_verify`, `after_verify`, or `catch`). If you see `before_verify` then the failure is in `cryptoService.verify()` (integration public key parsing).
  - **integrationPublicKey length**: number of characters (only set when we reached `before_verify`). Helps spot truncated or wrong format.
  - **Error**: full error message (same as the red text, copyable).

## Build and run (reference)

From repo root, using Bash (Git Bash on Windows):

```bash
# Dependencies
cd ezkey_mobile
yarn install

# Clean and run (recommended before testing)
yarn start
# In another terminal:
yarn android
```

**Clean build (if you suspect cache):**

```bash
cd ezkey_mobile
# Clear Metro cache
yarn start --reset-cache
# In another terminal: clean Android build and run
cd android && ./gradlew clean && cd .. && yarn android
```

**Common pitfalls:**

- Run `yarn android` (or press **A** in Metro) after Metro is up; otherwise the bundle may be stale.
- After code changes, reload the app (double-tap R in Metro, or shake device → Reload). For native (Kotlin) changes, rebuild: `yarn android` again.
- Debug info is shown on the device in the "Debug (for support)" box when an error occurs; no log file or server is required.

## Backend ↔ Android signature parity (reference)

`ezkey-core` signs pending payloads with BouncyCastle; the mobile verifies with JCA `SHA256withECDSA` in `IntegrationKeyVerifier`. **Regression tests** in `SignatureServiceTest` (`testBouncyCastleSignatureVerifiesWithJcaSha256WithEcdsa`, `testJcaVerifiesPendingPayloadShape`) assert that signatures produced by `SignatureService.generateSignature` verify with the same JCA path the app uses.

After each pending signature, **Auth API** runs **JCA self-verification** with the normalized enrollment public key. If you see **ERROR** `Pending integration signature failed JCA self-verification`, the problem is on the **server / enrollment keys**, not the phone. If that line **never** appears but the app still shows `signature valid: false`, compare **logcat** (`adb logcat | findstr EzkeyCrypto`) for `verify returned false` vs exceptions.

## Reading the debug box (your screenshot)

- **Last step `after_verify`** means native `verify()` ran and returned **false** (cryptographic mismatch), not a thrown error before verify.
- **pendingPayload** `...|false||` matches a pending auth with no per-attempt challenge and no context — consistent with a minimal test.
- **integrationPublicKey length 448** is longer than a bare P-256 SPKI Base64 (~124 chars); the verifier still accepts **certificate** or double-encoded forms if parsing succeeds. If JCA self-verify passes on the server, the key material is consistent for signing on the JVM.
