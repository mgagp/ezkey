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

### 2026-05-29 — step-3 functional PASS (operator)

- **Worked well:** Manual enroll + auth on device after RN 0.85.3; `EZKEY_DIAG_TEMP` visible in logcat — diag strip validated before harder bumps.
- **Next:** step-5 Async Storage 3.x (S08 retry with Kotlin 2.1.20).

### 2026-05-29 — step-5 Async Storage 3.x (agent)

- **Context:** Operator confirmed step-3 smoke + diag logcat; proceed with storage bump.
- **Worked well:** S08 root cause = missing `local_repo` Maven in `android/build.gradle`, not Kotlin alone; official Jest mock at `./jest` export.
- **Friction:** v3 Jest mock ships as ESM — required adding `@react-native-async-storage/async-storage` to `transformIgnorePatterns`.
- **Gates:** `yarn validate:ci` PASS; licenses regenerated for 3.0.2.
- **Next:** Operator `installDebug` + storage-focused smoke; then Vision Camera 5 spike (step-6).

### 2026-05-29 — step-5 functional PASS (operator)

- **Worked well:** Storage smoke on Pixel after `installDebug` (langue, préférences, auth) — no regression vs step-3.
- **Next:** step-6 Vision Camera 5.x spike (Nitro, frame processor, QR + `EZKEY_DIAG_TEMP`).

### 2026-05-29 — step-6 Vision Camera 5 spike (agent)

- **Approach:** Avoid custom Nitro ML Kit plugin for spike — use VC5 built-in `useObjectOutput` for QR; drops ~90 lines Kotlin + direct ML Kit app dependency.
- **Friction:** `react-native-nitro-modules` codegen JNI missing until `newArchEnabled=true` re-added for third-party Gradle gates; cleared stale `app/.cxx`.
- **Removed:** `EzkeyQrFrameProcessorPlugin`, worklets-core, babel worklets-core plugin, Android RNWorklets CMake stub.
- **Gates:** `yarn validate:ci` PASS; `installDebug` Pixel — operator QR smoke pending.

### 2026-05-29 — step-6 functional PASS (operator)

- **Lesson:** `useObjectOutput` is iOS-only; Android-first apps need `react-native-vision-camera-barcode-scanner` (ML Kit) or a custom Nitro frame plugin.
- **Worked well:** Official barcode output restored enrollment QR without reviving custom Kotlin plugin.
- **Next:** step-4 ESLint 9 (optional parallel), strip `EZKEY_DIAG_TEMP`, step-8 Lane E retro.

### 2026-05-31 — closeout (diag strip + ESLint 9 + Lane E)

- Removed `EZKEY_DIAG_TEMP` strip, `capture-stack-migration-logcat.sh`, env flag.
- ESLint 9 flat config; `PendingAuthScreen` `no-void` fix.
- TB/I → `done`; `MOBILE_STACK_AND_ARCHITECTURE.md` updated.

### 2026-05-31 — post-merge closeout (operator + agent)

- **Context:** PR **#177** merged to `main`; operator re-tested all app screens — functional **PASS**.
- **Worked well:** Two-commit post-ladder (runtime prudent line, then Orval 8.14); canonical Android build script eliminated repeated JDK 25 / wrong JBR path failures on Windows agents.
- **Friction:** Agents defaulted to repo JDK 25 and documented `Android Studio\jbr` path; actual workstation uses `Android Studio1\jbr`. Long Gradle without prior `adb devices` wasted install step when wireless adb dropped.
- **Promoted:** `product-docs/methodology/decisions/2026-05-31-mobile-android-build-jdk-resolution.md`; `ezkey_mobile/scripts/build-install-debug-clean.sh`; `.cursor/rules/ezkey-mobile-android-build.mdc`.
- **Deferred:** Maestro enrollment path; gesture-handler 3; iOS Podfile.lock.

---

## Retrospective synthesis (Step 8)

### Questions (R1–R7)

| # | Question | Answer |
|---|----------|--------|
| R1 | Plan incubation → I/TB/branch — smooth? | **Yes.** Issue `#177`, branch naming, I/TB/ML materialization aligned with Admin UI Orval pattern. Archived May review prevented re-running S01–S23. |
| R2 | Scope creep visible early? | **Yes, contained.** “Simple bumps” reframed to residual majors (VC5, AS3, CI). Maestro deferred explicitly when operator clarified pilot limits. |
| R3 | Multi-pass PRs + deferred index — issues? | **Low friction.** Single branch with logical steps; operator-owned push. TB ladder still useful as merge narrative. |
| R4 | Which gates caught real regressions? | **`yarn validate:ci`** (Jest ESM, ESLint). **`installDebug`/device** caught `useObjectOutput` Android crash (Error Boundary). Operator PASS gates essential for storage + final barcode path. |
| R5 | Missing agent/human instructions? | **`newArchEnabled` for Nitro codegen** not obvious on RN 0.82+. VC5 platform tags (`@platform iOS`) should be checked before choosing QR API. Windows: Git Bash + `JAVA_HOME` + commit wrapper quirks. |
| R6 | Reusable ML log template for toolchain TBs? | **Yes** — this ML format (context / worked / friction / gates / next) fits multi-step native upgrades. |
| R7 | Feed `I-2026-0016` methodology hygiene? | **Partial** — defer Maestro until harness covers enrollment; toolchain TBs should list platform-specific API checks in TB functional section. |

### Decisions to promote (0..n)

| Decision slug | Status |
|---------------|--------|
| `mobile-vc5-qr-use-barcode-scanner-package` | **Documented** — `ezkey_mobile/README.md`, TB step-6 notes |
| `rn-nitro-codegen-newArchEnabled-flag` | **Documented** — `gradle.properties`, TB step-6 |
| `mobile-android-build-jdk-resolution` | **Promoted** — methodology decision 2026-05-31; scripts + AGENTS + Cursor rule |

### Corpus updates (0..n)

| File | Status |
|------|--------|
| `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md` | Updated stack + program status |
| `ezkey_mobile/README.md` | QR path + canonical build script |
| `ezkey_mobile/AGENTS.md` | Android debug build agent workflow |
| `product-docs/.../TB-2026-05-29-...` | `done`; post-merge closeout |
| `product-docs/global/backlog/index.md` | Recently completed entry on `main` |

## Final closeout (2026-05-31)

- **Status:** `I-*` / `TB-*` → `done`; issue **#177** merged.
- **Evidence:** CI green; `validate:ci` 172/172; Pixel install + operator full-app smoke PASS.
- **Next actions:** `TB-2026-0002` Maestro enrollment extension (optional); iOS lockfile pass; Play release track when ready.
