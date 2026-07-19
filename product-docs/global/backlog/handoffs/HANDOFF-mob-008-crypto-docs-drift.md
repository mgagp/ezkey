# Handoff — MOB-008 Crypto documentation path drift

**Status:** implemented — PR [#385](https://github.com/mgagp/ezkey/pull/385)  
**Finding:** MOB-008 (P3)  
**Assessment:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-16-pass-1.md`](../../hygiene/mobile-protocol-security/2026-07-16-pass-1.md)

---

## Context

Living docs still cite stale package paths (`com/ezkeymobile/...` vs `org.ezkey.mobile.crypto`), and
some historical audit plans describe remediated API gaps (e.g. respond rate limiting) as current
defects. Cold agents can “fix” the wrong thing.

## In scope

1. Update `docs/CRYPTO.md` implementation references to `org.ezkey.mobile.crypto` paths.
2. Update `docs/MOBILE_DEVELOPER_GUIDE.md` reference pointers if they still cite `com.ezkeymobile`.
3. Patch `ezkey_mobile/docs/NATIVE_MODULES.md` if package paths or deleted frame-processor claims remain.
4. Add a short “historical / partially stale” banner to
   `.github/prompts/plan-authProtocolSecurityAudit.prompt.md` pointing at current `RateLimitFilter`
   and the 2026-07 assessment — do not rewrite the whole plan.
5. Confirm May P1 investigation already has a superseded banner (done in assessment pass).

## Out of scope

- Re-auditing Auth API rate limits as new work
- Changing code behavior
- iOS docs parity campaign

## Read first

- `docs/CRYPTO.md`
- `docs/MOBILE_DEVELOPER_GUIDE.md` (Reference Implementation Pointers)
- `ezkey_mobile/docs/NATIVE_MODULES.md`
- `docs/security/mobile-protocol-crypto-assessment-2026-07.md` § MOB-008

## Validation

| Layer | Required? |
| --- | --- |
| Docs review / link check | **Yes** |
| Device / unit tests | No |

## Acceptance

- No living doc points agents at `com/ezkeymobile` for the current Android module.
- Historical audit prompt is clearly marked stale where applicable.

## Suggested session opening message (copy-paste)

```
Implement MOB-008 from docs/security/mobile-protocol-crypto-assessment-2026-07.md.

Fix stale Android crypto package paths in living docs and mark the historical auth protocol audit
prompt as partially stale. Docs-only; no behavior change. Read
product-docs/global/backlog/handoffs/HANDOFF-mob-008-crypto-docs-drift.md.
```
