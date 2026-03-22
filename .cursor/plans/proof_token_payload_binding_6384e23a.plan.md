---
name: Proof token payload binding
overview: Extend the authentication protocol so that (1) the Pending response binds cryptographically the proof token to authAttemptChallengeRequired, contextTitle, and contextMessage (integration signature), and (2) the Respond request binds the proof token to authAttemptAccepted (device signature). This closes the MITM tampering gaps described in audit item V4 and the analogous Respond gap, across backend, demo device, and both mobile apps.
todos: []
isProject: false
---

# Cryptographic binding of Pending/Respond payloads (V4 + Respond)

## 1. Confirmation: cryptographic continuity (Pending → Respond)

Your understanding is correct. The flow is:

- **Pending**: Backend returns `authAttemptProofToken` (plain) and `authAttemptProofTokenSignedByIntegration`. The device stores the token and will use it later.
- **Respond**: The device sends `authAttemptProofTokenSignedByDevice` = signature over the **same** proof token (with the private key of the device). The backend verifies that signature using the device public key and the stored proof token.

So the only way to produce a valid `authAttemptProofTokenSignedByDevice` is to have received that exact proof token during Pending. That gives **continuity**: the Respond is cryptographically tied to the Pending. An attacker cannot respond to an attempt without having first obtained the token from a valid Pending. This design is sound.

---

## 2. Gaps identified (MITM tampering)

### 2.1 Pending (audit V4)

Today the integration signs only `authAttemptProofToken`. The fields `authAttemptChallengeRequired`, `contextTitle`, and `contextMessage` are sent in the same JSON but **are not part of the signed payload**. A MITM between Auth API and the device can:

- Change context (e.g. "50€" → "3,000,000€").
- Set `authAttemptChallengeRequired` to `false` to bypass the challenge.

So the user can be tricked into approving something different from what the integration intended.

### 2.2 Respond (same class of issue)

Today the device signs only `authAttemptProofToken`. The field `authAttemptAccepted` is sent in the same request but **is not part of the signed payload**. The backend verifies only that the signature is over the proof token ([AuthAttemptRespondService.java](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java) lines 218–223). A MITM between device and Auth API can:

- Change `authAttemptAccepted` from `false` to `true`, turning a user denial into an approval.

As you noted, in a high-value context (e.g. "Transfer 3M€") flipping false→true is critical. So both directions (Pending and Respond) need the same remedy: **bind every security-relevant field to the signature**.

---

## 3. Proposed strategy (canonical payloads)

### 3.1 Canonical format

- **Separator**: Single character `|` (no spaces). No trailing separator.
- **Nulls**: Use empty string for null (so no literal `"null"`).
- **Boolean**: Lowercase `"true"` or `"false"`.

### 3.2 Text canonicalization: Unicode NFC + UTF-8

Context fields (`contextTitle`, `contextMessage`) can contain accented or composed characters (e.g. French "é", "à"). Unicode allows multiple byte-level representations for the same visual character (e.g. "é" as U+00E9 vs "e" + U+0301). If backend and clients use different representations, the same logical string would produce different signatures and verification would fail.

**Strategy:**

1. **Unicode NFC (Canonical Composition)**
  Normalize every text field that goes into the signed payload to **NFC** before building the payload. NFC reduces equivalent sequences to a single canonical form, so "é" is always the same code point(s) regardless of input encoding or platform.
2. **UTF-8**
  Treat the payload as a **UTF-8** byte sequence for hashing/signing. All stacks (Java, Kotlin, JS) already use UTF-8 by default for strings; the spec should state this explicitly so that (a) payload bytes are unambiguous and (b) future implementations (e.g. other languages) do not use another encoding.
3. **Where to apply NFC**
  Apply NFC only to **user-facing text** that can contain accents: `contextTitle` and `contextMessage`. The proof token and the literals `"true"` / `"false"` are ASCII and need no normalization.

**Implementation:**

- **Java (backend, demo device)**: `java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)`. Use for non-null `contextTitle` / `contextMessage` before concatenating into the payload. Null stays null (then mapped to `""` in the payload).
- **Kotlin (mobile native)**: Same: `java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)` (Kotlin/JVM has access to `java.text.Normalizer`). Apply when building the payload for verification or when passing text from JS to native if the payload is built there.
- **JavaScript/TypeScript (mobile)**: `string.normalize('NFC')` for `contextTitle` / `contextMessage` before building the payload. Supported in all modern runtimes.

**Reference:** Unicode Standard Annex #15 (UAX #15) — NFC is the recommended form for “canonical representation” and is stable across platforms.

### 3.3 Canonical JSON (future use)

The current payload is a simple concatenation `token|bool|title|message` (or `token|accepted` for respond), not JSON. No canonical JSON is required for this design.

