# Enrollment Uniqueness — 80/20 Quick Wins (Complement)

> **Status:** Exploration complement only. Not an ADR. Not accepted product direction.
> **Date:** 2026-09-22
> **Parent inventory:** [`ENROLLMENT_UNIQUENESS_SCOPE_EXPLORATION.md`](ENROLLMENT_UNIQUENESS_SCOPE_EXPLORATION.md)
>   (facts: constraints, tenant link, admin vs enrollment identity).
> **Living lifecycle canon:** [`docs/LIFECYCLE_GOVERNANCE.md`](../LIFECYCLE_GOVERNANCE.md) §3.3.
> **Intent:** Before any per-tenant uniqueness paradigm, list **targeted** status / constraint /
> validation / UX rearrangements that unlock legitimate re-enroll / reclaim cases with proportional
> depth. **Documentary only — no implementation in this PR.**

---

## 0. Why this complement exists

The parent note shows that Marc’s “global uniqueness” symptom is largely **not** missing
per-tenant indexes. For enrollment **names**, uniqueness is already **per integration + VERIFIED**.
The operable trap is narrower:

> **Deactivate keeps `enrollment_status = VERIFIED`**, so
> `idx_enrollment_unique_verified_name` still holds `(integration_id, enrollment_name)`.
> Create may succeed (`EnrollmentService` allows inactive VERIFIED); verify fails (app + DB).

This document asks: **what 80/20 changes unlock reclaim / re-enroll without a multi-tenant unique
paradigm?** Per-tenant uniqueness remains a later-phase option in the parent note (§2–4), not the
primary proposal here.

---

## 1. Operator pain → mechanism → why it hurts

| # | Operator intent / mental model | What they often do | Current mechanism | Why it hurts |
|---|-------------------------------|--------------------|-------------------|--------------|
| P1 | “Cancel / soft-disable this device, then enroll again with the **same name**.” | `POST …/enrollments/{id}/deactivate` then `POST /enrollments` + bind/verify | Deactivate: status stays **`VERIFIED`**, `active=false` (`EnrollmentRevocationService`). Partial unique `idx_enrollment_unique_verified_name` (V5) still applies. Create allows inactive VERIFIED; verify rejects any other VERIFIED same name (`EnrollmentVerifyService.validateUniqueness`). | Half-open door: invitation created, verify blocked. Feels like a uniqueness bug. |
| P2 | “Cancel” as a single enrollment word | Look for Cancel in UI / API | **No** `CANCELLED` / `DEACTIVATED` enrollment status. Enum: `CREATED`, `BOUND`, `VERIFIED`, `INVALID`, `REVOKED`, `EXPIRED` (`EnrollmentStatus`). HTTP cancel exists for **auth attempts** only. | Vocabulary mismatch → wrong lifecycle action chosen. |
| P3 | Replace lost/compromised end-user device | Deactivate (cautious) instead of revoke | LIFECYCLE §3.3: deactivate = investigate/reactivate; revoke = permanent + new enrollment. Admin UI AGENTS: non-admin device replacement is **revoke + create** (no dedicated rebind). | Cautious path preserves unique slot; replacement path is revoke (or admin reset), not deactivate. |
| P4 | Admin MFA device loss | Expect deactivate + re-create | Admin path is **recover + `POST …/enrollments/reset`** → status `CREATED`, new proof/challenge (`AdminRecoveryService`). End-user enrollments lack that reset API. | Operators copy admin mental model onto end-user enrollments. |
| P5 | Same Linux username / email in two tenants | Blame enrollment uniqueness | `user_identifier` / `contact_email`: **no unique constraint**. Admin `username` / `email`: **global** unique. | Wrong layer diagnosed; quick enrollment fixes won’t unlock multi-hat **admins**. |
| P6 | Multi-device same `userIdentifier` | — | Lookup returns a list; Integration API requires disambiguation | Unrelated to name unique trap; **conflicts** with later unique-`user_identifier` ideas. |

**App vs DB misalignment (hypothesis confirmed for names):**

| Layer | Create (same name, inactive VERIFIED exists) | Verify (second row → VERIFIED) |
|-------|-----------------------------------------------|--------------------------------|
| App | **Allows** | **Rejects** |
| DB (`idx_enrollment_unique_verified_name`) | N/A (new row is `CREATED`) | **Rejects** second `VERIFIED` |

