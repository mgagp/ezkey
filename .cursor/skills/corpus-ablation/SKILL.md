---
name: corpus-ablation
description: >-
  HITL prune of ephemeral scaffolds (Cursor plans, prompt plans) into existing canon while
  preserving discoverability for design signals cold agents need. Use when the operator says
  corpus-ablation, points at .cursor/plans/*, or continues a plan-prune / parking pass.
disable-model-invocation: true
---

# Corpus ablation

## Purpose

Delete liquid scaffolding after the durable signal lives in **existing** canon (`docs/`,
`product-docs/`, module `AGENTS.md`, or executable shared code). Park adjacent micro-drift.
Do **not** invent `I-*` / `TB-*` per finding.

## Authority

- Lane: [`product-docs/global/hygiene/corpus-ablation/README.md`](../../product-docs/global/hygiene/corpus-ablation/README.md)
- Active pass: [`…/2026-08-cursor-plans-pass.md`](../../product-docs/global/hygiene/corpus-ablation/2026-08-cursor-plans-pass.md)
- Hygiene index: [`product-docs/global/hygiene/README.md`](../../product-docs/global/hygiene/README.md)
- Hygiene vs program: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) § *Three rules worth keeping*
- Retained plans: methodology § *Ephemeral scaffold vs. retained plan*

## Per-document loop (HITL)

1. Operator points at one scaffold (or says continue).
2. Read it; cross-check code + living docs (do not ritual-read the whole corpus).
3. Classify:
   - **Go** — single preferred package (see below).
   - **Keep** — still unique option space (Lane B retained); record under Kept this pass.
   - **Promote then delete** — fold unique substance into V/I/docs first, then delete.
4. Propose **Go** as the **most corpus-adapted package** (discoverability included); **wait for
   Go / Keep / variant**. Variants (e.g. Keep, Go without fix) only when useful — never present
   “optional parking” outside Go as if it were separate from the recommendation.
5. On Go: apply the full package; append Done (+ parking / Kept) in the active pass note.
   After each applied Go, refresh the pass-log **Resume** block (next file, constraints, open
   parking) so a **cold session without a pasted hand-off** can match this quality.

### Go briefing shape (mandatory)

One plan per turn. Lead with a single actionable **Go** the operator can accept as-is:

1. **Delete or Keep** — and why (shipped / already in canon / unique option space).
2. **In-pass now** — cheap discoverability or subject-blocking drift a cold agent would miss
   (pointers, stale snippets, entry-path links). Say “none” when none.
3. **Park inside Go** — adjacent / larger edits as `P-NNN` rows (receptacle + why not now).
   Say “none” when none. Parking is part of Go, not an optional aside.
4. **Wait** — do not edit until Go / Keep / variant.

**Go = the one best package** for documentary realignment + discoverability, not a minimal
delete. Build it with a pragmatic corpus edit budget:

| Include in Go | When |
|---------------|------|
| **Delete** the scaffold | Essence shipped / already in canon (or after promote) |
| **In-pass cheap edits** | Subject-blocking drift **or** discoverability gaps a cold agent would miss — fix **now** (pointers, stale snippets, entry-path links) |
| **Parking `P-NNN` rows** | Adjacent / larger / lower-urgency edits that should **not** inflate this turn — **still part of Go** (record them; do not leave as “optional aside”) |

Do **not** offer a bare delete as Go when a small doc/discoverability pass is clearly warranted.
Do **not** treat parking as optional flavoring — if an edit should wait, **park it inside Go**.

## Discoverability check (mandatory on each plan)

Ask: *Would a cold agent implementing the next similar screen/feature find and reuse this design
without the plan?*

| Signal strength | Where it should live | Ablation action |
|-----------------|----------------------|-----------------|
| Executable shared module + ≥2 call sites | Code is primary canon | Delete plan; add **2–4 lines** in module `AGENTS.md` (or design-decisions) **only if** the entry path is easy to miss |
| One-line placement / “use X not Y” rule | Module `AGENTS.md` | Prefer in-pass pointer over parking forever |
| Cross-cutting architecture / protocol | ADR or `docs/` | Promote if missing; do not keep Cursor plan as ADR substitute |
| Open option space, no chosen direction | `V-*` / `I-*` (or Keep plan) | Do not flatten into a false decision |
| Session inventory / line numbers / todos | Nowhere | Delete freely |

**Anti-patterns**

- Do **not** ADR every UI convention (noise).
- Do **not** paste full analysis into canon (noise). Prefer pointer → code.
- Do **not** rely on file-header comments alone as the cold-start entry (agents often read
  `AGENTS.md` first).
- Do **not** keep `.cursor/plans/*` as the discoverability layer after materialization.

**Equilibrium:** keep signal cold agents need; trust pattern + call sites for the rest. Prefer
discoverability pointers over re-specification.

## Parking

- Append `P-NNN` rows for deferred doc drift found adjacent to the pointed plan.
- Parking rows proposed in the briefing are **part of the default Go package** when Go is chosen
  (operator accepts deferred work; burn-down stays deferred until they say so).
- Burn down when opening that source doc or at campaign closeout.
- Prefer in-pass over parking when the fix is cheap and closes a cold-agent discoverability gap.

## Cold start (no pasted hand-off)

A fresh session should match a hand-off session if it loads, in order:

1. This skill (Go briefing shape + discoverability rubric).
2. Lane README + the active pass note — especially the **Resume** block (next file,
   constraints, open parking, do-not-touch).
3. The next scaffold named in Resume (or the next remaining file alphabetically under
   `.cursor/plans/archived/`).

Do **not** invent a richer process than that. If Resume is stale, repair it in-pass before
briefing the next plan. Session-only judgment that is not in Resume is a method defect — write
it down there, do not rely on chat memory or a hand-off prompt.

## Kickoff (paste-ready)

```text
corpus-ablation — continue 2026-08 Cursor plans pass.
Read .cursor/skills/corpus-ablation/SKILL.md, then the pass-log Resume block in
product-docs/global/hygiene/corpus-ablation/2026-08-cursor-plans-pass.md.
One plan per turn. Brief Go as: delete-or-keep + in-pass now + park-inside-Go. Wait for Go.
```
