# Integrity cut-3 — exploratory QA seed (lab only)

**Audience:** Isabelle / Marc / operators validating Admin UI Integrity cut-3 on a **warm local Docker stack**  
**PR context:** Alerts signal-only + Integrity deep-link reconcile; conditional **Confirm archived**  
**Non-goals:** no second journal, no Integrity API mocks, no product redesign, no permanent compose default change

Clean-start does **not** leave an `AUDIT_INTEGRITY_RUPTURE` or a sealed tranche with `confirmationRequired=true`. Use the lab helpers below, then the Admin UI steps.

Related canon: [`docs/AUDIT_LOG_INTEGRITY.md`](../AUDIT_LOG_INTEGRITY.md) (HMAC + chain + lifecycle endpoints).

---

## Operator order (read this first)

> **Prefer path (A) before path (B) on one warm stack.**  
> Or use **separate stacks**, or run **full cleanup between A and B**.  
> **Do not** seal (B) and then re-Resolve / reconcile an OPEN seed rupture (A) whose validation window still contains an `ARCHIVE_SEAL` checkpoint — the API correctly rejects with `checkpoint … not REGULAR (type=ARCHIVE_SEAL)`. That is expected product behavior, not a UI bug.

Suggested single-stack sequence:

1. **(A)** tamper → Run validation → Alerts deep-link → **finish Reconcile**  
2. **`./scripts/lab/cleanup-integrity-cut3-qa.sh`** (default = full lab reset) **or** leave seals absent  
3. **(B)** `--enable-archival` → Seal Archive (empty window) → Confirm archived  

Between re-runs: always prefer full cleanup so OPEN seed alerts and `ARCHIVE_SEAL` / `EXPORTED` rows are not silent landmines.

---

## Prerequisites

1. Warm stack (e.g. `ezkey-tests/clean-start.sh` already completed; Admin API healthy).
2. Global Admin session (`admin.docker` + Demo Device) in Admin UI.
3. From repo root, Bash available.

**Do not** use `scripts/lab/seed-alerts-ui-review.sh` for reconcile QA. That inserts a **fake** rupture payload for Alerts list polish and also seeds an OPEN `AUDIT_CHAIN_HEARTBEAT_STALE`, which **blocks** real reconcile. Full cut-3 cleanup also removes those rows; or run:

```bash
./scripts/lab/cleanup-alerts-ui-review.sh
```

---

## One-shot lab seed

```bash
# (A) Induce a real per-entry HMAC mismatch (SQL only)
./scripts/lab/seed-integrity-cut3-qa.sh

# (B) Flip Admin API external-archival-enabled=true (brief admin-api recreate)
# Prefer AFTER path A is finished (or after full cleanup / on another stack)
./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival

# Archival flag only
./scripts/lab/seed-integrity-cut3-qa.sh --archival-only
```

Cleanup (default = **full** reset for re-runs):

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh
# untamper + OPEN lab alerts + ARCHIVE_SEAL/EXPORTED → REGULAR/ACTIVE
# + drop lab external-archival compose override

./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-evidence   # leave tamper/alerts/seals/override
./scripts/lab/cleanup-integrity-cut3-qa.sh --alerts-seals-only
./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-seals
./scripts/lab/cleanup-integrity-cut3-qa.sh --keep-alerts
```

Lab files:

| File | Role |
|------|------|
| `scripts/lab/seed-integrity-cut3-qa.sh` | Entry point |
| `scripts/lab/seed-integrity-rupture-lab.sql` | Mutate one signed `reason`, leave `entry_hmac` |
| `scripts/lab/docker-compose.lab-external-archival.yml` | Override only; not a product default |
| `scripts/lab/cleanup-integrity-cut3-qa.sh` | Full lab reset (default) |
| `scripts/lab/cleanup-integrity-cut3-alerts.sql` | OPEN rupture / heartbeat / polish-seed alerts |
| `scripts/lab/cleanup-integrity-cut3-seals.sql` | Reset ARCHIVE_SEAL / SEALED / EXPORTED |

---

## Path (A) — Real rupture → Alerts deep-link → Reconcile

1. Run `./scripts/lab/seed-integrity-cut3-qa.sh` and note `audit_log_id` / `created_at` printed.
2. Admin UI → **Integrity**.
3. Set the date range so it **covers** that `created_at` (UI default may already).
4. Click **Run validation and raise alert if needed** (detective path; may raise/touch alert).
5. Open **Alerts** → filter Open → open the **CRITICAL** `AUDIT_INTEGRITY_RUPTURE`.
6. Use the Integrity deep-link (cut-3 contract: `/integrity?action=reconcile&alertId=<id>`).
7. On Integrity, open **Reconcile**, acknowledge the live entry violation id(s), enter justification + category, submit.
8. Expect alert resolved and Integrity lifecycle/meta-event path exercised — **not** a mocked UI-only alert.

Notes:

- Reconcile rejects when an OPEN `AUDIT_CHAIN_HEARTBEAT_STALE` exists — clear via full cleanup (or `cleanup-alerts-ui-review.sh`).
- Reconcile rejects when any checkpoint in the rupture window is not `REGULAR` (e.g. leftover `ARCHIVE_SEAL` from path B). **Do not re-Resolve that seed alert after B** without cleanup / a window free of seals — use cleanup or finish A before sealing.
- Boundaries come from the **alert payload** loaded by `alertId` on Integrity (no fabricated second journal).

---

## Path (B) — Sealed tranche → Confirm archived

Default Docker keeps `ezkey.audit.archive.external-archival-enabled=false`, so `confirmationRequired` stays false even after sealing.

1. Prefer path A already complete, or a clean stack / post-cleanup state (no OPEN seed rupture you still plan to reconcile over sealed windows).
2. Run `./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival`.
3. Wait until Admin API is healthy again.
4. Admin UI → **Integrity** → **Lifecycle overview**: `externalArchivalEnabled` should read **Yes**.
5. Under **Exceptional maintenance**, open **Seal Archive**.
6. Use **checkpoint ID mode** on a **single empty** `REGULAR` / `ACTIVE` window (script prints candidates; prefer `entry_count = 0`).
7. Justification ≥ 10 characters → seal (optional: include `LAB_CUT3` in the text for readability in DB notes).
8. Lifecycle overview: `confirmationRequired` → **Yes**; sealed tranche ids shown.
9. **Confirm archived** appears → open it, supply a lab `exportBundleDigest` (≥ 16 chars), confirm.
10. Expect tranche → `EXPORTED` and the button to clear on refresh.

If you still have an OPEN seed rupture from path A: **do not** deep-link Reconcile it after this seal if the alert window covers the sealed checkpoint — run full cleanup first or reconcile only before sealing.

---

## Safety

- Lab override and SQL markers are **local/demo only**.
- Do not commit a permanent `external-archival-enabled=true` flip in Docker product defaults for this QA path.
- Default cleanup restores `reason`, clears OPEN lab alerts, and resets lab `ARCHIVE_SEAL` / `EXPORTED` rows. `MANIPULATION_CONCILIATION` from a successful reconcile is left intact; use clean-start for a fully fresh DB.