So the trap is not “DB stricter than product intent” alone — **create is intentionally looser than
verify/DB**, which produces a false sense of progress. Loosening verify in app alone **cannot**
succeed without changing the partial unique index (or leaving VERIFIED).

**Soft-delete / tombstone nearby (reuse candidates):**

| Pattern | Where | Reuse for enrollment reclaim? |
|---------|-------|-------------------------------|
| Deactivate (`active=false`, status unchanged) | Enrollment, tenant, admin | Already the soft path — **holds** name unique |
| Revoke (terminal status) | Enrollment, API key | Frees name unique; irreversible |
| Reset → `CREATED` | Admin MFA only | Frees name unique; keeps same enrollment id |
| Auth-attempt supersession | Newer attempt supersedes older for same enrollment | Precedent for “replace in place” messaging — **not** copied to enrollments today (LIFECYCLE: no auth-attempt-style supersession for credentials) |
| `INVALID` / `EXPIRED` | Pending failure / invitation TTL | Free unique (not VERIFIED); not operator soft-disable |

---

## 2. Ranked 80/20 option set

Depth labels: **quick** (docs/UX or tiny validation copy), **medium** (enum and/or one Flyway
partial-index change + service align + tests), **paradigm** (per-tenant composites, denormalized
`tenant_id`, multi-device model change — parent note only).

Consequence width: **process** (operator SOP) → **API** (status/errors) → **UI** (labels, danger
zone, empty states).

### Option A — Docs + Admin UI wording (no schema)

| | |
|--|--|
| **Change surface** | `LIFECYCLE` cross-links already exist; Admin UI copy / tooltips / danger-zone help; optional ProblemDetail detail strings pointing to revoke vs reactivate |
| **Depth** | **Quick win** |
| **Unlocks** | Operators choose **revoke + create** (or reactivate) instead of deactivate-then-recreate; reduces P1/P2 confusion without behavior change |
| **Does not solve** | Soft-disable still holds unique slot; no per-tenant anything; admin global email/username |
| **Blast radius** | Auth attempts / PAM / multi-device: **none**. Integration API: none |
| **Consequence width** | Process + UI (and maybe API error copy). No new status |
| **Christophe?** | No |
| **Rank** | **1 — do first** |

### Option B — Align create with verify (reject create when any VERIFIED exists, including inactive)

| | |
|--|--|
| **Change surface** | App validation only (`EnrollmentService.create` + provisioning twin); tests |
| **Depth** | **Quick** (code) but **product-regressive** for the “create after deactivate” path that exists today |
| **Unlocks** | Honest fail-fast: no orphan CREATED rows that can never verify; clearer 409 at create |
| **Does not solve** | Still cannot re-enroll same name after deactivate without revoke/reset; merely stops the half-open door |
| **Blast radius** | Low technically; **worsens** P1 unless paired with A or C/D. PAM: none |
| **Consequence width** | API error earlier + UI must not promise “create after deactivate works” |
| **Rank** | **3 — only as companion** to A or to a slot-freeing option; **not** alone as the “fix” |

### Option C — Partial unique predicate includes `enrollment_active = true`

Replace / recreate index roughly as:

`UNIQUE (integration_id, enrollment_name) WHERE enrollment_status = 'VERIFIED' AND enrollment_active = true`

Align `EnrollmentVerifyService` (and create) with the same predicate.

| | |
|--|--|
| **Change surface** | Flyway partial unique + service uniqueness queries + uniqueness tests + LIFECYCLE §3.3 sentence |
| **Depth** | **Medium** |
| **Unlocks** | Deactivate → create → verify with **same name** (P1 reclaim after soft-disable) |
| **Does not solve** | Per-tenant user/email; admin global identity; “cancel” vocabulary; multi-device |
| **Blast radius** | **Reactivate collision:** old inactive VERIFIED + new active VERIFIED same name → reactivation of old would violate unique (must fail closed with clear 409). Two VERIFIED rows can coexist (one inactive). PAM lookup already filters `active=true` — OK. Device-key hash unique still global (unchanged). Auth history retained on old row — OK |
| **Consequence width** | Process (deactivate now means “name free for replacement”) → API → UI must explain reactivate-vs-replace |
| **Patrick craft risk** | Soft-disable that **releases** identity is a **semantic change** to deactivate (today: suspend credential, keep identity reserved). Clever predicate; easy to under-document |
| **Christophe?** | Only if product treats name as a trust/anti-hijack boundary beyond integration scope (unlikely) |
| **Rank** | **2b — candidate small implementation PR** after product accepts “deactivate frees name” |

