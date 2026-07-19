# Backlog Idea — `I-2026-07-11` Mobile doctor-curated continuous hygiene

## Metadata

- **ID:** `I-2026-07-11-mobile-doctor-curated-hygiene`
- **Status:** `ready` (evaluation Go 2026-07-11; MVP under `TB-2026-07-11-mobile-doctor-curated-mvp`)
- **Priority:** `P3`
- **Created at:** `2026-07-11`
- **Updated at:** `2026-07-11`
- **Last reviewed at:** `2026-07-11`
- **Progression markers:** `P3-polish`
- **Component tags:** `mobile`, `android`, `docs`, `tooling`
- **Lane:** `C`
- **Captured by:** Marc (continuous-improvement intent; plan incubation 2026-07-11)
- **GitHub issue:** _(optional — open when board visibility helps)_
- **Tracer bullet:** `TB-2026-07-11-mobile-doctor-curated-mvp`

## Intent

Give maintainers a **punctual, curated React Native / Android mobile static-analysis pass** —
same operating shape as Admin UI `doctor-curated` and Java `java-doctor-curated`: run selected
analyzers, suppress low-signal rules with reasons, emit a weighted P1/P2/P3 shortlist, and let a
cold agent apply a small hygiene fix set worth doing.

## Problem and value

- **Problem:** Mobile already has ESLint/Prettier/`tsc` gates and an early Semgrep+Detekt
  pipeline, but no elevated **doctor** keyword, HITL campaign notes, or evaluation-backed
  shortlist comparable to Java doctor. Biome was referenced without being wired.
- **Expected value:** A repeatable continuous-improvement circle on the short-path Windows
  workspace: run → curated report → brief → small PR. Improves RN + Kotlin hygiene without
  Sonar-scale ceremony.

## Why no separate `V-*`

Orientation already exists via Admin UI / Java doctor patterns and project values (80/20,
hygiene vs program). This idea is **tooling capability**, not a product-direction vision.

## Evaluation outcome (Go 2026-07-11)

Authoritative analysis:
[`../mobile-doctor-curated-evaluation-2026-07-11.md`](../mobile-doctor-curated-evaluation-2026-07-11.md).

**Analyzer shortlist (v1) — Go confirmed:**

| Tool | Family question |
|------|-----------------|
| react-doctor | React / RN structure, perf, anti-patterns? |
| Semgrep OSS (pinned Ezkey mobile pack) | Security / project footgun (JS or Kotlin)? |
| Detekt (narrow) | Kotlin poorly designed or hard to maintain? |

**Rejected for v1:** Biome-in-pipeline, SonarQube, Android Lint-in-curator, Knip as third tool,
CI fail-on-findings.

**Method:** Entrypoint + curator → `logs/mobile-doctor/`; keyword `mobile-doctor-curated`;
AGENTS.md contract; campaign notes under `product-docs/global/hygiene/mobile-doctor/`;
report-only (not `validate:ci` fail path).

## Scope

- **In scope (implementation):**
  - Wire react-doctor into the curated pipeline (Admin UI `doctor-curated.mjs` as template)
  - Keep Semgrep + Detekt; remove Biome phantom from default inputs/docs
  - Curator suppress / weight / curated MD+JSON under `logs/mobile-doctor/`
  - Keyword + AGENTS.md (`ezkey_mobile/` + root); HITL contract mirrored from Java doctor
  - Hygiene campaign folder (TEMPLATE + dated passes)
- **Out of scope (v1):**
  - Making findings a CI gate
  - Replacing ESLint/Prettier with Biome
  - Dependency CVE scanning as part of this pass
  - Auto-fixing every finding; methodology theatre for each polish item
  - iOS-specific rule packs

## Key assumptions

- Operator confirmed Go on shortlist + keyword (2026-07-11).
- Prefer **evolve** existing `quality-pipeline.mjs` / curator toward doctor shape over a
  parallel orphan script.
- Hygiene posture matches Java: briefing → small set → hygiene PR; campaign notes for decisions.
- Windows agents use Git Bash; Semgrep Docker must reuse Java doctor path lessons.

## Risks and exceptions

- react-doctor smoke shows volume on preference rules (`rn-prefer-pressable`) — curator must
  suppress / reweight early.
- Semgrep Windows/Docker friction — reuse slim workspace / `MSYS_NO_PATHCONV` patterns from Java.
- Over-broad Detekt packs will drown the curator; keep narrow + suppress with reasons.

## Candidate first slice (active TB)

[`TB-2026-07-11-mobile-doctor-curated-mvp`](../TB-2026-07-11-mobile-doctor-curated-mvp.md):

1. react-doctor + Semgrep + Detekt → curator MVP under `logs/mobile-doctor/`
2. Keyword + AGENTS contract + hygiene TEMPLATE
3. Drop Biome phantom; one dry-run campaign briefing (no obligation to fix everything)

## Promotion notes

- Moved to `ready` after operator Go on evaluation checklist (2026-07-11).
- TB created for curator MVP.

## Automation follow-up (optional)

- **Candidate:** Cursor keyword `mobile-doctor-curated` (AGENTS.md first; skill only if needed)
- **Trigger:** After curator script lands and one successful human/agent campaign

## Links

- Evaluation: [`../mobile-doctor-curated-evaluation-2026-07-11.md`](../mobile-doctor-curated-evaluation-2026-07-11.md)
- Tracer bullet: [`../TB-2026-07-11-mobile-doctor-curated-mvp.md`](../TB-2026-07-11-mobile-doctor-curated-mvp.md)
- Working plan: [`.cursor/plans/mobile_doctor_curated_hygiene.plan.md`](../../../.cursor/plans/mobile_doctor_curated_hygiene.plan.md)
- Comparables: Java doctor evaluation + `ezkey-admin-ui/scripts/doctor-curated.mjs`
- Hygiene vs program: `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`
