# Tracer Bullet Brief — `TB-2026-07-11` Mobile doctor-curated curator MVP

## Metadata

- **ID:** `TB-2026-07-11-mobile-doctor-curated-mvp`
- **Status:** `done` (MVP implemented; dry-run recorded 2026-07-12; exit criteria met)
- **Related idea:** `I-2026-07-11-mobile-doctor-curated-hygiene`
- **Lane:** `C` (tooling / continuous-improvement capability; hygiene posture for later campaigns)
- **Posture:** `single-pass`
- **GitHub issue:** `#235` (closed 2026-08-25 as completed; this TB is the successor MVP)
- **Created at:** `2026-07-11`
- **Updated at:** `2026-08-25`
- **Captured by:** Marc (Go on evaluation shortlist; handoff implementation)

## Objective

Ship a **punctual, curated mobile static-analysis pass** mirroring Admin UI `doctor-curated` and
Java `java-doctor-curated`:

1. Run react-doctor + Semgrep (pinned Ezkey mobile pack) + Detekt (narrow)
2. Curate into P1/P2/P3 under `ezkey_mobile/logs/mobile-doctor/` (or repo `logs/mobile-doctor/` if
   preferred — document the chosen path in AGENTS)
3. Document keyword `mobile-doctor-curated` in `ezkey_mobile/AGENTS.md` + root `AGENTS.md`
4. Campaign notes folder: `product-docs/global/hygiene/mobile-doctor/`

Not a CI / `yarn validate:ci` fail gate.

## Posture

`single-pass` — one vertical slice: analyzers wired + curator + keyword + one dry-run
campaign briefing. Later polish campaigns reuse the script without new methodology theatre.

## Boundaries in scope

- Evolve `ezkey_mobile/scripts/quality-pipeline.mjs` + `code-quality-curator.mjs` toward doctor
  shape (prefer evolve over orphan parallel script)
- Add react-doctor invoke (template: `ezkey-admin-ui/scripts/doctor-curated.mjs`)
- Keep Semgrep + Detekt runners; **remove Biome** from default expected inputs/docs
- Suppressions file with written reasons (commit under `ezkey_mobile/` or `config/mobile-doctor/`)
- Keyword + HITL contract (mirror Java: one finding at a time; campaign note after decisions)
- Windows Git Bash: Semgrep Docker path lessons from java-doctor
- One dry-run: produce curated report; brief findings (no obligation to fix all)

## Out of scope

- Binding findings into CI fail-on-findings
- Biome migration; SonarQube; Android Lint-in-curator; Knip as third analyzer
- Dependency CVE / SCA in this pass
- Auto-fixing every finding; per-finding `TB-*` for routine polish
- iOS rule packs

## First executable slice

1. Mark evaluation Go checklist; promote `I-*` to `ready` _(done with this TB creation)_
2. Wire react-doctor into pipeline; drop Biome phantom
3. Curator MVP: suppress map + P1/P2/P3 + curated MD/JSON under `logs/mobile-doctor/`
4. Keyword + AGENTS contract (mobile + root)
5. Hygiene TEMPLATE already seeded; dry-run campaign; record smoke evidence on this TB

## Critical flows

- **Nominal:** From `ezkey_mobile/` in Git Bash, run the doctor entrypoint; three analyzers emit
  raw reports; curator emits curated MD/JSON; exit 0 even when findings exist
- **Exception:** Semgrep unavailable (no binary / no Docker) → fail with clear message;
  react-doctor/`npx` failure → actionable stderr path

## Evidence plan

| Layer | Required |
| --- | --- |
| Script smoke | Curated MD+JSON under `logs/mobile-doctor/` after one run |
| react-doctor | Evaluation smoke already: **0.7.5**, 38 warnings, RN detected (2026-07-11) |
| Docs | `I-*` ready + linked; evaluation Go checked; AGENTS keyword |
| Non-goal | Fixing all findings in this TB |

## Exit criteria

1. Keyword `mobile-doctor-curated` documented and runnable via entrypoint
2. Three analyzer families produce inputs the curator can normalize
3. Curated report includes suppress map and P1/P2/P3 sections
4. Biome no longer advertised as a default pipeline input
5. Dry-run evidence noted on this TB (counts / path to report)
6. `I-*` links this TB

## Dry-run evidence (2026-07-12)

Command (from `ezkey_mobile/`):

```bash
yarn doctor:curated
```

| Signal | Result |
|--------|--------|
| react-doctor | Raw **38** diagnostics (same ballpark as evaluation smoke 0.7.5) |
| Semgrep | Local CLI; Ezkey pack **10** rules; **0** findings on app + Kotlin main |
| Detekt | CLI **1.23.8** downloaded to `.monitor/tools/`; SARIF written; Kotlin findings present |
| Curator | Raw **58** → curated **42** after **16** suppressions (`unused-*`, `rn-prefer-pressable`) |
| Outputs | `logs/mobile-doctor/mobile-doctor.curated.md\|json` + `raw/` |
| Sample P1 | `async-await-in-loop`, `exhaustive-deps`, `rn-no-metro-babel-runtime-version` |

### Delivered artifacts

- `ezkey_mobile/scripts/mobile-doctor-curated.mjs` + `.sh`
- `ezkey_mobile/config/mobile-doctor/suppressions.json`
- `yarn doctor:curated` (and `quality:pipeline` alias)
- Keyword sections: `ezkey_mobile/AGENTS.md`, root `AGENTS.md`
- Hygiene TEMPLATE command filled

## Links

- Idea: [`ideas/I-2026-07-11-mobile-doctor-curated-hygiene.md`](ideas/I-2026-07-11-mobile-doctor-curated-hygiene.md)
- Evaluation: [`../mobile-doctor-curated-evaluation-2026-07-11.md`](../mobile-doctor-curated-evaluation-2026-07-11.md)
- Hygiene notes: [`../hygiene/mobile-doctor/README.md`](../hygiene/mobile-doctor/README.md)
- Comparables: `TB-2026-07-11-java-doctor-curated-mvp`, `ezkey-admin-ui/scripts/doctor-curated.mjs`
- Deleted Cursor plan (materialized); see evaluation and linked I/TB above