### Option D — New terminal-ish status for soft replacement: `SUPERSEDED` (or `DEACTIVATED` leaving VERIFIED)

Operator action **“Replace / Re-enroll”** (or deactivate-with-intent) transitions old row off `VERIFIED`
(e.g. to `SUPERSEDED`) so existing partial unique frees the slot; then create new invitation.
Optional: keep plain **deactivate** as today’s suspend (slot held) for investigation.

| | |
|--|--|
| **Change surface** | `EnrollmentStatus` enum + Flyway check constraints if any + revocation service + OpenAPI/Bruno + Admin UI danger zone + LIFECYCLE state diagram |
| **Depth** | **Medium** (wider than C: new status across API/UI) |
| **Unlocks** | Explicit product transition matching “cancel then re-create”; preserves **true** deactivate (investigate/reactivate) vs **replace** (free name) |
| **Does not solve** | Per-tenant uniqueness; admin global email |
| **Blast radius** | Filters/lists by status; bulk revoke/deactivate; eligibility (`EntityEligibilityService`); reporting. PAM: superseded rows already non-VERIFIED or inactive — must define. Multi-device: unaffected |
| **Consequence width** | **Full** process → API → UI (new action + status badge) |
| **Patrick craft** | Prefer this over C if product wants **two** soft meanings (suspend vs release identity). Explicit status beats “active flag doubles as unique key” |
| **Auth-attempt precedent** | Supersession naming already exists for attempts — reuse vocabulary carefully so operators don’t confuse layers |
| **Rank** | **2a — preferred structural quick-win** if Marc wants one intentional transition rather than predicate cleverness |

### Option E — End-user “reset / rebind” API (mirror admin reset)

Same enrollment id: clear device key, status → `CREATED`, new proof/challenge (like
`AdminRecoveryService.resetEnrollment`). Name slot freed because status leaves `VERIFIED`.

| | |
|--|--|
| **Change surface** | New Admin API endpoint + authz + audit + UI + tests; decide device_public_key_hash clearing (parent §1.8 ambiguity) |
| **Depth** | **Medium** |
| **Unlocks** | Reclaim **same** enrollment row / history link without revoke; good for “same person, new phone, keep enrollment id” |
| **Does not solve** | Create-second-row flows; per-tenant; multi-hat admins |
| **Blast radius** | Integration API clients holding `enrollmentId` keep working after re-verify; PAM by `userIdentifier` OK. Must invalidate in-flight auth attempts. Trust: clearing device key/hash is a credential boundary — **flag Christophe** if hash/key handling is wrong |
| **Consequence width** | API + UI + process (when reset vs revoke) |
| **Rank** | **4 — valuable**, but different story (rebind same id) than “create another enrollment”; can pair with A |

### Option F — On deactivate, rename/suffix old `enrollment_name` (tombstone name)

e.g. append ` (deactivated #id)` so unique slot frees without status change.

| | |
|--|--|
| **Change surface** | Service on deactivate + UI display of historical name |
| **Depth** | Quick-medium, **high accidental complexity** |
| **Unlocks** | Same as C for create/verify |
| **Does not solve** | Clean identity semantics; reactivate name restore races; audits/search by original name |
| **Blast radius** | Surprising renames; Integration metadata; operator search |
| **Patrick craft** | **Reject** as primary — soft-delete that mutates the business key is the classic trap |
| **Rank** | **Park / discard** |

### Option G — Per-tenant unique indexes / paradigm

| | |
|--|--|
| **Change surface** | See parent note §§2–4 |
| **Depth** | **Paradigm** |
| **Unlocks** | Multi-hat end-user identity across tenants (if uniqueness were ever global — it isn’t for user/email today) |
| **Does not solve** | P1 deactivate trap by itself |
| **Rank** | **Later phase only** — not an 80/20 for today’s re-create pain |

---

## 3. Hypothesis check (non-binding)

