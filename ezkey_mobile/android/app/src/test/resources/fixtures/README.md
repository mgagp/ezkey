# Crypto test fixtures (golden vectors)

These files are **optional**. When present, `IntegrationKeyVerifierTest` runs **Ed25519** verification against the **exact** UTF-8 payload and Base64(Base64URL) signature the Auth API produced — regression coverage for the integration signing chain (Pending + Respond result).

## Required for optional golden tests

| File | Contents |
|------|----------|
| `integration_public_key_base64.txt` | One line: integration public key (**raw 32 bytes**, Base64URL without padding, as returned by bind). |

**Populate from DB** (Docker Postgres running):

```bash
./scripts/export-mobile-crypto-fixture.sh 3 --write
```

(Default enrollment id `3` = documented golden enrollment; adjust as needed.)

## Pending (integration signs the Pending HTTP response)

| File | Contents |
|------|----------|
| `pending_payload_utf8.txt` | Exact canonical string: `proofToken|challengeRequired|contextTitle|contextMessage` with `true`/`false`, Unicode **NFC** on title and message (must match `buildPendingPayload` / server). |
| `pending_signature_base64.txt` | Value of `authAttemptProofTokenSignedByIntegration` from the Pending **200** JSON (raw Ed25519, typically Base64URL). |

**How to capture:** After a successful functional Pending call, copy the payload string your client builds (or from Auth API debug / Crypto API validate `data` field in Postman step 4) and the signature field from JSON. One line per file, no extra quotes.

## Respond result (integration signs the Respond HTTP response)

| File | Contents |
|------|----------|
| `respond_result_payload_utf8.txt` | Exact string: `proofToken|authAttemptId|authAttemptResult|authAttemptMessage` with NFC on message (must match `buildRespondResultPayload` / server). |
| `respond_result_signature_base64.txt` | Value of `authAttemptProofTokenResultSignedByIntegration` from the Respond **200** JSON (raw Ed25519). |

**How to capture:** Same idea as Pending — Postman collection step **7** `data` body (`respondResultPayload`) and the signature from step **6** / **6b**, or export from logs after a real approve/deny.

## What runs in CI vs locally

- **Without** these files: golden tests exit early (pass) — same as `parsesFromFixtureFileWhenPresent` for the integration key alone.
- **With** all three files per flow: `IntegrationKeyVerifier.verify` must return **true**, or the test fails — catch regressions in parsing, encoding, or algorithm parity with the backend.

## TypeScript / Jest

`yarn test` exercises **payload string construction** in `app/services/crypto/__tests__/authAttemptPayload.test.ts` only. It does **not** run native ECDSA (Jest has no hardware/native verifier). **Contract** tests for HTTP shapes live in `app/services/api/__tests__/authAttempts.test.ts` (mocked Axios — placeholders, not real signatures).

For end-to-end cryptographic assurance, rely on **Android JVM** tests here + your functional runs.
