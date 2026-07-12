# Tracer Bullet Brief — `TB-2026-07-11` Java doctor-curated curator MVP

## Metadata

- **ID:** `TB-2026-07-11-java-doctor-curated-mvp`
- **Status:** `active` (MVP implemented; dry-run recorded 2026-07-11)
- **Related idea:** `I-2026-07-11-java-doctor-curated-hygiene`
- **Lane:** `C` (tooling / continuous-improvement capability; hygiene posture for later campaigns)
- **Posture:** `single-pass`
- **GitHub issue:** `#326`
- **Issue labels:** `lane:c`, `type:chore`, `component:core`, `component:admin-api`, `component:auth-api`, `component:integration-api`, `component:infra`, `priority:p3`, `status:ready`
- **Created at:** `2026-07-11`
- **Updated at:** `2026-07-11`
- **Captured by:** Marc (Go on evaluation shortlist; handoff implementation)

## Objective

Ship a **punctual, curated Java static-analysis pass** mirroring Admin UI `doctor-curated`:

1. Run SpotBugs + narrow PMD (Maven report-only) + Semgrep (pinned small pack, JSON)
2. Curate into P1/P2/P3 under `logs/java-doctor/`
3. Document keyword `java-doctor-curated` in root `AGENTS.md`

Not a CI / `scripts/build.sh` fail gate.

## Posture

`single-pass` — one vertical slice: analyzers wired + curator + keyword + one dry-run
campaign briefing. Later polish campaigns reuse the script without new methodology theatre.

## Boundaries in scope

- Parent POM: upgrade SpotBugs pins; add SpotBugs + PMD plugins (report-only, no lifecycle fail)
- `config/java-doctor/` — narrow PMD ruleset, Semgrep pack, optional SpotBugs exclude stub
- `scripts/java-doctor-curated.sh` + `scripts/java-doctor-curated.mjs`
- Default modules: `ezkey-core`, `ezkey-core-security`, `ezkey-admin-api`,
  `ezkey-auth-api`, `ezkey-integration-api`
- Root `AGENTS.md` keyword section; brief `scripts/README.md` pointe
- Windows Git Bash: `MSYS_NO_PATHCONV=1` for Semgrep Docker mounts
- One dry-run: produce curated report; brief findings (no obligation to fix all)

## Out of scope

- Binding findings into `scripts/build.sh` or CI fail-on-findings
- SonarQube, Error Prone, FindSecBugs, full PMD/Semgrep catalogues
- Dependency CVE / SCA in this pass
- Auto-fixing every finding; per-finding `TB-*` for routine polish
- Demo apps / `ezkey-tests` in default scope

## First executable slice

1. Mark evaluation Go checklist; promote `I-*` to `ready`; open GitHub issue
2. Wire Maven report-only SpotBugs (≥ 4.10.x) + narrow PMD
3. Pin Semgrep config under `config/java-doctor/`
4. Bash entrypoint → raw reports → Node curator → `logs/java-doctor/*.curated.*`
5. Keyword + AGENTS contract (brief → small fix set → hygiene PR; modest low-signal allotment)
6. Dry-run campaign; record smoke evidence on this TB

## Critical flows

- **Nominal:** From repo root in Git Bash, `./scripts/java-doctor-curated.sh` compiles target
  modules if needed, runs three analyzers, emits curated MD/JSON, exits 0 even when findings exist
- **Exception:** Semgrep unavailable (no binary / no Docker) → fail with clear message; SpotBugs
  without compiled classes → script compiles first or fails with actionable hint

## Evidence plan

| Layer | Required |
| --- | --- |
| Script smoke | Curated MD+JSON under `logs/java-doctor/` after one run |
| Maven | SpotBugs/PMD goals succeed with `failOnError` / `failOnViolation` false |
| Docs | `I-*` ready + linked; evaluation Go checked; `AGENTS.md` keyword |
| Non-goal | Fixing all findings in this TB |

## Exit criteria

1. Keyword `java-doctor-curated` documented and runnable via Bash entrypoint
2. Three analyzer families produce inputs the curator can normalize
3. Curated report includes suppress map (even if initially empty/stub) and P1/P2/P3 sections
4. Dry-run evidence noted on this TB (counts / path to report)
5. `I-*` links this TB; optional GitHub issue labeled

## Dry-run evidence (2026-07-11)

Command: `./scripts/java-doctor-curated.sh --skip-compile` (Git Bash / Windows).

| Signal | Result |
|--------|--------|
| SpotBugs | XML collected for all 5 default modules (engine 4.10.2) |
| PMD | Narrow ruleset OK after PMD 7 `NcssCount` swap (was `ExcessiveMethodLength`) |
| Semgrep | Docker `semgrep/semgrep:1.168.0`; slim `logs/java-doctor/scan-workspace` mount + `cygpath -w` + `MSYS_NO_PATHCONV` confined to Docker subshell; **0** findings on pinned pack |
| Curator | Raw **440** → curated **173** after suppressing `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` (**267**); MapStruct `*MapperImpl` excluded in SpotBugs filter; outputs `logs/java-doctor/java-doctor.curated.md\|json` |
| Windows lessons | Full-repo Docker mounts hang; MSYS `/tmp` mounts invisible; do not leave `MSYS_NO_PATHCONV=1` exported for Node |

Sample high-signal shortlist from dry-run (not auto-fixed in this TB): SpotBugs `RCN_*`, `ST_WRITE_TO_STATIC_*`, `DMI_RANDOM_USED_ONLY_ONCE`, `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`; PMD `EmptyCatchBlock`, complexity rules.

## Links

- Idea: [`ideas/I-2026-07-11-java-doctor-curated-hygiene.md`](ideas/I-2026-07-11-java-doctor-curated-hygiene.md)
- Evaluation: [`../java-doctor-curated-evaluation-2026-07-11.md`](../java-doctor-curated-evaluation-2026-07-11.md)
- Comparable: `ezkey-admin-ui/scripts/doctor-curated.mjs`