| Hypothesis | Verdict |
|------------|---------|
| Deactivate holds `idx_enrollment_unique_verified_name` → create/verify trap | **Confirmed** (parent §1.5; LIFECYCLE §3.3; uniqueness tests) |
| Missing status (`DEACTIVATED` / `SUPERSEDED`) could free the slot | **Plausible** — Option D; not required if Option C changes the predicate or operators use revoke |
| Including `active=false` exclusion in partial unique frees slot | **Plausible** — Option C; changes deactivate semantics |
| App stricter/looser than DB | **Confirmed misalignment**: create looser than verify/DB for names; device-key app filters `VERIFIED` while DB unique is all non-null hashes (parent §1.8) |
| “Cancel” needs one intentional product transition | **Confirmed vocabulary gap**; Option A + D address it |
| Soft-delete / tombstone reuse | Deactivate is soft but **reserves** name; revoke/reset/supersede free it; auth-attempt supersession is a naming precedent only |

---

## 4. Recommendation (for Marc — do not implement in this PR)

### Ship later as small PRs (1–2)

1. **Option A (docs/UI wording)** — always worth it; zero schema risk; teaches revoke vs deactivate vs admin reset. Can land alone.
2. **Pick exactly one slot-freeing behavior** after product lock:
   - Prefer **Option D (`SUPERSEDED` / explicit Replace)** if Marc wants to **keep** investigation-deactivate (slot held) **and** offer a separate “re-enroll / replace device” path (Patrick-explicit).
   - Prefer **Option C (partial unique + `active=true`)** only if product accepts that **every** deactivate frees the name (simpler schema, blurrier meaning, reactivate conflicts).

Optional companion: **Option B** only together with A or C/D (fail-fast create), never alone.

### Park for now

- **Option E** (end-user reset) — strong for “same enrollment id” dogfood; separate story from uniqueness scope; revisit after A + D/C.
- **Option F** (rename on deactivate) — discard as primary.
- **Option G** (per-tenant paradigm) — parent note; after operable single-tenant / per-integration reclaim is solved.

### Patrick-readable craft verdict

> **Prefer an explicit lifecycle transition that leaves `VERIFIED` (revoke or `SUPERSEDED` /
> Replace) over a clever soft-unique on `active`.** Soft-disable that still holds a unique key is
> today’s trap; soft-disable that silently frees the key without a named transition is tomorrow’s
> trap (reactivate vs replace ambiguity). Docs/UI first; then one intentional API/UI action; defer
> per-tenant uniqueness until multi-hat identity is a real enrollment constraint (it is not, for
> `user_identifier` / `contact_email`, today).

### Christophe

Flag only if implementing **Option E** (device key/hash clearing) or if **Option C/D** is framed as
weakening device-key anti-hijack (they should not — leave `device_public_key_hash` global unique).

### Isabelle

Not required for choosing among A–D; existing `EnrollmentUniquenessIntegrationTest` already locks
P1. Add characterization tests in the future implementation PR.

---

## 5. Mapping to parent note

| Parent section | How this complement uses it |
|----------------|----------------------------|
| §1 inventory | Pain table cites same Flyway/service facts |
| §2 alternatives (soft-unique, release on deactivate, per-integration) | Expanded into ranked Options C/D/G |
| §4 phased plan | Phase 0–1 still apply; Options A/C/D are **prep before** per-tenant Phase 2+ |
| §5 go/no-go | Unchanged: no paradigm until Marc/Alex lock; this note adds **pre-paradigm** quick-win choice |

---

## Appendix — Citation anchors

| Claim | Anchor |
|-------|--------|
| Partial unique VERIFIED name | V5 `idx_enrollment_unique_verified_name`; LIFECYCLE §3.3 |
| Deactivate keeps VERIFIED | `EnrollmentRevocationService` Javadoc + `deactivate` |
| Create allows inactive VERIFIED | `EnrollmentService.create` |
| Verify rejects any VERIFIED | `EnrollmentVerifyService.validateUniqueness` |
| No CANCELLED status | `EnrollmentStatus` enum |
| Admin reset → CREATED | `AdminRecoveryService.resetEnrollment` |
| End-user replace = revoke + create | `ezkey-admin-ui/AGENTS.md` |
| user_identifier non-unique | V5 `idx_enrollment_integration_user_identifier` comment |
| Auth-attempt supersession | `AuthAttemptRespondService` / `AuthAttemptStatus.EXPIRED` docs |
