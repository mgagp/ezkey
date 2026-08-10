# SPKI recovery signature payload (canonical format)

This document defines the canonical payload formats for Auth API **SPKI pin recovery**
(propose offer + accept). Backend (`ezkey-core`), Demo Device, and Ezkey Mobile must build and
verify payloads identically.

**Design pack:**
[`product-docs/global/mobile-certificate-pinning-design-pack.md`](../product-docs/global/mobile-certificate-pinning-design-pack.md)

See also [ENROLLMENT_SIGNATURE_PAYLOAD.md](ENROLLMENT_SIGNATURE_PAYLOAD.md) (encoding rules and
enrolled instance-info) and [AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md](AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).

## Encoding

Same rules as enrollment payloads:

- **Character encoding:** UTF-8.
- **Separator:** single `|` (U+007C), no spaces.
- **Text normalization:** Unicode NFC only on fields explicitly marked; proof tokens, hashes,
  enums, and ids are **not** NFC-normalized.
- **Signatures:** Integration Ed25519 over UTF-8 bytes of the canonical string; Base64URL without
  padding on the wire unless an existing Auth API field already uses a documented variant — TB
  must match `EnrollmentSignaturePayload` conventions. Device accept uses ECDSA-SHA256 over UTF-8
  bytes with the enrollment device public key (same family as enrollment verify / auth respond).
- **`integrationKeyAlgorithm`:** fail-closed when missing or unsupported (same as other Auth API
  signed responses).

## SPKI hash encoding

`proposedSpkiSha256` and `observedSpkiSha256` segments use **lowercase hex** of the raw 32-byte
SHA-256 digest of the DER-encoded `SubjectPublicKeyInfo` (64 hex characters). No `sha256/` prefix
in the canonical string (OkHttp pin prefix may be used only in native pin-store adapters, not in
these payloads).

## Recovery offer response (integration signs)

Returned by `POST /api/v1/trust/spki-recovery` (path per design pack).

**Payload to sign:**

`{enrollmentProofToken}|{enrollmentId}|SPKI_RECOVERY_OFFER|{recoveryId}|{proposedSpkiSha256}|{corroborationStatus}|{rebindAllowed}|{expiresAt}`

| Segment | Rules |
|---------|--------|
| `enrollmentProofToken` | Exact enrollment proof token string |
| `enrollmentId` | Decimal string, no padding |
| `SPKI_RECOVERY_OFFER` | Literal purpose tag |
| `recoveryId` | Opaque server id string (UUID text is fine) |
| `proposedSpkiSha256` | Lowercase hex SPKI hash from the mobile proposal |
| `corroborationStatus` | Literal `MATCH`, `MISMATCH`, or `UNAVAILABLE` |
| `rebindAllowed` | Literal `true` or `false` (`true` only when status is `MATCH`) |
| `expiresAt` | ISO-8601 UTC instant with `Z` (e.g. `2026-08-10T20:15:30.000Z`) — exact wire form must match JSON field used to reconstruct |

JSON includes the unsigned field values plus
`spkiRecoveryOfferPayloadSignedByIntegration` (Base64URL Ed25519). Mobile reconstructs the string
from JSON and verifies with the stored integration public key **before** acting on
`rebindAllowed`.

When `rebindAllowed` is `false`, mobile enters blocked/retry UX and must **not** call accept to
commit a new pin.

## Recovery accept request (device signs)

Sent by `POST /api/v1/trust/spki-recovery/accept`.

**Payload to sign:**

`{enrollmentProofToken}|{enrollmentId}|SPKI_RECOVERY_ACCEPT|{recoveryId}|{proposedSpkiSha256}`

| Segment | Rules |
|---------|--------|
| `enrollmentProofToken` | Exact stored proof token |
| `enrollmentId` | Decimal string |
| `SPKI_RECOVERY_ACCEPT` | Literal purpose tag |
| `recoveryId` | Same id as the offer |
| `proposedSpkiSha256` | Same lowercase hex as the offer |

JSON includes `spkiRecoveryAcceptPayloadSignedByDevice` (or the TB-final field name) verified by
the Auth API with the enrollment’s device public key.

Server must reject accept unless the offer was `MATCH`, unexpired, and corroboration still
allows rebind.

## Recovery accept response (integration signs)

**Payload to sign:**

`{enrollmentProofToken}|{enrollmentId}|SPKI_RECOVERY_ACCEPT_RESULT|{recoveryId}|{proposedSpkiSha256}|{outcome}`

| Segment | Rules |
|---------|--------|
| `outcome` | Literal `ACCEPTED` on success; other literals only if TB defines explicit failure bodies that are still signed |

Mobile must verify this signature before replacing the stored installation SPKI pin.

## Instance-info mode field (extension)

When pinning mode is advertised on enrolled instance-info, extend the existing
`INSTANCE_INFO` canonical string from [ENROLLMENT_SIGNATURE_PAYLOAD.md](ENROLLMENT_SIGNATURE_PAYLOAD.md)
by appending:

`|{spkiPinningMode}`

where `spkiPinningMode` is the literal `ENFORCED` or `DISABLED`.

Update Java `EnrollmentSignaturePayload#buildInstanceInfoPayload` and mobile
`enrollmentPayload.ts` in the same TB that adds the JSON field. Public unsigned GET may expose
the same field as a **hint only**; it is not signed and must not override a verified signed value.

## Implementation references (to be added with TB)

- **Java:** extend `org.ezkey.enrollment.service.EnrollmentSignaturePayload` (or a sibling builder
  under trust/recovery).
- **TypeScript (mobile):** `ezkey_mobile` crypto payload helpers alongside enrollment payloads.
- **Design pack:** normative product/protocol context for these strings.
