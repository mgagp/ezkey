# Admin UI — Walk / Done gate (canon)

## Purpose

One contract for “UI slice is complete”: **free entry** (Marc may open with Julie, Patrick, or K), **unique exit** (a filled Walk Gate + Isabelle verdict).

This is not a process ceremony. It exists because missing or moving completion criteria produced split PR walks and shallow QA (#570 lesson).

## Rule

Nobody (human or bot) declares an Admin UI chantier **complete** without:

1. A single **walkable SHA / PR** (not a chain of “almost” trailers).
2. A filled [`ui-walk-gate.template.md`](../templates/ui-walk-gate.template.md) block.
3. Isabelle **PASS** on that checklist with proofs — or an explicit **FAIL documented** baseline.

## Who does what

| Role | Responsibility |
|------|----------------|
| **Intention owner** (often Julie, sometimes K) | Fill the Walk Gate before coding is called “ready to walk”. |
| **Patrick** | Craft/API placement. May signal “walk gate missing”; does not invent product done or call Isabelle without a gate. |
| **Alex** | Product copy / naming locks when asked — ideally before first implementation. |
| **Isabelle** | Refuse a green walk if the gate is missing; PASS only with per-row proof. |
| **K** | When Marc routes via K: fill or demand the gate, then dispatch. Not a mandatory bottleneck. |

## Lesson from #570 (base runtime + Integrity honesty)

Symptoms: runtime smoke, then honesty FAIL baseline, then unified PASS — three bars on one thread; artificial #566→#569→walk sequencing; late product/API locks.

Root cause: **done moved**. Not “Isabelle too light” once checklist 1–6 was fixed on one SHA.

Standing rules:

1. One brief page: audience, non-goals, **done = SHA + numbered checklist + merge**.
2. Grill Alex (copy) + Patrick (API placement) **before** first Cloud Agent when honesty/API surface is involved.
3. One walkable PR (or stack announced as one), one CA owner, one walk, one close.
4. Intention docs carry Isabelle criteria, not only the “why”.

## Related

- Template: [`../templates/ui-walk-gate.template.md`](../templates/ui-walk-gate.template.md)
- Example unified delivery: GitHub PR #570
- Integrity IA pack: [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md)
- Filled Walk Gates (Integrity cut 2):
  - [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md) — phase A density
  - [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md) — phase B+B′ modes + incidents pager (`TBD` SHA until impl PR)
