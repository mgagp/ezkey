# Corpus ablation (hygiene)

HITL pass to prune ephemeral scaffolds (Cursor plans, GitHub prompt-style plans) after
extracting any remaining essence into existing canonical docs — and after checking that
**discoverability** for design signals cold agents need is preserved.

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
| [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md) | `.cursor/plans/*` and related prompt noise; parking list for deferred doc fixes |
