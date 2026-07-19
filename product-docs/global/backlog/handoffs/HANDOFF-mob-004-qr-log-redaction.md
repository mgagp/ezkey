# Handoff — MOB-004 QR and debug secret redaction

**Status:** implemented on `mobile` (awaiting commit/PR)  
**Finding:** MOB-004 (P2)  
**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

`useEnrollmentWizard.handleQrScanned` logs raw QR and parsed payload under `__DEV__`. Invalid QR
`console.warn` includes the raw value. Pending debug panel can show payload previews when
`EZKEY_PENDING_AUTH_DEBUG_PANEL` is enabled.

## In scope

1. Remove or redact `enrollmentProofToken` (and full QR JSON) from default `__DEV__` logs.
2. If raw dumps remain useful, gate behind an explicit env flag (separate from generic `__DEV__`).
3. Tighten pending debug panel redaction (hashes OK; full tokens / long previews not).
4. Extend Semgrep `mobile-security.yml` if a stable pattern can catch regressions.
5. Add/adjust unit tests where logging helpers are extractable.

## Out of scope

- Changing QR payload format
- Disabling all diagnostics permanently
- Production log shipping infrastructure

## Read first

- `ezkey_mobile/app/hooks/useEnrollmentWizard.ts`
- `ezkey_mobile/app/hooks/usePendingAuth.ts`
- `ezkey_mobile/app/config/env.ts`
- `ezkey_mobile/semgrep/rules/mobile-security.yml`
- `ezkey_mobile/docs/MOBILE_SECURITY_INVESTIGATION_TECHNIQUES.md` (if present)

## Validation

| Layer | Required? |
| --- | --- |
| Unit / Semgrep | **Yes** |
| Physical phone logcat | Optional (`verify-android-sensitive-storage.sh`) |

## Acceptance

- Scanning a QR in a debug build does not print the proof token to logcat by default.
- Debug panel does not display full proof tokens when enabled.

## Implementation notes (2026-07-19)

- Shared helper: `ezkey_mobile/app/utils/enrollmentSeedLogRedaction.ts` (camera + F2a `ingestSeedPayload`).
- Opt-in plaintext dumps: `EZKEY_ENROLLMENT_SEED_RAW_DUMP` (release preflight forbidden).
- Pending debug panel: hashes / lengths / short prefixes only (removed payload preview + base64).
- Semgrep: `ezkey-js-no-default-enrollment-seed-raw-console`.

## Suggested session opening message (copy-paste)

```
Implement MOB-004 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Redact QR/proof-token logging and tighten pending debug panel. Prefer an explicit flag over
__DEV__ raw dumps. Read product-docs/global/backlog/handoffs/HANDOFF-mob-004-qr-log-redaction.md
and ezkey_mobile/AGENTS.md.
```
