# Audit Integrity APIs — Date Range Behaviour and Recommendations

## Context

The Admin UI exposes two verification actions under **Integrity & Lifecycle**:

1. **Entry Integrity** — `GET /api/v1/audit-logs/integrity-check?from=…&to=…`  
   Recomputes HMAC for each audit log entry in a date range.

2. **Chain Integrity** — `GET /api/v1/audit-logs/chain-integrity?from=…&to=…`  
   Recomputes checkpoint digests and verifies chain linkage for checkpoints in a date range.

Both use the same **DateRangeFilter** in the UI (presets + From/To). When the user leaves **"Full range"** (no preset, empty from/to), the UI sends **no query params**. Backend behaviour then differs, which led to a confusing experience (Chain Integrity reporting "Intact" with 0 checkpoints).

---

## Current Backend Behaviour (Verified in Code)

### Entry Integrity (`AuditIntegrityService.verifyRange(from, to)`)

- **Params:** `from`, `to` are optional (`@RequestParam(required = false)`).
- **Range spec:** `buildRangeSpec(from, to)`:
  - `from == null && to == null` → `cb.conjunction()` → **no filter** → **all audit log entries** are verified.
- **Result:** With no params, the API scans the entire audit log table (e.g. 51 entries in a test DB). For 4M rows this does not scale and is not a sensible default.

### Chain Integrity (`AuditChainVerificationService.verifyChain(from, to)`)

- **Params:** `from`, `to` optional.
- **Query:** `checkpointRepository.findByWindowRange(from, to)` with JPQL:
  `WHERE c.windowStart >= :from AND c.windowStart < :to`
- **When both null:** The JPQL predicate is evaluated with `null` bounds. In JPA, comparisons with `null` yield no rows, so the list of checkpoints is **empty**.
- **Result:** Report with `totalCheckpoints = 0`, `validCheckpoints = 0`, `invalidCheckpoints = 0`, `intact = true`, message `"No checkpoints found in range"`. So the UI shows "Intact" with Total 0, which is misleading when the user intended "full chain".

### UI

- **Integrity & Lifecycle** section uses `checkRange` state: `{ from: '', to: '' }` by default.
- Preset dropdown includes **"Full range"** (value `''`), which keeps `from` and `to` empty.
- Both buttons send `from: undefined, to: undefined` when no range is set (no query params).
- So the same visible choice ("Full range") produces:
  - **Entry Integrity:** verify all entries (works but does not scale).
  - **Chain Integrity:** 0 checkpoints → "Intact" (wrong mental model).

---

## Design Goals

- **Consistency:** Same semantics for "no range" (or no default) for both APIs, and UI that reflects that.
- **Scalability:** Avoid full-table scan of audit log entries by default (e.g. 4M rows).
- **Clarity:** No misleading "Intact" with 0 checkpoints; admin must understand what was verified.
- **UX:** Either a clear "select a range" flow or a documented, bounded default.

---

## Options

### Option A — Require explicit date range (recommended)

**Backend**

- Both endpoints treat **missing range** as invalid when both `from` and `to` are absent:
  - Return **400 Bad Request** with a clear message, e.g.  
    `"Date range is required for verification. Provide from (inclusive) and to (exclusive) as ISO-8601."`
- No implicit "full range" for either API.

**UI**

- In the **Integrity & Lifecycle** verification block only, treat "no range" as "no selection":
  - Change the preset label from **"Full range"** to **"Select a range"** (or use a dedicated placeholder for this section).
  - **Disable** "Chain Integrity" and "Entry Integrity" buttons when `!checkRange.from || !checkRange.to`, with a tooltip: e.g. "Select a date range to run verification."
- From/To inputs stay as-is (JJMMAAAA); once both are set (or a preset that sets both is chosen), buttons enable.

**Pros**

- Same rule for both APIs; no surprise cost; works for any dataset size.
- Clear intent: admin explicitly chooses what to verify.

**Cons**

- One extra step (select a range) before running a check.

---

### Option B — Bounded default (e.g. "last 7 days")

**Backend**

- When **both** `from` and `to` are null:
  - Define a default window, e.g. last 7 days:  
    `to = now()`, `from = now() - 7 days` (in server timezone or config).
  - **Entry Integrity:** apply this range (and optionally enforce a **cap**, e.g. stop after 50k entries with a message: "Verification capped at 50,000 entries; specify a narrower range for full coverage.").
  - **Chain Integrity:** use the same default range so both APIs behave identically for "no params".

**UI**

- Either:
  - Pre-fill the Integrity section with this default (e.g. "Last 7 days") so the first click works without selecting a range, or
  - Keep a single preset that means "use server default" and document it (e.g. "Default (last 7 days)").