If in the future the signed payload is extended to a **structured object** (e.g. key-value pairs or nested data), then **canonical JSON serialization** should be used: deterministic key order (e.g. lexicographic), no unnecessary whitespace, defined number/string escaping, and UTF-8 encoding. That would avoid signature mismatches due to different serialization. For the present scope, the pipe-separated format plus NFC + UTF-8 is sufficient.

### 3.4 Pending — integration signs

**Payload to sign (integration, when building pending response):**

```text
{proofToken}|{challengeRequired}|{contextTitle}|{contextMessage}
```

- `challengeRequired`: `"true"` or `"false"` (same value as in the JSON `authAttemptChallengeRequired`).
- `contextTitle` / `contextMessage`: value or `""` if null.

**Backend** ([AuthAttemptPendingService.buildPendingResponse](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java)): Compute this string (using the same logic as today for `authAttemptChallengeRequired`), then call `signatureService.generateSignature(payload, integrationPrivateKey)`. The proof token value itself does not change; only the signed payload is extended.

**Clients (demo device, ezkey_mobile, ezkey_mobile_app)**: Before trusting the pending response, verify the integration signature over this same canonical payload. If verification fails, reject the response (do not show context or allow respond).

### 3.5 Respond — device signs

**Payload to sign (device, when sending respond):**

```text
{proofToken}|{accepted}
```

- `accepted`: `"true"` or `"false"` (same as the JSON `authAttemptAccepted`).

**Backend** ([AuthAttemptRespondService.validateDeviceSignature](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java)): Rebuild this payload from `authAttempt.getAuthAttemptProofToken()` and `request.getAuthAttemptAccepted()`, then call `signatureService.validateSignature(payload, request.getAuthAttemptProofTokenSignedByDevice(), enrollment.getDevicePublicKey())`.

**Clients**: When calling respond, sign this payload (not the proof token alone) and send the resulting `authAttemptProofTokenSignedByDevice`.

---

## 4. Redundancy in the proof token

The proof token (random value) stays as-is. The **signed payload** is what we extend: for Pending we sign (token + challengeRequired + contextTitle + contextMessage); for Respond we sign (token + accepted). So there is no structural change to the token; there is a small redundancy in the sense that the same token appears both as a field and inside the signed string. That is acceptable and standard (signing a fingerprint of the full message).

---

## 5. Scope of changes (by component)


