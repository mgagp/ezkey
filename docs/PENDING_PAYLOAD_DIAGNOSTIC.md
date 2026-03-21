# Pending crypto diagnostics (Auth API ↔ mobile)

When debugging **integration signature verification** on the mobile pending flow, compare **INFO** lines from the Auth API with the **Debug (for support)** box on the device. All SHA-256 values are over **UTF-8** bytes of the **exact string** (lowercase hex, 64 characters).

## 1. Canonical payload

| Source | Field |
|--------|--------|
| Auth API | `PENDING_PAYLOAD_DIAG` → `payloadSha256Utf8Hex` |
| Mobile | `pendingPayload SHA256 (UTF-8 hex)` |

Same string as used for signing: `AuthAttemptSignaturePayload.buildPendingPayload` / `buildPendingPayload(...)`.

## 2. Integration signature (Base64 DER string)

| Source | Field |
|--------|--------|
| Auth API | `PENDING_SIGNATURE_DIAG` → `signatureSha256Utf8Hex` |
| Mobile | `integration signature SHA256 (UTF-8 hex)` |

The JSON field `authAttemptProofTokenSignedByIntegration` as received by the client (same characters as serialized in the response body).

## 3. Integration public key

| Source | Field |
|--------|--------|
| Auth API | `PENDING_INTEGRATION_PUBLIC_KEY_DIAG` → `integrationPublicKeySha256Utf8Hex` (normalized SPKI/cert from DB) |
| Mobile | `integrationPublicKey SHA256 (UTF-8 hex)` (string passed to `verify`) |

If the enrollment row stores the same material as the bind response, these should match. If they differ, storage or normalization differs between server verify and the device.

## Interpretation

| Pair | Equal | Not equal |
|------|--------|-----------|
| Payload hash | Payload OK | Fix payload construction / JSON |
| Signature hash | Same signature string over the wire | Truncation, wrong field, encoding of JSON |
| Public key hash | Same key string for verify | Wrong or corrupted key on device vs DB |

If **payload** and **signature** and **public key** hashes all match but **JCA self-verify passes on the server** and **verify fails on Android**, investigate **Conscrypt / ECDSA** interop (e.g. DER encoding, low-S) as a last resort.

## Showing the on-device debug box

The **Debug (for support)** panel on the Pending auth error screen is **hidden by default**. Set `EZKEY_PENDING_AUTH_DEBUG_PANEL=true` in `ezkey_mobile/.env` (see `.env.example`), then **rebuild** the native app (`react-native-config` injects at build time). Parsed in `app/config/env.ts` as `env.pendingAuthDebugPanel`.