- Label "Full range" only if we truly mean "all data" (not recommended for entry integrity at scale).

**Pros**

- One-click verification for common case (recent data).
- Consistent behaviour between the two APIs.

**Cons**

- "Full range" is a misnomer if the default is bounded; need to document and possibly cap entry count.

---

### Option C — Asymmetric: full chain, bounded entries

- **Chain Integrity:** when both null → verify **all** checkpoints (add repository method or pass a very wide range derived from min/max `window_start`). Checkpoint count is usually small.
- **Entry Integrity:** when both null → **require range** (400) or apply a **strict cap** (e.g. 10k entries) and return a message suggesting a date range for full coverage.

**Pros**

- "Full chain" is often cheap and matches the idea of "check the whole chain."

**Cons**

- Different semantics for the same "Full range" in the UI; two different defaults to document and maintain.

---

## Recommendation

**Prefer Option A (require explicit range)** for both APIs and align the UI accordingly:

1. **Backend:** For both `GET /audit-logs/integrity-check` and `GET /audit-logs/chain-integrity`, if both `from` and `to` are missing, return **400** with a clear message that a date range is required.
2. **UI (Integrity & Lifecycle only):**
   - Show **"Select a range"** (or equivalent) instead of "Full range" when the preset is empty for this section.
   - Disable the two verification buttons until a range is selected (both from and to set), with a short tooltip explaining that a date range is required.

This keeps semantics consistent, avoids full-table scans by default, and removes the misleading "Intact / 0 checkpoints" case. If later you want a one-click default, Option B can be added (e.g. "Last 7 days" as default range) without changing the "no params = 400" contract.

---

## Implementation Checklist (Option A) — Implemented

- [x] **ezkey-admin-api**  
  - In `AuditLogController.checkIntegrity`: if `from == null || to == null`, throw `IllegalArgumentException` (ValidationExceptionHandler returns 400 with message).
  - In `AuditLogController.checkChainIntegrity`: same rule.
- [x] **ezkey-core**  
  - No change; controller enforces range required.
- [x] **ezkey-admin-ui**  
  - `DateRangeFilter`: added optional prop `emptyOptionLabel` (default "Full range"). In Integrity & Lifecycle section, use `emptyOptionLabel="Select a range"`.
  - Disable "Chain Integrity" and "Entry Integrity" when `!checkRange.from || !checkRange.to`; native `title` tooltip when disabled.
- [x] **Docs**  
  - Updated `docs/AUDIT_LOG_INTEGRITY.md`: both verification endpoints require `from` and `to`; omitting returns 400.
- [x] **Tests**  
  - `AuditLogControllerChainCheckpointsTest`: added `checkIntegrity_withoutDateRange_throwsIllegalArgumentException` and `checkChainIntegrity_withoutDateRange_throwsIllegalArgumentException`.
  - `AuditIntegrityElectiveTest`: both integrity-check and chain-integrity calls now send `from` and `to` (wide range 2000–2030).

---

## Gap declaration and existing post-gap checkpoints (re-chain)

### Observed behaviour

When a gap is declared **after** the scheduler has already created checkpoints for the first windows following the gap (e.g. you deleted checkpoints 21–30, then the system ran and created checkpoint 31 for window 4h, then you declared the gap with anchor 20 and gapEnd 4h):

1. The declaration correctly creates a `GAP_DECLARATION` checkpoint (e.g. id 315) for [3h50, 4h) with `prev_chain_hmac` = anchor (20) and its own `chain_hmac`.
2. Chronological order becomes: 20 → 315 → 31 → 32 → …
3. Checkpoint 31 was created when the “latest” checkpoint in DB was still 20, so `31.prev_chain_hmac` = `20.chain_hmac`.
4. Chain verification walks by `window_start` ASC and expects each checkpoint’s `prev_chain_hmac` to match the **previous** checkpoint’s `chain_hmac`. For 31, it expects `31.prev_chain_hmac` = `315.chain_hmac`, but it is still `20.chain_hmac` → **violation** at window 2026-03-10T20:00Z: “Chain link broken (prev_chain_hmac does not match previous checkpoint’s chain_hmac)”.

### Root cause

Declare-gap only **inserts** the gap checkpoint. It does not update existing checkpoints that already exist **after** the gap (window_start ≥ gapEnd). Those were chained from the anchor (or earlier) when they were created; after inserting the gap, they must be **re-chained** so that the first such checkpoint links to the gap’s `chain_hmac`, and each subsequent one links to the updated previous `chain_hmac`.

### Fix (implemented)

