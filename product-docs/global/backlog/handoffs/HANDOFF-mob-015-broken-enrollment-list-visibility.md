# Handoff — MOB-015 enrollment list must not collapse broken secrets to empty

**Status:** `open` — **fix authorized**; **mini Grill Me + UI design required before coding UI**  
**Lane:** Mobile protocol security hygiene (pass-2)  
**Finding:** MOB-015 (P2, Confirmed)  
**Campaign:** [`product-docs/global/hygiene/mobile-protocol-security/2026-07-19-pass-2.md`](../../hygiene/mobile-protocol-security/2026-07-19-pass-2.md)  
**Assessment register:** [`docs/security/mobile-protocol-crypto-assessment-2026-07.md`](../../../../docs/security/mobile-protocol-crypto-assessment-2026-07.md) §14.2 MOB-015

Use this prompt to start a **new Cursor session** that:

1. Runs a **mini Grill Me + UI design** (section below) with the operator to lock display/recovery behavior,
2. Then **implements** the storage + UI fix.

Do not invent final copy or Home layout before that Grill Me cluster closes. Storage-layer discrimination (broken vs missing vs parse failure) can be prototyped in parallel, but **UI shapes and user actions wait for operator answers**.

Soft preference: land after MOB-012 if practical (fewer seal-key loss false empties), but MOB-015 is still valid without 012. Do not bundle MOB-011/013/014/016 unless asked.

---

## Operator decisions (already made)

1. **Disposition:** **fix** (hygiene).
2. HITL briefing accepted 2026-07-19: secret unseal / missing secure material / broad parse catch must not present as a healthy empty enrollment list.
3. **UI details are not pre-authorized** — complete the Grill Me / design section in this handoff with the operator first.

---

## One-sentence problem

`listEnrollments` can return `[]` on parse/unseal failure, and silently omit rows when proof token or integration public key rehydration fails — so crypto/storage breakage looks like “no enrollments,” which undermines wipe honesty and recovery.

---

## Scenario that led to the observation (preserve this narrative)

Not a remote bypass. White-box read of `enrollmentStorage.listEnrollments` / `attachProofToken` / `attachIntegrationPublicKey` / Android seal path during Pass-2, cross-linked with MOB-012 seal-key loss.

Two mechanisms, same bad UX:

**A — Broad catch → `[]`**  
JSON parse failure or thrown unseal inside the `try` → `console.warn` + return empty array. Metadata may still exist on disk.

**B — Missing secret → row dropped**  
`attachProofToken` / `attachIntegrationPublicKey` return `undefined` (DEV warn only) → filtered out. Production Home looks like the enrollment never existed.

Related lifecycle honesty: `clearAll` does not delete app seal key `ezkey_app_seal_v1` (optional sub-decision in Grill Me).

**Observation label:** *Storage/crypto failure presented as absence of enrollment.*

**Non-claim:** does not forge MFA decisions. Impact is local resilience, operator diagnosis, and recovery clarity.

---

## Evidence map (read these first)

- `ezkey_mobile/app/services/storage/enrollmentStorage.ts` — `listEnrollments` catch → `[]`; attach* return `undefined`; `clearAll`
- `ezkey_mobile/app/services/storage/secureStorage.ts` — seal/unseal `getItem`
- `ezkey_mobile/app/hooks/useEnrollments.ts` — list query feeding Home
- `ezkey_mobile/app/screens/Home/HomeScreen.tsx` — empty / grouped list UX today
- `ezkey_mobile/app/i18n/resources.ts` — home / enrollment strings
- Assessment §14.2 MOB-015; coupling MOB-012 (seal loss), MOB-011 (key overwrite)

---

## Mini Grill Me + UI design (mandatory before UI implementation)

Skill: `.cursor/skills/grill-me/SKILL.md`. Keep questions concrete. **Iterate one cluster at a time**; wait for operator answers before proposing a locked UI mock in prose.

### Cluster 1 — Failure taxonomy (what the app must distinguish)

1. Whole-collection parse failure (corrupt `ezkey-mobile/enrollments` JSON) vs per-enrollment secret failure vs thrown unseal (seal key dead / bad envelope)?
2. Should “metadata present, secret missing” and “unseal threw” share one UX state or two?
3. If some enrollments hydrate OK and others fail, is partial list + broken rows required (vs fail-all)?

### Cluster 2 — Where and how to show it (Home and beyond)

