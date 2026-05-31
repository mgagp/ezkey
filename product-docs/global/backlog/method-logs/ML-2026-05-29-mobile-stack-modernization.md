# Method Log — `ML-2026-05-29` Mobile stack modernization (Lane E)

## Metadata

- **ID:** `ML-2026-05-29-mobile-stack-modernization`
- **Related idea:** `I-2026-05-29-mobile-stack-modernization`
- **Related tracer bullet:** `TB-2026-05-29-mobile-stack-modernization`
- **GitHub issue:** `#177`
- **Git branch:** `feature/177-i-2026-05-29-mobile-stack-modernization`
- **Incubation source:** `.cursor/plans/mobile_stack_upgrade_a92a7f09.plan.md`
- **Purpose:** Running, honest notes during execution — input for Step 3 methodology retrospective.
- **Rule:** Append-only entries during work; canonize into `methodology/decisions/` only at retro.

---

## How to use this log

At the end of each significant session or when merging a TB ladder PR, add a dated section below using:

- **Context** — planned vs actual
- **Worked well** — process, artifacts, gates
- **Friction** — ambiguity, tooling, agent/human handoff
- **Scope drift** — if any; how detected
- **Verbatim signal** (optional) — short operator quote if wording matters
- **Methodology hint** — candidate rule/skill/template change (**do not implement here**)

---

## Entries

### 2026-05-29 — step-0-methodology (bootstrap)

- **Context:** Operator ready to start work; requested GitHub issue, branch, and methodology artifacts. Scope evolved from “simple component upgrade” to Track A (Vision + Async Storage) + Track B (CI validate) after directed planning questions.
- **Worked well:** Plan incubation produced a clear three-step model (incubate → execute → Lane E retro). Parallel to Admin UI Orval pattern: issue number in branch name (`feature/177-...`).
- **Friction:** `gh` not on default PowerShell PATH — used `C:\Program Files\GitHub CLI\gh.exe`. Remote repo is `mgagp/ezkey`, not `ezkey-org/ezkey`.
- **Scope drift:** Initial expectation was routine bumps; evaluation showed S01–S23 already done — program reframed as **residual majors + CI**, not full dependency sweep.
- **Methodology hint:** Consider documenting `gh` path / repo slug in operator notes or `AGENTS.md` for Windows agents.

### 2026-05-29 — steps 1–3 + TEMP diag + first debug install (Pixel 7 Pro)

- **Context:** Operator asked to stay on branch until full upgrade + tests; functional validation preferred; camera upgrade deferred step-by-step; Pixel on adb (wireless).
- **Worked well:** `adb uninstall` then `gradlew installDebug` after clearing invalid shell `JAVA_HOME`; RN **0.85.3** + CI workflow + `validate:ci` green locally.
- **Friction:** PowerShell had stale `JAVA_HOME` → Android Studio `jbr` without `bin/java.exe`; Gradle install failed until env cleared. `gh` not on PATH (use full path to GitHub CLI).
- **TEMP strip:** `EZKEY_DIAG_TEMP` in Kotlin (`MainApplication`, `EzkeyQrFrameProcessorPlugin`) and JS (`tempStackMigrationDiag.ts`); `EZKEY_STACK_MIGRATION_DIAG=true` in local `.env` (not committed).
- **Functional:** Release build removed; debug **installed** on Pixel 7 Pro — manual smoke: app launch, optional enrollment QR to validate diag lines.
- **Next:** Operator QR smoke; then step-5 Async Storage 3.x before Vision Camera 5 spike.

### 2026-05-29 — Maestro deferred (operator guidance)

- **Context:** Maestro pilot only automates pending/respond with a **pre-existing** enrollment id; enrollment/QR is not in the harness yet.
- **Decision:** No Maestro effort on branch `#177` until a separate session extends automation. Functional validation = **manual** on Pixel (operator testing now).
- **Push:** Operator handles `git push`; agents commit only.

---

## Retrospective synthesis (Step 8 — fill after execution)

_To be completed in a dedicated Lane E session._

### Questions (R1–R7)

| # | Question | Answer |
|---|----------|--------|
| R1 | Plan incubation → I/TB/branch — smooth? | |
| R2 | Scope creep visible early? | |
| R3 | Multi-pass PRs + deferred index — issues? | |
| R4 | Which gates caught real regressions? | |
| R5 | Missing agent/human instructions? | |
| R6 | Reusable ML log template for toolchain TBs? | |
| R7 | Feed `I-2026-0016` methodology hygiene? | |

### Decisions to promote (0..n)

| Decision slug | Status |
|---------------|--------|
| _(none yet)_ | |

### Corpus updates (0..n)

| File | Status |
|------|--------|
| _(none yet)_ | |