**Repository** (`AuditChainCheckpointRepository`): added `findAllWithWindowStartAtOrAfter(OffsetDateTime from)` — returns checkpoints with `window_start >= from` ordered by `window_start` ASC. Used only by declare-gap to find checkpoints that need re-chaining.

**Lifecycle service** (`AuditLifecycleService.declareGap()`): after persisting the `GAP_DECLARATION` checkpoint:

1. Load all checkpoints with `window_start >= gapEnd` ordered by `window_start` ASC.
2. For each checkpoint in order: set `prev_chain_hmac` to the previous checkpoint’s `chain_hmac` (gap for the first, then the updated value), recompute `chain_hmac` = HMAC(entries_digest | prev_chain_hmac), save. This restores chain continuity without changing `entries_digest`.

So the change is **purely linkage**: we do not delete or insert any checkpoint except the single GAP_DECLARATION; we only update `prev_chain_hmac` and `chain_hmac` on checkpoints that already exist after the gap.

### Is the simulation realistic?

Yes. The test (manually delete checkpoints 21–30 in the DB, then run analysis → declare gap → verify chain) correctly simulates the **legitimate** recovery scenario:

- **Real situation**: Application is down for a period → no checkpoints for those windows. After restart, the scheduler creates checkpoints for new windows (31, 32, …) and chains them from the last existing checkpoint (20). The admin may take 30 minutes, an hour, or more to detect the gap and run the recovery procedure (declare-gap). So at declare-gap time, checkpoints **after** the gap already exist and are still chained to the anchor. That is exactly the situation your test produced by deleting 21–30 and letting the system create 31+.
- The fix makes this legitimate flow work: declare-gap inserts the gap checkpoint and re-chains the existing post-gap checkpoints so the chain verifies as intact.

### Security considerations (threat model)

**Question:** Does the re-chain logic allow a malicious actor to delete checkpoints in the DB and then "fix" the chain via declare-gap, effectively erasing traces?

**Analysis:**

1. **Who can do what**
   - **Admin with Admin UI only (recommended / SOC 2 practice):** Can call declare-gap. **Cannot** delete checkpoints or audit log rows. So they **cannot** create a "fake" gap by deleting data. The re-chain does not give them any new capability. The only way they see a gap is after **real** downtime (no checkpoints created during that period).
   - **Actor with direct DB access:** Can delete checkpoints (or audit entries). They could already create a gap and call declare-gap; before the fix the chain **failed** at the first post-gap checkpoint (visible break); after the fix the chain **passes** after re-chain. So with DB access, an attacker can delete evidence and then use declare-gap to get a consistent chain.

2. **What the re-chain does and does not do**
   - It does **not** grant anyone the ability to delete data. Deletion is a **separate** capability (DB access).
   - It only **repairs linkage** for checkpoints that already exist after the gap. So it closes the gap between "legitimate recovery when post-gap checkpoints already exist" and "chain verification passes."
   - A declare-gap call is **always** recorded: meta-audit entry `AUDIT_CHAIN_GAP_DECLARED` (with gap period, justification, gap checkpoint id) and a `GAP_DECLARATION` checkpoint in the chain. So even if someone with DB access deletes checkpoints and then declares a gap, there is a durable record that a gap was declared (who, when, justification). To hide that, they would have to also alter or delete audit log entries — i.e. full DB compromise, which is out of scope for "normal" chain design.

3. **Conclusion**
   - The fix addresses a **real operational scenario** (recovery after downtime when the scheduler has already created post-gap checkpoints) and does not introduce a new **privilege**: the re-chain only runs as part of the existing declare-gap API, which already required the ability to insert a gap checkpoint.
   - Under **recommended** separation of duties (admin has Admin UI, not direct DB access), the simulation is realistic for recovery, and the "malicious actor with DB access" scenario is a different, higher-privilege threat. The design does not rely on the chain alone to protect against DB-level tampering; it relies on access control (no direct DB for day-to-day admin) and audit trail (declare-gap is logged and visible).

---

## References

- Entry Integrity: `AuditIntegrityService.verifyRange`, `buildRangeSpec` (conjunction when null/null).
- Chain Integrity: `AuditChainVerificationService.verifyChain`, `AuditChainCheckpointRepository.findByWindowRange`.
- Controller: `AuditLogController.checkIntegrity`, `AuditLogController.checkChainIntegrity`.
- UI: `audit-logs.tsx` (runChainCheck, runIntegrityCheck, checkRange, DateRangeFilter).
- Presets: `date-range-presets.ts` (`DATE_RANGE_PRESET_OPTIONS`: "Full range" value `''`).
