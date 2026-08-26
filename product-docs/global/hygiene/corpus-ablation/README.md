# Corpus ablation (hygiene)

HITL pass to prune ephemeral scaffolds (Cursor plans, GitHub prompt-style plans) after
extracting any remaining essence into existing canonical docs — and after checking that
**discoverability** for design signals cold agents need is preserved.

For broader legacy-document decisions, use [`../document-hygiene/`](../document-hygiene/) and the
`document-hygiene-curated` skill. This lane stays focused on scaffolding that should usually
disappear once the substance is canonized.

**Not** product vision, ADR, or backlog execution. No `I-*` / `TB-*` per finding.

## Invoke

- Cursor skill: [`.cursor/skills/corpus-ablation/SKILL.md`](../../../.cursor/skills/corpus-ablation/SKILL.md)
  (keyword **`corpus-ablation`**)
- Active parking / Done / **Resume** log: [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md)
  (cold start: read the Resume block — a pasted hand-off is optional)

## Operating rules

1. One pointed document per iteration → essence → canonical receptacle → delete when ready.
2. **Go** = the **single best package** for corpus realignment **including discoverability** — not
   a minimal delete. Include cheap in-pass edits now when warranted; include **parking `P-NNN`
   rows inside that same Go** for adjacent / deferred edits (parking is not an optional aside).
3. Drift **on the pointed document’s subject** that still blocks a clean delete or would mislead a
   cold agent: prefer the cheap canon fix **in Go**. Larger or lower-urgency adjacent drift → park
   **as part of Go**, burn down later when the operator chooses.
4. Git history is the archive — do not invent `archive/` copies of deleted plans.
5. **Discoverability check** (each plan): would a cold agent reuse this design without the plan?
   If the signal is easy to miss from `AGENTS.md` / call sites, prefer a **2–4 line pointer** in the
   right module guide (or an in-pass fix) over keeping the plan or writing an ADR. Full rubric in
   the skill. Do not over-specify what pattern + call sites already teach.

## Active campaign

| File | Scope |
|------|--------|
| [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md) | Working HITL inventory: Resume, Done, Parking |
| [`2026-08-17-cursor-plans-closeout.md`](2026-08-17-cursor-plans-closeout.md) | Dated closeout when the archived `plan-*` queue emptied |

## Log structure (this lane)

Same split as doctor / pentest campaign notes:

| Artifact | Role |
|----------|------|
| **Pass log** (`YYYY-MM-…-pass.md`) | Live HITL workbook. Update Resume after every Go. |
| **Dated closeout** (`YYYY-MM-DD-…-closeout.md`) | Campaign instance when a queue empties — why remaining parking stayed deferred. |
| **This README** | Lane entry. Do not duplicate the pass log. |

Do **not** create `I-*` / `TB-*` / `ML-*` for a hygiene ablation pass.
