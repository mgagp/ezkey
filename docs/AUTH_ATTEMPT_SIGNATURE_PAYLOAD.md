# Auth attempt signature payload (canonical format)

This document defines the canonical payload format used when signing or verifying auth attempt Pending and Respond messages. Backend (ezkey-core), demo device (ezkey-demo-device), and mobile apps (ezkey_mobile, ezkey_mobile_app) must all build and verify payloads identically.

Enrollment bind and verify use a separate spec: [ENROLLMENT_SIGNATURE_PAYLOAD.md](ENROLLMENT_SIGNATURE_PAYLOAD.md).

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

### Ed25519 signature encoding (integration → clients)

- **Algorithm**: **Ed25519** (`Signature.getInstance("Ed25519")` on the JVM; same logical contract on mobile).
- **Public key (wire)**: **Raw 32 bytes** after Base64 decode. JSON typically uses **Base64URL without padding** (canonical on the wire). The server also accepts standard Base64 when decoding (`SignatureService.decodeFlexibleBase64ToBytes`).
- **Signature (wire)**: **Raw 64 bytes** after Base64 decode. JSON typically uses **Base64URL without padding**. Not DER / ASN.1.
- **Mobile verification (Android)**: `IntegrationKeyVerifier` decodes flexible Base64, checks lengths **32** / **64**, builds RFC 8410-style Ed25519 SPKI from the raw public key, and verifies with JCA **Ed25519**. **Conscrypt** is registered as a provider so behaviour is consistent across Android API levels (Bouncy Castle is not used).

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
