# UI Walk Gate — `WALK-2026-09-21-integrity-cut2-phase-b`

Copy into the PR description / Isabelle dispatch brief.
Canon: [`../../templates/ui-walk-gate.template.md`](../../templates/ui-walk-gate.template.md), [`../ui-walk-done-gate.md`](../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-09-21-integrity-cut2-phase-b`
- **Owner (intention):** Julie / Marc
- **Walk executor:** Isabelle (default)
- **Single walkable SHA / PR:** https://github.com/mgagp/ezkey/pull/598 — walk **PR head** (`git fetch && git rev-parse origin/cursor/integrity-cut2-phase-b-936e`); tip at authoring was `a939584dba1eb9796cae658367da9bf95fd708ce`
- **Related craft PR(s):** https://github.com/mgagp/ezkey/pull/598
- **Created:** `2026-09-21`
- **Intention note:** [`../../admin-ui-integrity-cut2-phase-b.md`](../../admin-ui-integrity-cut2-phase-b.md)
- **Parent IA:** [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md) (cut 2)
- **Predecessor:** [`WALK-2026-09-21-integrity-cut2-phase-a.md`](WALK-2026-09-21-integrity-cut2-phase-a.md) (density rules stay inside modes)

## Done (one line)

> One SHA where `/integrity` exposes Observe | Verify | Remediate in-page modes (correct defaults + deep-links), phase A density still holds inside modes, Alerts chrome is unchanged, EN+FR mode labels work, and the incidents list pager calls the existing Page API with `page`/`size` — Isabelle numbered PASS with captures + network → mergeable.

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
| 1 | **Healthy seed:** bare `/integrity` opens in **Observe** (mode chrome shows Observe selected / active) | Must NOT land in Remediate or Verify by default when healthy | capture + URL bar |
| 2 | **Manual mode switch:** operator can switch Observe → Verify → Remediate (and back) without leaving `/integrity` | Must NOT navigate to a new sidebar item or `/integrity/...` sub-route | capture + URL bar |
| 3 | **Non-healthy seed** (gaps **or** actionable incident **or** sealed awaiting confirm **or** chain non-green): default mode is **Remediate** | Must NOT hide pending work in Observe with no attention cue | capture (side-by-side vs #1) |
| 4 | **Deep-link** `/integrity?action=reconcile&alertId=<id>`: mode is **Remediate**; reconcile path reachable | Must NOT force Checkpoint timeline fully expanded solely because of `action=reconcile` | capture + URL bar |
| 5 | **Investigation deep-link** (`source=integrity-alert` and/or `focusCheckpointId` / gap locate): mode is **Verify**; timeline / locate behavior matches phase A | Must NOT open Remediate as the default for investigation locate | capture + URL bar |
| 6 | **Alerts chrome unchanged:** Alerts list / detail have no new Integrity mode chrome; Resolve still deep-links into Integrity | Must NOT add Observe/Verify/Remediate controls on Alerts | capture |
| 7 | **Phase A density regression:** healthy → Remediate cluster + timeline collapsed inside modes; non-healthy → Remediate panels auto-expand per phase A | Must NOT regress phase A collapse / expand rules when modes ship | capture (healthy + non-healthy) |
| 8 | **Locales:** EN walk of mode chrome; FR shows **Observer / Vérifier / Remédier** (or locked FR strings from the intention table) | Must NOT leave raw EN keys or missing FR mode labels | capture EN + FR |
| 9 | **B′ pager:** Operational incidents list shows page/size controls; changing page or size triggers `GET …/lifecycle/incidents` with matching `page` and `size` query params | Must NOT keep a silent hardcoded `page=0&size=50` with no pager when more than one page is needed (or when size is changed) | capture + network |
| 10 | **B′ empty/error usable:** empty incidents page shows the empty message; failed fetch remains recoverable (message / retry or refresh still reachable) | Must NOT blank the incidents body with no recovery path | capture |

## Locales

- [ ] EN (this walk)
- [ ] FR mode labels (required for assert #8)
- _or:_ EN only this walk — **not** allowed for mode chrome; FR labels are in-scope for B

## Network / API claims (if any)

- Must see: existing Integrity calls; for B′ specifically `GET /api/v1/audit-logs/lifecycle/incidents?page=…&size=…` (and existing `sort`) when the pager is used.
- Must not see: new status-strip / phase C endpoints; inventing a second journal API; Alerts list API changes for this slice.

## Seed / data plan (Marc — critical)

Positive/negative proof on the **same SHA**: healthy → Observe (#1, #7) then non-healthy → Remediate (#3) plus deep-links (#4, #5). Reuse cut-3 lab helpers like phase A.

### Healthy fixture

Goal: continuous chain, no undeclared gaps in the UI default window, no `RECOVERED_PENDING_DECLARATION` / `IN_PROGRESS` incidents, no sealed tranche with `confirmationRequired=true`.

```bash
# Repo root — warm default stack (monitoring on)
cd ezkey-tests && ./clean-start.sh
```

Operator observation on `/integrity` after Global Admin login:

- Mode chrome defaults to **Observe**.
- Phase A: Exceptional Maintenance collapsed; Operational incidents collapsed; timeline collapsed.
- Verification actions remain reachable by switching to **Verify** (assert #2).

If a prior lab run left seals / OPEN rupture alerts / heartbeat landmines:

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh
./scripts/lab/cleanup-alerts-ui-review.sh   # if polish-seed heartbeat alerts remain
```

### Non-healthy fixture (pick **at least one** for #3; add path A for #4)

| Intent | How | Expected UI |
|--------|-----|-------------|
| **Sealed awaiting confirm** | Path (B) in [`docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md`](../../../docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md): `./scripts/lab/seed-integrity-cut3-qa.sh --enable-archival` → Seal Archive → Lifecycle confirmation required | Default mode **Remediate**; maintenance auto-expanded |
| **Integrity rupture / reconcile deep-link** | Path (A): `./scripts/lab/seed-integrity-cut3-qa.sh` → Run validation → Alerts OPEN `AUDIT_INTEGRITY_RUPTURE` → `/integrity?action=reconcile&alertId=<id>` | **Remediate**; reconcile reachable; timeline **not** forced by reconcile alone |
| **Investigation locate** | Alert Investigate / `source=integrity-alert` / `focusCheckpointId` (as today) | **Verify**; timeline / focus as phase A |

**Order on one warm stack:** finish rupture reconcile (A) **or** cleanup before sealing (B). See cut-3 lab doc.

### B′ pager seed

- Prefer enough incident rows to exercise page change, **or** change **page size** and prove the network query updates (`page` / `size`).
- Disposable local only — do not invent a second journal. If the stack has fewer than one page at default size, assert #9 via **size change** still counts.

### Cleanup

```bash
./scripts/lab/cleanup-integrity-cut3-qa.sh
```

## Out of scope (one line)

> No Alerts redesign; no tamper-proof copy; no second journal; no phase C status-strip / API; no new Integrity sidebar item or sub-routes; no Tenant Admin access; no pixel polish campaign beyond mode chrome + pager.

## Expected verdict

- [ ] PASS (ship / merge UI slice)
- [ ] FAIL documented (baseline / intentional gap) — list which rows must fail

## Dispatch rule

- **Do not walk** until this block is filled and the SHA is walkable (`TBD` must be replaced).
- **Do not declare UI complete** without Isabelle PASS (or explicit documented FAIL baseline).
- Entry may be Marc→Julie / Marc→Patrick / Marc→K; **exit walk always uses this gate**.
