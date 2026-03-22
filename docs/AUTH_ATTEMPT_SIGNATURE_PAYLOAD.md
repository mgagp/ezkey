# Auth attempt signature payload (canonical format)

This document defines the canonical payload format used when signing or verifying auth attempt Pending and Respond messages. Backend (ezkey-core), demo device (ezkey-demo-device), and mobile apps (ezkey_mobile, ezkey_mobile_app) must all build and verify payloads identically.

## Encoding

- **Character encoding**: UTF-8. The payload string is encoded as UTF-8 bytes for hashing/signing.
- **Text normalization**: Unicode NFC (Canonical Composition), per Unicode Standard Annex #15 (UAX #15). Applied only to user-facing text fields that can contain accents: `contextTitle` and `contextMessage`. The proof token and the literals `"true"`/`"false"` are ASCII and are not normalized.

## Pending (integration signs)

**Payload to sign:** `{proofToken}|{challengeRequired}|{contextTitle}|{contextMessage}`

- **Separator**: Single character `|` (U+007C). No spaces.
- **proofToken**: The auth attempt proof token value (unchanged).
- **challengeRequired**: Literal string `"true"` or `"false"` (lowercase), matching the JSON field `authAttemptChallengeRequired`.
- **contextTitle**: Value of the context title, or empty string if null. Must be NFC-normalized before concatenation.
- **contextMessage**: Value of the context message, or empty string if null. Must be NFC-normalized before concatenation.

**Example:** `abc123token|true|Virement|Virement de 50€` (NFC-normalized French text).

Clients must build this exact string (with NFC for title/message), then verify the integration signature over it. If verification fails, reject the pending response.

### ECDSA signature encoding (integration → clients)

- **Algorithm**: `SHA256withECDSA` with **secp256r1** (P-256).
- **Wire format**: ASN.1 DER `(r, s)`, then **Base64** (standard URL-safe or classic Base64 as returned by the API).
- **Low-S (canonical) form**: The backend normalizes **s** to the lower half of the curve order (`0 < s ≤ n/2`) before DER encoding. ECDSA is malleable: `(r, s)` and `(r, n−s)` are both mathematically valid; **Android Conscrypt** may reject the high-**s** form for `Signature.verify` even when the JVM accepts it. Emitting low-**s** improves cross-platform behaviour.
- **Mobile verification (Android)**: `IntegrationKeyVerifier` verifies with **BouncyCastle `ECDSASigner`** over SHA-256 of the UTF-8 payload, using **`PublicKeyFactory.createKey` on the raw decoded key bytes** (same as `SignatureService.validateSignature` on the Auth API). It must **not** rely on a JCA-only round-trip (`PublicKey.getEncoded()` after `KeyFactory`) for the primary path, because the JVM may **re-encode** SubjectPublicKeyInfo (e.g. named curve vs explicit parameters) and the resulting EC point encoding can differ from what BC used when signing. **JCA `SHA256withECDSA`** with the BouncyCastle provider and then the default provider are used as fallbacks.

## Respond (device signs)

**Payload to sign:** `{proofToken}|{accepted}`

- **proofToken**: The auth attempt proof token received in the Pending response.
- **accepted**: Literal string `"true"` or `"false"` (lowercase), matching the JSON field `authAttemptAccepted`.

**Example:** `abc123token|true`

The backend rebuilds this payload from the stored proof token and the request’s `authAttemptAccepted`, then verifies the device signature. If verification fails, the respond request is rejected.

## Respond HTTP response (integration signs)

After processing the respond request, the Auth API returns a JSON body that includes an **integration** signature so the mobile can detect MITM tampering on the result shown to the user.

**Payload to sign:** `{proofToken}|{authAttemptId}|{result}|{message}`

- **proofToken**: The same `authAttemptProofToken` from Pending (empty string `""` if the server could not associate a token, e.g. some error paths).
- **authAttemptId**: Decimal string of the attempt id (empty string if null).
- **result**: The authentication outcome enum name: `APPROVED`, `DENIED`, `FAILED`, or `EXPIRED` (same as JSON `authAttemptResult`).
- **message**: User-facing text; NFC-normalized; null becomes `""`.

**Example:** `abc123token|456|APPROVED|Auth attempt completed`

The backend signs this string with the enrollment’s integration private key. The JSON field `authAttemptProofTokenResultSignedByIntegration` carries the Base64 signature. Clients verify using the integration public key (same as for Pending). If verification fails, do not trust the displayed outcome.

## Implementation references

- **Java (backend, demo device)**: `org.ezkey.authattempt.service.AuthAttemptSignaturePayload` (ezkey-core). Use `Normalizer.normalize(s, Normalizer.Form.NFC)` for non-null context strings.
- **JavaScript/TypeScript (mobile)**: `string.normalize('NFC')` for context fields before building the payload.
- **Kotlin (mobile native)**: `java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)` when building payloads in native code; otherwise the JS layer builds the payload with NFC and passes it to native for sign/verify.
