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

## Respond (device signs)

**Payload to sign:** `{proofToken}|{accepted}`

- **proofToken**: The auth attempt proof token received in the Pending response.
- **accepted**: Literal string `"true"` or `"false"` (lowercase), matching the JSON field `authAttemptAccepted`.

**Example:** `abc123token|true`

The backend rebuilds this payload from the stored proof token and the request’s `authAttemptAccepted`, then verifies the device signature. If verification fails, the respond request is rejected.

## Implementation references

- **Java (backend, demo device)**: `org.ezkey.authattempt.service.AuthAttemptSignaturePayload` (ezkey-core). Use `Normalizer.normalize(s, Normalizer.Form.NFC)` for non-null context strings.
- **JavaScript/TypeScript (mobile)**: `string.normalize('NFC')` for context fields before building the payload.
- **Kotlin (mobile native)**: `java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)` when building payloads in native code; otherwise the JS layer builds the payload with NFC and passes it to native for sign/verify.
