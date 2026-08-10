# Enrollment signature payload (canonical format)

This document defines the canonical payload format for integration-signed and device-signed enrollment messages. Backend (`ezkey-core`), demo device, and mobile apps must build and verify payloads identically.

See also [AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) for authentication
attempt Pending/Respond, and [SPKI_RECOVERY_SIGNATURE_PAYLOAD.md](SPKI_RECOVERY_SIGNATURE_PAYLOAD.md)
for trust-zone SPKI pin recovery (and the planned `spkiPinningMode` extension to enrolled
instance-info when that surface exists).

## Encoding

- **Character encoding**: UTF-8. The payload string is encoded as UTF-8 bytes for hashing/signing.
- **Text normalization**: Unicode NFC (Canonical Composition), per UAX #15, applied only to user-facing text fields called out below. Proof tokens, Base64 key material, numeric segments, and enum literals are **not** normalized.

## Bind response (integration signs)

**Payload to sign:**  
`{enrollmentProofToken}|{enrollmentId}|{integrationPublicKey}|{integrationKeyAlgorithm}|{integrationName}|{integrationDescription}|{enrollmentName}|{tenantId}|{tenantName}|{tenantDescription}`

- **Separator**: Single character `|` (U+007C). No spaces.
- **enrollmentProofToken**: Exact token string (unchanged).
- **enrollmentId**: Decimal string of the enrollment id (no padding).
- **integrationPublicKey**: The **same** normalized wire value returned in JSON (Base64URL without padding over raw 32 bytes for Ed25519), as produced by the server’s integration public key normalization.
- **integrationKeyAlgorithm**: Literal from JSON (e.g. `ed25519`).
- **integrationName**, **integrationDescription**, **enrollmentName**, **tenantName**, **tenantDescription**: NFC-normalized; null becomes `""`.
- **tenantId**: Decimal string of the tenant id, or `""` if null.

The mobile device receives `enrollmentBindPayloadSignedByIntegration` (Base64URL Ed25519 signature). It reconstructs the same UTF-8 string from the JSON fields, then verifies the signature with `integrationPublicKey` (same encoding rules as Pending).

## Verify request (device signs)

**Payload to sign:**  
`{enrollmentProofToken}|{enrollmentId}|{challengeResponse}|{devicePublicKey}`

- **enrollmentProofToken**: Same secret token as returned at bind (UTF-8 bytes; not re-sent in the verify JSON body; device uses the stored value when signing).
- **enrollmentId**: Decimal string.
- **challengeResponse**: Decimal string of the six-digit (or configured) challenge.
- **devicePublicKey**: Exact Base64 SPKI string as sent in JSON (standard Base64 for EC P-256 public key).

The backend verifies `enrollmentProofTokenSigned` with ECDSA-SHA256 over these UTF-8 bytes using the submitted device public key.

## Verify response (integration signs)

**Payload to sign:**  
`{enrollmentProofToken}|{enrollmentId}|{outcome}|{message}`

- **enrollmentProofToken**: Enrollment’s proof token.
- **enrollmentId**: Decimal string.
- **outcome**: For successful verification, the literal `VERIFIED` (same as enum `EnrollmentVerificationOutcome#name()`).
- **message**: User-facing confirmation text; NFC-normalized; null becomes `""`.

The JSON includes `enrollmentVerifyPayloadSignedByIntegration` (Ed25519) and `enrollmentVerifyMessage` (the message segment). Clients must verify the signature before treating enrollment as complete.

## Implementation references

- **Java**: `org.ezkey.enrollment.service.EnrollmentSignaturePayload` (`ezkey-core`).
- **TypeScript (mobile)**: `app/services/crypto/enrollmentPayload.ts` (`ezkey_mobile`).
- **Dart (experimental SDK)**: `ezkey_dart/lib/src/payload.dart` (`buildEnrollmentBindPayload`, `buildEnrollmentVerifyDevicePayload`, `buildEnrollmentVerifyResultPayload`).
