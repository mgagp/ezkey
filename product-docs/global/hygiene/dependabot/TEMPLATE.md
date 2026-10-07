# Dependabot curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Pass type:** Monday full / weekday light (rare full mid-week) / other
- **Operator:** Marc (+ agent)
- **Skill:** `dependabot-curated`
- **Concurrency check:** clear / backed off (detail)
- **Open Dependabot PRs at start:** _(list or count)_
- **Highest accepted tier:** T1 / T2 / T3 / T4
- **Highest Orval tier this pass:** O0 / O1 / O2 / O3 / n/a
- **GitHub write identity:** `GH_TOKEN as <login>` / harness-only (read-only) / n/a

## Lots overview

Brief numbered overview only (lot id, tier, PR numbers, one-line blast radius). Keep to ≈3–6 lots.
Orval lots: name Admin UI vs mobile **separately** and record the **O-tier**.

**Agent briefing style:** do not use a dense options matrix as the primary HITL vehicle. Iterate
one lot at a time in dialogue with the operator; record the final decisions table here after HITL
closes. Weekday light / autonomous sessions may skip dialogue and record decisions directly.

## Decisions

| Lot | PRs | Tier | Orval | Decision | Notes |
|-----|-----|------|-------|----------|-------|
| A | #NNN, #NNN | T1 | n/a | merge / hold / defer | |

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
| Concurrency check (owning pass / ~30 min merges) | yes | clear / backed off |
| Node engines pre-flight (`node -v` vs `engines` / Orval floor) | yes / n/a | |
| Dependabot PR CI (per merged PR) | yes / no | |
| Java BOM pulse (`spring-boot.version` vs current same-minor) | yes / n/a | newer? applied? none (Monday full) |
| SEC-019 overrides reviewed after Boot bump | yes / n/a | kept / dropped |
| Nested pin: `google-java-format` | yes / n/a | current / bump proposed |
| Mobile RN pulse (declared `react` / `react-native` + coupled libs) | yes / n/a | versions recorded (Monday full) |
| Mobile `yarn deps:monitor` (actionable / deferred / high audit) | yes / n/a | summary or none |
| Dependabot mobile PRs peeled | yes / n/a / none | PR #s or empty queue |
| Orval codegen diff (Admin UI and/or mobile; O-tier) | yes / n/a | O0–O3 + one-line summary |
| Characterization tests (O2 only; before + after) | yes / n/a | |
| `./scripts/build.sh` | yes / no / n/a | |
| Clean-start stack | yes / no / deferred | |
| Functional tests | yes / no / deferred | |
| Elective functional | yes / no / n/a | |
| Playwright Admin UI | yes / no / deferred / n/a | |
| Mobile phone smoke tracked (non-blocking; gates next Play AAB) | yes / no / n/a | issue # / residual |
| Exact pin preserved (no accidental `^`) | yes / no / n/a | |
| Documented pins synced (`AGENTS.md`, …) | yes / no / n/a | |
| Workspace install (`npm ls` / Yarn clean) | yes / no / n/a | |
| Codegen after Orval / OpenAPI generator bump | yes / no / n/a | |
| Exploratory human | yes / no / n/a | |
| Weekday `daily-log.md` summarized (Monday) | yes / n/a | |

If **T1-only shortcut** was used, state explicitly that stack/Playwright were deferred to the next
T2+ session or Monday milestone.

Pin/install/codegen rows are **required when a merge touched those surfaces**; mark `n/a` only when
no Admin UI / mobile / SDK pin or codegen tool moved in this pass.

Weekday light passes that only append to `daily-log.md` do **not** need a full copy of this
template unless the operator asks for a mid-week full note.

## Holds and deferrals

- PRs left open and why
- Deferred disruptors and any `I-*` / release-train note (e.g. TypeScript 7 → Release 7.1)

## Out of scope this pass

PRs or ecosystems intentionally not attacked this session.
