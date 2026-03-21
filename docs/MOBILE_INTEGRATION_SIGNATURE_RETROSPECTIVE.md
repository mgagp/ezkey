# Mobile integration signature verification — retrospective

This note captures what we learned from debugging **Pending** integration signature verification on Android, separates **root cause** from **iterative noise**, and outlines a path toward **simpler, platform-native** client code and **stronger automated tests**.

## What actually went wrong (root cause)

### Primary: SPKI / public key encoding mismatch (not the signature bytes)

The Auth API signs with BouncyCastle using **`PublicKeyFactory.createKey(keyBytes)`** where `keyBytes` are the **raw Base64-decoded** integration public key material (SubjectPublicKeyInfo or equivalent after normalization on the server).

On the mobile app, an earlier verification path did:

1. Parse the key with **JCA** (`KeyFactory`, certificate fallback, etc.).
2. Use **`PublicKey.getEncoded()`** and feed that into BouncyCastle.

For EC keys, **JCA can re-encode** SubjectPublicKeyInfo differently from the original DER (e.g. named curve OID vs explicit parameters, point representation). The **same mathematical public key** can yield **different `ECPublicKeyParameters`** inside BouncyCastle than **`PublicKeyFactory.createKey(originalBytes)`**. ECDSA verification then fails **even when** payload and signature Base64 strings are bit-identical to the server logs.

**Conclusion:** The decisive fix was to **verify with the same key material pipeline as the backend**: decode Base64 → **`PublicKeyFactory.createKey(rawBytes)`** (plus certificate / BC-KeyFactory fallbacks where needed), **not** a JCA round-trip to SPKI.

### Secondary: ECDSA “strict” verification on Android (Conscrypt)

Android’s default `Signature` provider (**Conscrypt**) can reject some **valid** ECDSA signatures that **OpenJDK** accepts (commonly discussed in the context of **low-`s`** / canonical signature form). That can produce **verify = false** on device while server-side **JCA** self-check still passes.

**Conclusion:** **Low-`s` normalization** on the server (after BC signing, before DER encoding) remains a **reasonable interoperability measure** for mobile verifiers that use **only** `SHA256withECDSA` + default provider. It is **not** a substitute for correct key bytes; it addresses a **different** class of failures.

### What was “noise” in the investigation

- Repeated **manual** checks (hashes, screenshots) were necessary because we lacked a **single failing automated test** that reproduced the device behaviour.
- Multiple hypotheses (low-S only, Conscrypt only) were **partially** true; the **blocking** issue for this project was the **key encoding path**, once payload/signature strings were proven identical.

## What is strictly required *today*

| Measure | Role | Safe to remove? |
|--------|------|------------------|
| **Raw-bytes → `PublicKeyFactory` (or equivalent) on mobile** | Matches backend verification semantics | **No** — this is the correctness fix. |
| **Server low-S normalization** | Improves odds that **standard** `SHA256withECDSA` on Android accepts the DER signature | **Risky** to remove until clients rely only on platform JCA and you have tests proving no high-S rejections. |
| **BC JCA provider / fallbacks on mobile** | Defence in depth after Conscrypt quirks | **Can be simplified later** if contract tests prove **default** JCA verify passes for all emitted signatures and keys. |

## Is “mimic the backend with BouncyCastle on mobile” the right long-term shape?

**No — it’s a reasonable bridge, not the destination.**

- **Backend-first** teams naturally align the client with **library-level** behaviour (BC) because the server uses BC.
- **Mobile-first** practice is normally: **platform crypto** (`java.security.Signature`, Keystore, **no** extra provider unless there is a clear requirement).

The current mobile stack uses BC because it was the fastest way to match **`SignatureService.validateSignature`** and work around **Conscrypt** edge cases. That is **pragmatic**, but it increases **dependency surface** and **cognitive load** for mobile developers.

## Target architecture (gradual migration)

**Goal:** The mobile app verifies integration signatures with **only**:

- `Signature.getInstance("SHA256withECDSA")`
- `KeyFactory.getInstance("EC")` + `X509EncodedKeySpec` **from the same bytes the product considers canonical** (typically normalized SPKI Base64 from enrollment)

…and **no** requirement to mirror BouncyCastle’s low-level API.

**Backend evolution (incremental, test-driven):**

1. **Emit** integration public keys in **one canonical form** (e.g. always SubjectPublicKeyInfo Base64 after normalization — already partly addressed by `normalizeIntegrationPublicKeyToBase64`).
2. **Sign** in a way that **always** verifies with:
   - **JCA** `SHA256withECDSA` on OpenJDK, and
   - **Default** Android `Signature` (Conscrypt),  
   using the **same** UTF-8 payload bytes as clients.
3. Keep **low-S** (or move signing to **JCA** `SHA256withECDSA` entirely) so mobile does not need BC-specific verification.
4. **Contract tests** in CI: vectors `(payload, signatureBase64, integrationPublicKeyBase64)` generated by the **same** code path as production signing — assert **true** on a **Conscrypt**-backed verifier (or Android emulator test) as well as JVM.

**Mobile evolution:**

1. Keep the **raw-bytes / SPKI correctness** lesson: never assume `PublicKey.getEncoded()` equals enrollment bytes.
2. Replace BC-heavy paths with **platform JCA** once backend contract tests guarantee it.
3. Retain a **small** BC or test-only module only if you must support **legacy** stored key formats.

## How we could have diagnosed faster with tests (TDD-oriented)

### What was missing

- A **failing unit test** that said: “given **these exact three strings** (payload, signature, public key) from a real Pending response, `verify()` must return **true**.”
- A test that distinguishes **key parsing** vs **signature policy**:
  - Same payload + signature, but verify once with **raw SPKI bytes** into BC and once with **JCA round-trip** → would have shown **only the first** passes.

### Recommended test layers

1. **Golden-vector tests (Kotlin JVM, `testDebugUnitTest`)**  
   - Build vectors from **Java** (`SignatureService.generateSignature` + enrollment key material) or from a **fixture export** script.  
   - Assert **`IntegrationKeyVerifier.verify(...)`** is true.  
   - Add a **regression** test: “JCA parse → `.encoded` → BC verify” **may fail** while “raw bytes → BC verify” passes — document as **known anti-pattern**.

2. **Conscrypt / Android fidelity**  
   - JVM unit tests use the **default** Sun provider; device uses **Conscrypt**.  
   - Add **one** test that runs `Signature.getInstance("SHA256withECDSA")` with **Conscrypt** if the dependency is available in the test classpath, or an **instrumented** test on emulator.

3. **Payload contract tests** (already partly in place)  
   - Shared examples for NFC, `true`/`false`, empty context — compare **UTF-8 hex** of payload between TypeScript builder and Java `AuthAttemptSignaturePayload`.

4. **Process**  
   - When adding crypto interop, **freeze a failing vector first** (red), then fix (green), then refactor — avoids iterating only on device.

## Summary

- **True root cause:** Verification used **different EC public key material** than the backend (JCA-reserialized SPKI vs raw decoded bytes), not a mystery hash mismatch.
- **Secondary:** Android **Conscrypt** can be stricter than OpenJDK for ECDSA; **low-S** on the server helps **plain JCA** clients.
- **Direction:** Tighten **backend contracts** and **tests** so the **mobile** path can be **boring platform crypto**, and treat BouncyCastle on the client as **temporary** unless a product requirement forces it.

This document should be updated when the backend emits signatures that are proven **Conscrypt-clean** and mobile drops BC from the integration verification path.
