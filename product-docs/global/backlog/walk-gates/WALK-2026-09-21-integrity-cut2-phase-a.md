# UI Walk Gate — `WALK-2026-09-21-integrity-cut2-phase-a`

Copy into the PR description / Isabelle dispatch brief.
Canon: [`../../templates/ui-walk-gate.template.md`](../../templates/ui-walk-gate.template.md), [`../ui-walk-done-gate.md`](../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-09-21-integrity-cut2-phase-a`
- **Owner (intention):** Julie / Marc
- **Walk executor:** Isabelle (default)
- **Single walkable SHA / PR:** https://github.com/mgagp/ezkey/pull/588 @ `f2d79ca7`
- **Related craft PR(s):** none (phase A is UI + docs only)
- **Created:** `2026-09-21`
- **Intention note:** [`../../admin-ui-integrity-cut2-phase-a.md`](../../admin-ui-integrity-cut2-phase-a.md)
- **Parent IA:** [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md) (cut 2)

## Done (one line)

> One SHA where healthy `/integrity` keeps Remediate (Exceptional Maintenance + Operational incidents + gaps list) and the Checkpoint timeline collapsed; non-healthy or remediation deep-link opens the right panels; Verification still works; Isabelle numbered PASS with captures → mergeable.

## Stack

- Runtime profile: **default** (`integrity` — monitoring on). Do **not** use `--runtime=base` for this walk.
- How to start:
  1. From `ezkey-tests/`: `./clean-start.sh` (warm Admin API + checkpoints).
  2. Admin UI: `cd ezkey-admin-ui && npm run generate:api && npm run dev` (or `./start.sh` for Docker UI on `:3090`).
- Admin role(s): **Global Admin** only (`/integrity` is GA-gated).
- Login path: `admin.docker` + Demo Device (`http://localhost:8083/phone/ezkey`); challenge as usual.

## Checklist (numbered, binary)

| # | Assert (what must be true) | Also assert absence (must NOT) | Proof |
|---|----------------------------|--------------------------------|-------|
| 1 | **Healthy seed:** on first paint of `/integrity`, Exceptional Maintenance is **collapsed** (`[data-testid=integrity-maintenance-toggle]` `aria-expanded=false`; Seal / Confirm / Reconcile buttons not visible until expanded) | Must NOT show Seal Archive / Confirm archived / Reconcile buttons without expanding | capture |
| 2 | **Healthy:** Checkpoint timeline **collapsed** (`[data-testid=integrity-timeline-toggle]` `aria-expanded=false`; no checkpoint table) | Must NOT load / show a tall timeline table on healthy open | capture |
| 3 | **Healthy:** Undeclared gaps area is **empty-compact** (green “no undeclared gaps” message) **or**, if gaps were present and cleared, list not tall-expanded by default | Must NOT show a tall expanded gap list on healthy paint | capture |
| 4 | **Verification** still usable: Verify chain / Verify entry / Run validation buttons work; auto chain-verify on mount may run; report stats appear under Verification | Must NOT require expanding timeline or Remediate to run verification | capture / network |
| 5 | **Non-healthy seed** (gaps **or** actionable incident **or** sealed awaiting confirm): Remediate cluster **auto-expanded** (maintenance and/or incidents body visible; gaps list expanded when gap count > 0) | Must NOT leave pending work hidden behind collapsed Remediate with no attention cue | capture (side-by-side vs #1) |
| 6 | **Deep-link** `/integrity?action=reconcile&alertId=<id>` (after cut-3 rupture seed + validation alert): Remediate opens and reconcile path is reachable without hunting (dialog auto-open OK) | Must NOT force Checkpoint timeline fully expanded solely because of `action=reconcile` | capture + URL bar |
| 7 | **Alerts** unchanged: Alerts list / detail have no new Integrity chrome; Resolve still deep-links into Integrity | Must NOT add Integrity density controls on Alerts | capture |
| 8 | **Locales:** EN walk of rows 1–7; FR keys present for new `integrity.disclosure.*` (spot-check FR label on maintenance/incidents) | Must NOT leave untranslated `integrity.disclosure.attention` in FR | note / capture |

## Locales

- [ ] EN (this walk)
- [ ] FR keys present (smoke / spot-check)
- _or:_ EN only this walk — FR parity of new keys still required in the PR

## Network / API claims (if any)

- Must see: existing Integrity calls (`chain-integrity`, `archive-eligibility`, `lifecycle/incidents`, optional `integrity/bootstrap`) — **no new endpoints**.
- Must not see: inventing a second journal API; incidents pagination changes.

## Seed / data plan (Marc — critical)

Positive/negative proof: Isabelle records **healthy collapse** (#1–#3) then **non-healthy expand** (#5) on the **same SHA** (before/after or side-by-side captures).

### Healthy fixture

Goal: continuous chain, no undeclared gaps in the UI default window, no `RECOVERED_PENDING_DECLARATION` / `IN_PROGRESS` incidents, no sealed tranche with `confirmationRequired=true`.

```bash
# Repo root — warm default stack (monitoring on)
cd ezkey-tests && ./clean-start.sh

# Wait until Admin API healthy and a few REGULAR checkpoints exist
# (scheduler creates windows; typically ready shortly after clean-start completes)

# Optional: confirm no actionable incidents (psql — disposable local only)
docker exec ezkey-postgres psql -U postgres -d ezkey_db -c \
  "SELECT status, count(*) FROM ezkey_audit_chain_incident GROUP BY status;"

# Confirm archival: use Integrity → Lifecycle Overview
# (confirmation required = No; no sealed tranche awaiting confirm)
```
Operator observation on `/integrity` after Global Admin login:

- Verification may auto-run; report shows intact / 0 undeclared gaps (or green compact gaps message).
- Exceptional Maintenance collapsed; Operational incidents collapsed; timeline collapsed.
- Lifecycle Overview may remain visible (policy summary) — that is **not** Remediate density.

If a prior lab run left seals / OPEN rupture alerts / heartbeat landmines:

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh
./scripts/lab/cleanup-alerts-ui-review.sh   # if polish-seed heartbeat alerts remain
```

Then re-open `/integrity` (hard refresh) for the healthy paint.

### Non-healthy fixture (pick **at least one**)

Prefer existing lab helpers (EXP1 / local disposable). Do **not** invent a second journal.

| Intent | How | Expected UI |
|--------|-----|-------------|
| **Sealed awaiting confirm** | Path (B) in [`docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md`](../../../docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md): `./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival` → Seal Archive on an empty REGULAR window → Lifecycle shows confirmation required | Exceptional Maintenance **auto-expanded**; Confirm archived visible |
| **Integrity rupture / reconcile deep-link** | Path (A): `./scripts/lab/seed-integrity-cut3-qa.sh` → Integrity **Run validation** → Alerts OPEN `AUDIT_INTEGRITY_RUPTURE` → open `/integrity?action=reconcile&alertId=<id>` | Remediate open; reconcile dialog / button reachable; timeline **not** forced open by reconcile alone |
| **Undeclared gaps** | If chain-integrity reports gaps after downtime simulation (delete intermediate checkpoints only on disposable DB — advanced; prefer rupture/confirm paths for phase A) | Gaps toggle expanded with list when count > 0 |

**Order on one warm stack:** finish rupture reconcile (A) **or** cleanup before sealing (B). See cut-3 lab doc — do not seal then re-reconcile over `ARCHIVE_SEAL` windows.

Minimal non-healthy for phase A density: **(B) awaiting confirm** is enough for assert #5; add **(A) deep-link** for assert #6.

### Cleanup

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh
```

## Out of scope (one line)

> No Observe/Verify/Remediate mode tabs; no incidents pagination; no Alerts chrome redesign; no API redesign; no tamper-proof copy; no cut-2 phase B.

## Expected verdict

- [ ] PASS (ship / merge UI slice)
- [ ] FAIL documented (baseline / intentional gap) — list which rows must fail

## Dispatch rule

- **Do not walk** until this block is filled and the SHA is walkable.
- **Do not declare UI complete** without Isabelle PASS (or explicit documented FAIL baseline).
- Entry may be Marc→Julie / Marc→Patrick / Marc→K; **exit walk always uses this gate**.