4. Home only, or also Detail / Pending entry points when the selected id is broken?
5. Visual treatment: inline row in the installation group, banner above the list, blocking full-screen, or Danger Zone only?
6. How much technical detail in production vs `__DEV__` (alias id, “unseal failed”, “proof token missing”)?
7. Empty-state copy today means “no enrollments” — how do we prevent collision with “enrollments exist but all broken”?

### Cluster 3 — User actions (recovery)

8. Primary CTA: “Re-enroll”, “Remove broken enrollment”, “Clear all local data”, open Detail, or contact admin — which are in-scope for this hygiene fix?
9. Is one-tap delete of a broken row (metadata + best-effort Keystore + sealed keys) required in v1?
10. Should Danger Zone `clearAll` also wipe `ezkey_app_seal_v1`? (yes / no / later)

### Cluster 4 — Fail posture and product tone

11. Fail-closed for crypto use (cannot pending/respond) is already implied if secrets missing — should the list still show the broken enrollment (fail-open visibility) while blocking auth actions?
12. Any copy that must avoid sounding like a security breach vs honest “this device enrollment data is unreadable”?

### Design lock deliverable (after Grill Me)

Write a short **UI contract** in the campaign note (or append to this handoff) before coding UI:

| Situation | Surface | Message intent (EN + FR keys) | Actions | Non-actions |
| --- | --- | --- | --- | --- |
| … | … | … | … | … |

Only then implement screens/i18n against that table.

---

## Intended fix shape (storage — can start after Cluster 1)

### Behavioral contract (storage)

| Outcome | Must not do | Should do |
| --- | --- | --- |
| Corrupt collection JSON | Return `[]` silently as healthy empty | Surface collection-level error to UI layer |
| Per-row missing proof / integration key | Drop row without trace in prod | Return a **broken enrollment** descriptor (id + reason) or parallel list |
| Unseal throw | Collapse entire list to `[]` | Isolate per-row if possible; else collection-level broken state |
| Healthy rows mixed with broken | Hide broken | Return both; Home renders both per UI contract |

### Suggested implementation sketch (adjust after Grill Me)

1. Narrow `listEnrollments` catch — do not treat all failures as empty success.
2. Introduce an explicit result type, e.g. `{ enrollments, broken[], collectionError? }` **or** `StoredEnrollment | BrokenEnrollment` union — pick one after Cluster 1.
3. Thread through `useEnrollments` → Home (and Detail if Cluster 2 says so).
4. i18n EN/FR per locked UI contract.
5. Unit tests: corrupt JSON; missing proof token; missing integration key; mocked unseal throw; mixed healthy+broken.
6. Optional in same PR only if Grill Me says yes: `clearAll` deletes app seal key (native API may be needed).

### Tests (minimum)

- `enrollmentStorage` unit coverage for the failure modes above.
- Hook/UI test only as deep as the locked contract requires (avoid huge Home snapshot churn).

---

## Product / methodology constraints

- Lane D hygiene; no `I-*` / `TB-*` unless Grill Me reveals a product redesign larger than a punctual fix.
- No Auth API / OpenAPI changes.
- Do not implement MOB-016 orphan cleanup here unless operator expands scope.
- Preserve MOB-002 fail-open Keystore delete on wipe.
- Update campaign note with Grill Me UI contract + disposition; delete this handoff on PR closeout.

---

## Suggested first agent turns

1. Read this handoff + `enrollmentStorage.ts` + Home empty-state + i18n home strings.
2. Run Grill Me **Cluster 1** only; wait.
3. Continue clusters 2→4; write the UI contract table.
4. Implement storage discrimination + UI per contract; add tests.
5. Do not commit until the operator asks.

---

## Out of scope (unless Grill Me expands)

- MOB-011 identity migration / MOB-012 gate / MOB-013 require-key / MOB-014 pending malformed
- Full onboarding redesign
- Server-side recovery of proof tokens (impossible by design — re-enroll only)
- iOS-specific storage beyond shared TS behavior

---

## Paste-ready starter message (for the other agent)

```text
Read product-docs/global/backlog/handoffs/HANDOFF-mob-015-broken-enrollment-list-visibility.md end-to-end.

Task: MOB-015 fix is authorized, but first run mini Grill Me + UI design from the handoff (Clusters 1–4, one cluster at a time). Lock an UI contract table for broken/missing/unseal/parse-failure cases before implementing Home (or other) UI. Then implement storage discrimination so broken state is not collapsed to a healthy empty list; add unit tests. Do not invent final copy before Grill Me answers. Do not bundle 011–014 or 016. Do not create I-*/TB-* unless Grill Me proves we need a program. Do not commit until I ask.

Start with Grill Me Cluster 1 only and wait for my answers.
```
