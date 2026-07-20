# Handoff — MOB-014 malformed pending HTTP 200 must fail closed

**Status:** `open` — **fix authorized**  
**Lane:** Mobile protocol security hygiene (pass-2)  
**Finding:** MOB-014 (P2, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-014

Use this prompt to start a **new Cursor session** that **implements** MOB-014.  
This item does **not** require MOB-011 / MOB-012 / MOB-013 to land first (no Keystore identity dependency). Prefer a dedicated small PR; do not bundle with 011–013 unless the operator asks.

---

## Operator decisions (already made)

1. **Disposition:** **fix** (hygiene).
2. HITL briefing accepted 2026-07-19: malformed pending HTTP 200 must not be treated like 204 “no pending.”

---

## One-sentence problem

`authAttemptsApi.pending` maps a non-204 response whose body fails `isUsablePendingResponse` to `undefined` — the same outcome as legitimate **204 No Content** — so callers show “no pending” instead of a fail-closed contract / integrity failure.

---

## Scenario that led to the observation (preserve this narrative)

Not a remote approve forge. White-box read of the pending client wrapper and shared `claimPendingAttempt` during Pass-2.

1. A real pending attempt exists on the Auth API.
2. A hostile or buggy hop returns **HTTP 200** with JSON missing / empty `authAttemptProofToken` or `authAttemptProofTokenSignedByIntegration` (or non-object body).
3. `isUsablePendingResponse` → false → wrapper returns `undefined`.
4. `claimPendingAttempt` treats `!response` as `{kind: 'none'}` → UI: no pending, no integrity alert.

Ed25519 verify already fail-closes when fields are present but the signature is wrong. Malformed 200 never reaches that path.

**Observation label:** *Empty (204) and malformed (200) are conflated; signature fail-closed is short-circuited upstream.*

**Non-claim:** does not forge an approve without the device key. Impact is denial-of-visibility / fail-open UX under hostile TLS trust (see deferred MOB-005) or contract bugs.

---

## Intended fix shape

### Behavioral contract

| HTTP outcome | Result |
| --- | --- |
| **204** | No pending (`undefined` / `{kind: 'none'}`) |
| **200** + usable body | Return data; claim path verifies Ed25519 |
| **200** + unusable / malformed body | **Fail closed** — do not return `undefined` |
| Other unexpected status | Fail closed or throw consistently with existing HTTP error handling |

### Suggested implementation sketch

1. `ezkey_mobile/app/services/api/authAttempts.ts` — `pending`:
   - Keep 204 → `undefined`.
   - If status indicates success with body but `!isUsablePendingResponse(response.data)` → throw a dedicated error **or** return a discriminated failure the claim layer understands. Prefer one clear pattern end-to-end.
2. `claimPendingAttempt.ts`:
   - If using throw: ensure callers map to `fail_closed` with a reason such as `malformed_pending_response` (do not show as “none”).
   - If using discriminated API result: extend types so “none” and “malformed” cannot be confused.
3. UX: reuse existing fail-closed pending messaging patterns where possible; avoid implying “nothing to approve.”
4. Do not weaken `isUsablePendingResponse` field checks — they are correct; only the **mapping** of failed checks changes.

### Tests (minimum)

- Unit `authAttempts.test.ts`: 204 → undefined; 200 usable → data; **200 malformed → not undefined** (throws or fail signal).
- Unit `claimPendingAttempt.test.ts`: malformed path → `fail_closed` (or equivalent), never `none`.
- Cover empty signature string / missing fields / non-object body if cheap.

---

## Evidence map (read these first)

- `ezkey_mobile/app/services/api/authAttempts.ts` — `isUsablePendingResponse`, `pending` 204 / fallback `undefined`
- `ezkey_mobile/app/services/pendingAuth/claimPendingAttempt.ts` — `if (!response) return {kind: 'none'}`
- `ezkey_mobile/app/services/api/__tests__/authAttempts.test.ts`
- `ezkey_mobile/app/services/pendingAuth/__tests__/claimPendingAttempt.test.ts`
- Assessment §14.2 MOB-014

---

## Product / methodology constraints

- Lane D hygiene; small PR.
- No `I-*` / `TB-*`.
- No Auth API / OpenAPI change required (client-side contract honesty only). Unless analysis shows the generated client already collapses statuses — verify, do not hand-edit OpenAPI.
- Out of scope: certificate pinning (MOB-005), MOB-011–013, MOB-015/016.
- Update campaign note + assessment disposition when PR lands; delete this handoff on closeout.

---

## Suggested first agent turns

1. Read this handoff + `authAttempts.ts` + `claimPendingAttempt.ts` + existing tests.
2. Implement discriminate 204 vs malformed 200; wire fail_closed.
3. Add/adjust unit tests; run targeted mobile unit tests for these files.
4. Do not commit until the operator asks.

---

## Out of scope

- Pinning / MITM lab
- Changing Auth API pending response schema
- Keystore / enrollment identity work
- Respond-path malformed handling unless the same helper is trivially shared and operator expands scope

---

## Paste-ready starter message (for the implementing agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-014-malformed-pending-fail-closed.md end-to-end.

Task: implement MOB-014 only — HTTP 204 remains “no pending”; HTTP 200 with a body that fails isUsablePendingResponse must fail closed (not undefined / not kind none). Wire claimPendingAttempt accordingly. Add unit tests. Do not change OpenAPI by hand. Do not bundle MOB-011–013 or 015–016. Do not create I-*/TB-*. Do not commit until I ask.
```
