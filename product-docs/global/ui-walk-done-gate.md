# Human UI surface — Walk / Done gate (canon)

## Purpose

One contract for “UI slice is complete”: **free entry** (any issue, review or session may start it), **unique exit** (a filled interface gate + a scripted walk verdict that Marc confirms).

Applies to **any human UI surface** touched by the slice: Admin UI, demo apps, tester pages, and mobile copy — not Admin UI alone.

This is not a process ceremony. It exists because missing or moving completion criteria produced split PR walks and shallow QA (#570 lesson). Retrospectives such as PR #726 showed the same gap when demos and tester pages were left outside the gate.

## Rule

Nobody (human or bot) declares a human-UI chantier **complete** without:

1. A single **walkable SHA / PR** (not a chain of “almost” trailers).
2. A filled [`ui-walk-gate.template.md`](../templates/ui-walk-gate.template.md) block.
3. A walk **PASS** on that checklist with proofs — or an explicit **FAIL documented** baseline.

## Who does what

Functions are defined in [`team-roles.md`](team-roles.md).

| Function | Responsibility |
|----------|----------------|
| **Interface gate** (written before) | The session that frames the slice fills the checklist before the PR is called “ready to walk”. |
| **Engineering review** | Craft and API placement. May signal “gate missing”; does not declare product done without one. |
| **Product decision** | Copy and naming locks, decided by Marc, ideally before the first implementation. |
| **Scripted walk** (executed after) | No green walk without a gate; PASS only with per-row proof. Marc confirms the verdict. |

## Sizing the gate

Keep the gate proportional to the change (Marc, 2026-11-24):

- **Copy on one screen** (help text, label, placeholder, no layout or flow change): a checklist of
  three to five rows in the PR description, plus a walk spec. No `WALK-*` record file.
- **A flow, a screen's composition, or a new entry mode**: a `WALK-*` record under
  `backlog/walk-gates/` from the template, indexed below.

## Reproducible walks (Admin UI)

An Admin UI walk lives in the repo as a Playwright spec against a mocked Admin API:
`ezkey-admin-ui/e2e/walks/*.walk.spec.ts`, run with `npm run test:walk` (no Docker stack). One spec
per Walk Gate record, one `test.step` per checklist row, EN and FR. A walk script kept only on a
bot's side or in a scratchpad cannot be re-run, so it does not count as proof. How to write and run
one: `ezkey-admin-ui/AGENTS.md` § *Walk Gate walks (mocked Admin API, no stack)*. A mocked walk
covers operability and copy; flows that depend on the backend or the Demo Device still need the
device-backed suite.

## Lesson from #570 (base runtime + Integrity honesty)

Symptoms: runtime smoke, then honesty FAIL baseline, then unified PASS — three bars on one thread; artificial #566→#569→walk sequencing; late product/API locks.

Root cause: **done moved**. Not “the walk was too light” once checklist 1–6 was fixed on one SHA.

Standing rules:

1. One brief page: audience, non-goals, **done = SHA + numbered checklist + merge**.
2. Settle the product decision (copy) and the engineering review (API placement) **before** the first implementation when honesty/API surface is involved.
3. One walkable PR (or stack announced as one), one CA owner, one walk, one close.
4. Intention docs carry the walk criteria, not only the “why”.

## Related

- Verification functions: [`team-roles.md`](team-roles.md)
- Template: [`../templates/ui-walk-gate.template.md`](../templates/ui-walk-gate.template.md)
- Example unified delivery: GitHub PR #570
- Integrity IA pack: [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md)
- Filled Walk Gates (Integrity cut 2):
  - [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md) — phase A density
  - [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md) — phase B+B′ modes + incidents pager (`TBD` SHA until impl PR)
- Walk Gate stub (Integrity cut 3):
  - [`backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md`](backlog/walk-gates/WALK-2026-10-09-integrity-cut3.md) — last verdict / micro-dashboard / verify cap (`TBD` SHA until impl PR)
- Filled Walk Gate (administrator onboarding, #1313):
  - [`backlog/walk-gates/WALK-2026-11-17-admin-onboarding-1313.md`](backlog/walk-gates/WALK-2026-11-17-admin-onboarding-1313.md) — record landing, bind card, Global and Tenant Admin views, EN/FR (PR #1398)
- Filled Walk Gate (enrollment Name / User ID, #940):
  - [`backlog/walk-gates/WALK-2026-11-23-enrollment-user-id-940.md`](backlog/walk-gates/WALK-2026-11-23-enrollment-user-id-940.md) — User ID under Name, help, empty-User-ID warning on application integrations only, EN/FR (PR #1455)
  - [`backlog/walk-gates/WALK-2026-11-25-enrollment-code-1494.md`](backlog/walk-gates/WALK-2026-11-25-enrollment-code-1494.md) — copy the enrollment code (the QR JSON, server-composed) on the record, the administrator bind card, activation and recovery, EN/FR (#1494)