| Component                           | Pending                                                                                       | Respond                           |
| ----------------------------------- | --------------------------------------------------------------------------------------------- | --------------------------------- |
| **Backend (ezkey-core)**            | Build extended payload; sign it in `buildPendingResponse`.                                    | Verify signature over `proofToken |
| **Demo device (ezkey-demo-device)** | Verify integration signature over extended payload before showing pending UI.                 | Sign `proofToken                  |
| **ezkey_mobile**                    | Verify integration signature over extended payload (need native `verify`); reject if invalid. | Sign `proofToken                  |
| **ezkey_mobile_app**                | Same as ezkey_mobile.                                                                         | Same as ezkey_mobile.             |


### 5.1 Kotlin crypto modules (ezkey_mobile vs ezkey_mobile_app)

Confirmed: the two Android native modules are effectively the same:

- [ezkey_mobile EzkeyCryptoModule.kt](ezkey_mobile/android/app/src/main/java/com/ezkeymobile/crypto/EzkeyCryptoModule.kt)
- [ezkey_mobile_app EzkeyCryptoModule.kt](ezkey_mobile_app/android/app/src/main/java/com/ezkeymobileapp/crypto/EzkeyCryptoModule.kt)

Only the package name differs (`com.ezkeymobile.crypto` vs `com.ezkeymobileapp.crypto`). They expose the same API: `sign(enrollmentId, data)` (no `verify` today). So:

- **Same change in both**: add a `verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean` (or equivalent) for ECDSA-SHA256, and use the same canonical payload construction in the JS/TS layer for both pending verification and respond signing.

---

## 6. Implementation details (concise)

### 6.1 Backend (ezkey-core)

- **AuthAttemptPendingService.buildPendingResponse**: Compute `challengeRequired` as today. For `contextTitle` and `contextMessage`, apply **NFC normalization** when non-null: `Normalizer.normalize(s, Normalizer.Form.NFC)`. Build `payload = proofToken + "|" + (challengeRequired ? "true" : "false") + "|" + (contextTitle != null ? normalized(contextTitle) : "") + "|" + (contextMessage != null ? normalized(contextMessage) : "")`. Encode/sign as **UTF-8** (Java `StandardCharsets.UTF_8`). Call `generateSignature(payload, integrationPrivateKey)`.
- **AuthAttemptRespondService.validateDeviceSignature**: Build `payload = authAttempt.getAuthAttemptProofToken() + "|" + (Boolean.TRUE.equals(request.getAuthAttemptAccepted()) ? "true" : "false")`. Signatures are already over UTF-8 bytes. Call `validateSignature(payload, ...)`.

### 6.2 Demo device (Java)

- **EzkeyAppController** (pending): Build the same canonical string from `pendingResponse`; apply **NFC** to non-null `contextTitle` / `contextMessage`. Use **UTF-8** for the payload. Call `cryptoService.validateSignature(payload, pendingResponse.getAuthAttemptProofTokenSignedByIntegration(), integrationPublicKey)`. On failure, return error and do not show pending UI.
- **EzkeyAppController** (respond): Build `payload = authAttemptProofToken + "|" + (approved ? "true" : "false")` (UTF-8). Call `cryptoService.signStringToBase64(payload, rec.devicePrivateKey())` and send that as `authAttemptProofTokenSignedByDevice`.

### 6.3 Mobile apps (ezkey_mobile, ezkey_mobile_app)

- **Native (Kotlin)**: Add `verify(data, signatureBase64, publicKeyBase64)` in both EzkeyCryptoModule implementations (ECDSA-SHA256, UTF-8). Expose to JS/TS.
- **JS/TS (pending)**: After receiving pending response, normalize `contextTitle` and `contextMessage` with `**string.normalize('NFC')`** when building the payload. Use UTF-8 (default in JS). Build the same canonical payload and call native `verify(payload, authAttemptProofTokenSignedByIntegration, integrationPublicKey)`. If false, do not set attempt (treat as invalid / show error).
- **JS/TS (respond)**: Build `payload = attempt.authAttemptProofToken + "|" + (accepted ? "true" : "false")` (UTF-8). Call `crypto.sign(enrollmentId, payload)`. Send result as `authAttemptProofTokenSignedByDevice`.

Enrollment storage already has `integrationPublicKey` (from bind) in both apps; use it for pending verification.

---

## 7. Tests and verification

- **Backend**: Unit tests for `buildPendingResponse` (signature covers extended payload; **NFC**: same context string in NFC form produces same signature) and for `validateDeviceSignature` (accept when signature over `token|true` / `token|false`, reject when payload or signature is tampered). Integration test: modify context or challengeRequired in a pending response, ensure device-side verification would fail. **NFC test**: context with accented characters (e.g. "Virement de 50€") normalized to NFC on both sides yields a valid signature; non-NFC form must be normalized before comparison.
- **Security test (existing suite)**: Add case “context modified in pending response → signature invalid” (backend generates correct signature; test substitutes modified context and asserts verification fails). Add case “respond with wrong accepted value in payload → backend rejects” (device signs token|true but request sends authAttemptAccepted=false → backend verification fails).
- **Demo device**: Manual or automated test that tampered pending payload fails verification; respond with correct payload succeeds.
- **Mobile**: Same logic; optional E2E if available.

---

## 8. Optional hardening: signed Respond response

Today the **response** of Respond (result: APPROVED/DENIED/FAILED) is plain JSON. A MITM could flip the result shown to the user (e.g. backend returns DENIED but attacker changes it to APPROVED so the user thinks they approved). Addressing that would require the backend to sign the respond response (e.g. result + authAttemptId) and the mobile to verify that signature with the integration public key. This is a separate, smaller improvement and can be done in a follow-up if desired.

---

## 9. Critical observations and challenges

**Context**: Full development only — no production deployment. No backward-compatibility or transition plan is required; we are defining the current and future behaviour.

1. **Canonical encoding**: Backend, demo device, and both mobile apps must use the **exact** same string: separator `|`, null→`""`, boolean `"true"`/`"false"`, and **Unicode NFC** for `contextTitle`/`contextMessage` plus **UTF-8** for the payload. Any mismatch (e.g. "True" vs "true", or NFD vs NFC for "é") will break verification. Recommendation: define a small shared spec (e.g. in `docs/`) that specifies NFC + UTF-8 and reference it from all four codebases.
2. **ezkey_mobile / ezkey_mobile_app do not verify integration signature today**: They receive and display context but do not verify `authAttemptProofTokenSignedByIntegration`. Adding verification is required for V4; otherwise a MITM can still change context and the app would display it. So the plan correctly includes “verify integration signature on pending” for both apps.
3. **No ambiguity in the design**: Binding context and challengeRequired to the integration signature, and accepted to the device signature, gives end-to-end integrity for the data that drives the user’s decision and the backend’s outcome. An auditor can see that no security-relevant field is transmitted without being covered by a signature.

---

## 10. Update to the audit plan document

After implementation, update [plan-authProtocolSecurityAudit.prompt.md](.github/prompts/plan-authProtocolSecurityAudit.prompt.md):

- **Step 4 (V4)**: Reflect that the remediation now includes (1) integration signs `proofToken|challengeRequired|contextTitle|contextMessage`, (2) device signs `proofToken|accepted` on respond, and (3) all clients verify/sign accordingly. Add a verification step for “respond payload includes authAttemptAccepted”.
- Add a short “Respond payload binding” item to the Steps list (or fold into step 4) so the respond-side change is explicit in the audit plan.

This keeps the audit plan and the code in sync and makes the end-to-end binding visible to future reviewers.
