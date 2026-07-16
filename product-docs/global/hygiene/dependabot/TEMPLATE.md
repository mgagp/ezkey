# Dependabot curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Operator:** Marc (+ agent)
- **Skill:** `dependabot-curated`
- **Open Dependabot PRs at start:** _(list or count)_
- **Highest accepted tier:** T1 / T2 / T3 / T4

## Lots overview

Brief numbered overview only (lot id, tier, PR numbers, one-line blast radius). Keep to ≈3–6 lots.

**Agent briefing style:** do not use a dense options matrix as the primary HITL vehicle. Iterate
one lot at a time in dialogue with the operator; record the final decisions table here after HITL
closes.

## Decisions

| Lot | PRs | Tier | Decision | Notes |
|-----|-----|------|----------|-------|
| A | #NNN, #NNN | T1 | merge / hold / defer | |

Decision values:

- **merge** — lot approved; each PR merged individually after CI green
- **hold** — leave open for later session or solo review (still Dependabot-owned)
- **defer** — decline for now; comment on PR; optional `I-*` if investigation cost must persist

## Rationale (short)

One short subsection per lot (2–8 lines). Record risk posture and why not the alternatives.
Do not paste full chat transcripts.

### Lot A — …

### Lot B — …

## Validation evidence

| Step | Ran? | Result |
|------|------|--------|
| Dependabot PR CI (per merged PR) | yes / no | |
| `./scripts/build.sh` | yes / no / n/a | |
| Clean-start stack | yes / no / deferred | |
| Functional tests | yes / no / deferred | |
| Elective functional | yes / no / n/a | |
| Playwright Admin UI | yes / no / deferred / n/a | |
| Exact pin preserved (no accidental `^`) | yes / no / n/a | |
| Documented pins synced (`AGENTS.md`, …) | yes / no / n/a | |
| Workspace install (`npm ls` / Yarn clean) | yes / no / n/a | |
| Codegen after Orval / OpenAPI generator bump | yes / no / n/a | |
| Exploratory human | yes / no / n/a | |

If **T1-only shortcut** was used, state explicitly that stack/Playwright were deferred to the next
T2+ session or weekly milestone.

Pin/install/codegen rows are **required when a merge touched those surfaces**; mark `n/a` only when
no Admin UI / mobile / SDK pin or codegen tool moved in this pass.

## Holds and deferrals

- PRs left open and why
- Deferred disruptors and any `I-*` / release-train note (e.g. TypeScript 7 → Release 7.1)

## Out of scope this pass

PRs or ecosystems intentionally not attacked this session.
