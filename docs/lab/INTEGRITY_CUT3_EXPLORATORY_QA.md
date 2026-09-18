# Integrity cut-3 — exploratory QA seed (lab only)

**Audience:** Isabelle / operators validating Admin UI Integrity cut-3 on a **warm local Docker stack**  
**PR context:** Alerts signal-only + Integrity deep-link reconcile; conditional **Confirm archived**  
**Non-goals:** no second journal, no Integrity API mocks, no product redesign, no permanent compose default change

Clean-start does **not** leave an `AUDIT_INTEGRITY_RUPTURE` or a sealed tranche with `confirmationRequired=true`. Use the lab helpers below, then the Admin UI steps.

Related canon: [`docs/AUDIT_LOG_INTEGRITY.md`](../AUDIT_LOG_INTEGRITY.md) (HMAC + chain + lifecycle endpoints).

---

## Prerequisites

1. Warm stack (e.g. `ezkey-tests/clean-start.sh` already completed; Admin API healthy).
2. Global Admin session (`admin.docker` + Demo Device) in Admin UI.
3. From repo root, Bash available.

**Do not** use `scripts/lab/seed-alerts-ui-review.sh` for reconcile QA. That inserts a **fake** rupture payload for Alerts list polish and also seeds an OPEN `AUDIT_CHAIN_HEARTBEAT_STALE`, which **blocks** real reconcile. If it was applied:

```bash
./scripts/lab/cleanup-alerts-ui-review.sh
```

---

## One-shot lab seed

```bash
# (A) Induce a real per-entry HMAC mismatch (SQL only)
./scripts/lab/seed-integrity-cut3-qa.sh

# (B) Also flip Admin API external-archival-enabled=true (brief admin-api recreate)
./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival

# Archival flag only
./scripts/lab/seed-integrity-cut3-qa.sh --archival-only
```

Cleanup:

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh              # remove tamper marker
./scripts/lab/cleanup-integrity-cut3-qa.sh --all        # untamper + drop archival override
```

Lab files:

| File | Role |
|------|------|
| `scripts/lab/seed-integrity-cut3-qa.sh` | Entry point |
| `scripts/lab/seed-integrity-rupture-lab.sql` | Mutate one signed `reason`, leave `entry_hmac` |
| `scripts/lab/docker-compose.lab-external-archival.yml` | Override only; not a product default |
| `scripts/lab/cleanup-integrity-cut3-qa.sh` | Untamper / disable override |

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

- Reconcile rejects when an OPEN `AUDIT_CHAIN_HEARTBEAT_STALE` exists — clear fake lab alerts first.
- Boundaries come from the **alert payload** loaded by `alertId` on Integrity (no fabricated second journal).

---

## Path (B) — Sealed tranche → Confirm archived

Default Docker keeps `ezkey.audit.archive.external-archival-enabled=false`, so `confirmationRequired` stays false even after sealing.

1. Run `./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival` (or `--all` with tamper).
2. Wait until Admin API is healthy again.
3. Admin UI → **Integrity** → **Lifecycle overview**: `externalArchivalEnabled` should read **Yes**.
4. Under **Exceptional maintenance**, open **Seal Archive**.
5. Use **checkpoint ID mode** on a **single empty** `REGULAR` / `ACTIVE` window (script prints candidates; prefer `entry_count = 0` so a prior lab tamper in another window does not fail seal pre-flight).
6. Justification ≥ 10 characters → seal.
7. Lifecycle overview: `confirmationRequired` → **Yes**; sealed tranche ids shown.
8. **Confirm archived** appears → open it, supply a lab `exportBundleDigest` (≥ 16 chars), confirm.
9. Expect tranche → `EXPORTED` and the button to clear on refresh.

To return Admin API to default archival policy:

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh --disable-archival
```

---

## Suggested order on one warm stack

1. **(B)** enable archival → seal empty checkpoint → exercise Confirm archived.  
2. **(A)** tamper → Run validation → Alerts deep-link → Reconcile.  

Or run `--enable-archival` once, seal an empty window, then tamper + validate (tamper lives in the earliest signed row’s window — keep seal off that window).

---

## Safety

- Lab override and SQL markers are **local/demo only**.
- Do not commit a permanent `external-archival-enabled=true` flip in Docker product defaults for this QA path.
- Untamper restores `reason` text only; if you already reconciled, leave the conciliation registry as historical lab evidence or clean-start for a fresh DB.
