# Corpus ablation (hygiene)

HITL pass to prune ephemeral scaffolds (Cursor plans, GitHub prompt-style plans) after
extracting any remaining essence into existing canonical docs — and after checking that
**discoverability** for design signals cold agents need is preserved.

**Not** product vision, ADR, or backlog execution. No `I-*` / `TB-*` per finding.

## Invoke

- Cursor skill: [`.cursor/skills/corpus-ablation/SKILL.md`](../../../.cursor/skills/corpus-ablation/SKILL.md)
  (keyword **`corpus-ablation`**)
- Active parking / Done log: [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md)

## Operating rules

1. One pointed document per iteration → essence → canonical receptacle → delete when ready.
2. **Go** = the recommended path for that plan (usually delete when liquidable, **plus** cheap
   in-pass discoverability/subject fixes when those are recommended). Append parking rows for
   adjacent micro-alignments not included in that Go — burn down when the operator chooses.
3. Drift **on the pointed document’s subject** that is still blocking a clean delete: include the
   cheap canon fix in the recommended Go; otherwise park and say so.
4. Git history is the archive — do not invent `archive/` copies of deleted plans.
5. **Discoverability check** (each plan): would a cold agent reuse this design without the plan?
   If the signal is easy to miss from `AGENTS.md` / call sites, prefer a **2–4 line pointer** in the
   right module guide (or an in-pass fix) over keeping the plan or writing an ADR. Full rubric in
   the skill. Do not over-specify what pattern + call sites already teach.

## Active campaign

| File | Scope |
|------|--------|
| [`2026-08-cursor-plans-pass.md`](2026-08-cursor-plans-pass.md) | `.cursor/plans/*` and related prompt noise; parking list for deferred doc fixes |
