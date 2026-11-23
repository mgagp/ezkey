# Agent-surface review — step 1 inventory (2026-11-23)

**Status:** step 3 decisions recorded below. Framing: [`2026-11-23-cadrage.md`](2026-11-23-cadrage.md).
Baseline: main @ `ab18f6ff3`. Each row carries a **proposed** destination from the step-3 grid;
nothing is decided until the step-3 HITL walk.

Legend: **Root** keep in root `AGENTS.md` (condensed) · **Skill** · **Module** module `AGENTS.md`
· **Platform** Cursor-on-Windows / macOS / cloud file · **Hook** · **Pointer** one line + link ·
**Delete**.

## Structural finding: one mechanism for scoped guidance

Claude Code never loads glob-scoped `.mdc` rules, and Cursor never loads `.claude/rules/`. Both
load **module `AGENTS.md`** files when Claude gets a one-line `CLAUDE.md` with `@AGENTS.md` per
module (decision 3; lazy-loaded when a file in that module is touched). Several scoped rules
already duplicate their module guide (Flyway → `ezkey-core/AGENTS.md`, Android build →
`ezkey_mobile/AGENTS.md`). **Proposal:** module guides are the single home for module-scoped
guidance. Only rules that cut across modules (Java conventions) need a dual format: canonical
`.claude/rules/java.md` with `paths:`, plus a thin glob-scoped `.cursor/rules/java.mdc` pointer.

## A. Root `AGENTS.md` (785 lines, 57 KB)

| # | Section | Bytes | Duplicated in | Proposal |
|---|---|---|---|---|
| A1 | Cold-start context (tiers + pointer table) | 8 663 | `cold-start-context.mdc`, Copilot | **Root**, condensed. It is the compass; trim the cells and fold A4, A23 and the lifecycle / mobile / config paragraphs (A31–A33) into it. |
| A2 | Working conventions | 1 337 | — | **Root**. *Merge-ready* becomes the **Done** block. Test hosts and ngrok → **Pointer** (`LOCAL_STACK_PORTS.md`, team roles). |
| A3 | Terminology (phase vs milestone) | 310 | Copilot | **Root** (short, real mistake). |
| A4 | Where are we? | 694 | A1 pointer row | **Delete** (A1 row already says it). |
| A5 | Fresh-session bootstrap | 1 735 | `product-docs-workflow-bootstrap.mdc`, Copilot | **Root**, 3 lines: new idea → methodology README, Plan mode first, critical-review gate for P0/P1 and G1. |
| A6 | Methodology explorer ordering | 234 | — | **Module** (`product-docs/`): methodology README or a `product-docs/AGENTS.md`. |
| A7 | Methodology publication boundary | 2 922 | — | **Module** (`product-docs/`), with a 1-line root pointer. |
| A8 | Maven and formatting | 2 094 | `maven-build.mdc`, `shell-preferences.mdc`, Copilot, `DEVELOPMENT.md` | **Root Evidence block**: `./scripts/build.sh`, non-negotiable for Java. Steps and rationale stay in `DEVELOPMENT.md` / Java rule. |
| A9 | GitHub Actions CI | 640 | `DEVELOPMENT.md` | **Root Done block**, 1 line: `ci-gate` green. |
| A10 | Javadoc `@param` on types | 564 | `javadoc-type-param.mdc` | **Java rule** (Checkstyle enforces it; the rule saves a build loop). |
| A11 | `scanBasePackages` sync | 942 | — | **Module** (`ezkey-core/AGENTS.md`): real trap (startup failure, unit tests miss it). |
| A12 | Contract refresh and Bruno | 2 652 | `openapi-specs.mdc` | **Root Done block**, 2 lines (controller change ⇒ contract review + Bruno + spec refresh, never hand-edit specs). Procedure → `openapi-specs` rule / `bruno/README.md`. |
| A13 | Project values | 2 751 | `project-values-analysis.mdc`, Copilot (stale subset) | **Root Mission and compass**, single copy. |
| A14 | Admin UI browser validation | 1 139 | `ui-browser-test-autonomy.mdc`, A24 | **Module** (`ezkey-admin-ui/AGENTS.md`); root Evidence ladder points there. |
| A15 | `doctor-curated` | 1 346 | `ezkey-admin-ui/AGENTS.md`, Copilot | **Skill** (new, thin: points at admin-ui guide § React Doctor). |
| A16 | `java-doctor-curated` (+ HITL contract) | 4 096 | Copilot | **Skill** (new). The HITL contract moves verbatim into it. |
| A17 | `pam-doctor-curated` (+ HITL) | 2 818 | skill exists, Copilot | **Skill** (exists): check nothing is lost, then delete here. |
| A18 | `dependabot-curated` (+ major upgrades, HITL) | 6 557 | skill (578 lines), `cloud-gh-token.mdc`, Copilot | **Skill** (exists). *Major upgrades* → `major-upgrades/README.md` pointer. |
| A19 | `mobile-doctor-curated` | 1 140 | `ezkey_mobile/AGENTS.md`, Copilot | **Skill** (new, thin: points at mobile guide). |
| A20 | `security-pentest-curated` | 1 388 | skill exists, Copilot | **Skill** (exists). |
| A21 | `javamelody-curated` | 1 461 | — | **Skill** (new). |
| A22 | `quality-gate` | 381 | — | **Skill** (new, thin) or **Pointer** to its README. |
| A23 | `assessment-curated` (+ HITL) | 2 271 | skill exists, Copilot | **Skill** (exists). |
| A24 | UI test autonomy | 742 | `ui-browser-test-autonomy.mdc`, A14 | **Module** (admin-ui), merged with A14. |
| A25 | Manual exploratory test summaries | 832 | `manual-exploratory-test-summaries.mdc` | **Root Evidence block**, 1 line. |
| A26 | Lightweight hygiene workflow | 997 | methodology README | **Root**, 2 lines (hygiene vs program) + pointer. |
| A27 | GitHub issue labels | 2 097 | `github-issue-labels.mdc` | **Skill** (new, model-invocable: "create a GitHub issue"); root keeps 1 line. **Hook** candidate (labels checked after `gh issue create`), step 5. |
| A28 | Git commit on Windows | 2 232 | `git-commit-windows.mdc`, Copilot | **Platform** (Cursor/PowerShell only). Claude Code's Bash tool is already Git Bash. Delete from root. |
| A29 | Pull request on Windows | 1 362 | `git-pr-windows.mdc`, Copilot | **Platform**, same as A28. |
| A30 | Windows Bash selection | 775 | `shell-preferences.mdc` | **Platform**, same as A28. |
| A31 | (untitled) Lifecycle governance mandate | ≈ 900 | A1 pointer row | **Root**, folded into the A1 lifecycle row ("mandatory before design"). |
| A32 | (untitled) Mobile entry + Android build | ≈ 800 | A1 row, `ezkey-mobile-android-build.mdc`, mobile guide | **Delete** from root (A1 row + module guide). |
| A33 | (untitled) `CONFIGURATION.md` convention | ≈ 400 | — | **Root**, 1 line (not discoverable, real convention). |

**Projected root:** about 120–170 lines, under the 200-line soft guardrail.

## B. `.cursor/rules/` (23 files, 43.6 KB; 19 `alwaysApply`)

| Rule | Mode today | Proposal |
|---|---|---|
| `cold-start-context`, `product-docs-workflow-bootstrap`, `project-values-analysis`, `manual-exploratory-test-summaries` | always | **Delete** (decision 2; `AGENTS.md` is canonical). |
| `voice-dictation` | always | **Delete**; fold the project-term table (Caddy, Ezkey, Lightsail, Wispr) into one root line. Generic behaviour already lives in the maintainer's user-level instructions. |
| `github-issue-labels` | always | **Skill** (A27). |
| `cloud-gh-token` | always | **Delete**: Cursor Cloud + Dependabot only, already in the `dependabot-curated` skill § Cloud GitHub identities (verify). |
| `git-commit-windows`, `git-pr-windows`, `git-fast-forward-windows`, `shell-preferences`, `jdk-25-windows-workstation` | always | **Platform**: merge into one `windows-agent-shell.mdc`, agent-requested ("committing, opening a PR, running scripts from PowerShell on Windows"). They cost every Cursor session, Mac included. |
| `java-spring-cursor-rules` | always | **Delete**. Generic boilerplate with **wrong facts** for Ezkey (MyBatis, Java 21, Spring Boot 3.x, `@DataJpaTest`, BCrypt, WebFlux, caching). Keep its three real signals (constructor injection / no `@Autowired` without asking, unnamed `_`, complete Javadoc) in the Java rule. |
| `javadoc-type-param`, `java-unused-lambda-unnamed`, `mapstruct`, `dto-match-domain` | mixed (3 are always despite globs) | **Merge** into one Java rule: canonical `.claude/rules/java.md` (`paths: **/*.java`) + thin `.cursor/rules/java.mdc` glob pointer. `dto-match-domain`'s glob (`src/main/**`) never matches from the repo root; the rule's claim needs checking. `mapstruct`'s "silent failure" claim needs checking against the current MapStruct version. |
| `maven-build` | always | Scoped to Java/POM files (or folded into the Java rule); the root keeps `./scripts/build.sh`. |
| `openapi-specs` | always | Scoped to controllers, DTOs and `specs/**`; root keeps the 2-line rule (A12). |
| `ui-browser-test-autonomy` | always | **Module** (admin-ui guide; A14/A24). |
| `agent-ui-closeout-validation` | manual | Keep (operator-triggered recipe), or **Skill** later. |
| `ezkey-mobile-android-build` | glob | **Module** (mobile guide already holds it; verify). |
| `flyway-partitioned-tables` | glob | **Module** (`ezkey-core/AGENTS.md` § Partitioned tables already holds it; verify). |
| `commands/cr.md` (14 KB, marked SUPERSEDED) | — | **Delete** (git history is the archive; 2 references to update). |

## C. Skills (`.cursor/skills/`, 8 skills, 84 KB)

- All eight already set `disable-model-invocation: true`: no description cost in context. Operator
  invocation becomes `/name` in Claude Code; a keyword in prose works because the root table names
  the skill file.
- **Move** to `.claude/skills/` (decision 1). 31 files reference `.cursor/skills`.
- `dependabot-curated` is 578 lines, over the 500-line guidance: split reference material
  (tiers, Orval grid, pulses) into supporting files.
- `.cursor/skills/README.md` lists 6 of 8 (missing `pam-doctor-curated`, `corpus-ablation`).
- **New thin skills** (A15, A16, A19, A21, A22, A27): each points at its existing canon; no copied
  contract text, except A16's HITL contract, which has no other home.

## D. Copilot mirror (`.github/copilot-instructions.md`, 394 lines) — extraction

Signals **not present** elsewhere and worth keeping:

| Signal | Destination |
|---|---|
| `@since <current year>` on **new** types only; never rewrite existing `@since` (code uses 2025 and 2026) | Java rule |

Checked and **not** worth porting:

- License header: enforced by Checkstyle `RegexpHeader`.
- Conventional commits: in `CONTRIBUTING.md` / `DEVELOPMENT.md` and visible in `git log`.
- `logger` field naming, UTF-8: visible in the code / `.editorconfig`.
- No Lombok: not a dependency; the "stay within the stack" value covers it.
- Generic Spring/REST/security/performance advice: no Ezkey-specific signal.

**Stale or contradicting** content (confirms retirement): "use `@Autowired` for clarity" (the Java
rule says never without asking), Spring Boot 3.5.3 (actual 4.1.2), React Native 0.72 (actual
0.87), an old milestone list, "90% / 10%" (values say 80–20). 10 files reference the Copilot file.

## E. Claude-specific surface

- `CLAUDE.md`: imports the 6 rules (to drop). The macOS notes are fine as a platform block. Add one
  Windows line: the Bash tool is Git Bash, so the Cursor PowerShell commit/PR flow does not apply.
  The iOS worktree note is project state that can go stale; move it to `ezkey_mobile/AGENTS.md`
  in wave 2.
- `.claude/settings.json`: deny rules on force-push, push to main and `gh pr merge` match
  "merge only on the owner's go". Keep.
- `.claude/hooks/session-start.sh`: cloud-only, no-op elsewhere. Keep.
- `.cursor/plans/` (2 files): out of scope (corpus-ablation lane).

## Proposed wave split (each PR leaves main coherent)

1. **Root surface:** new root `AGENTS.md` (mission / rules / done / evidence), `CLAUDE.md` imports
   dropped, the 6 duplicate rules deleted, Windows rules merged, Java rules merged, `cr.md` and the
   Copilot file retired (with the `@since` signal ported), links fixed.
2. **Skills:** move to `.claude/skills/`, new thin skills, `dependabot-curated` split, references
   fixed. Wave 1 root table points at the skill paths, so wave 2 lands **before** wave 1 or wave 1
   keeps pointing at `.cursor/skills/` until wave 2 updates the table: to settle in step 3.
3. **Module guides:** trim, absorb module-scoped rules, add per-module `CLAUDE.md` `@AGENTS.md`.

## Step 3 decisions (HITL log)

| # | Item | Decision |
|---|---|---|
| 1 | Hygiene lanes → skills (A15–A23, A27; `cloud-gh-token`, `github-issue-labels`) | **Go as proposed.** Move 8 skills to `.claude/skills/` (+ 31 refs, skills README). New operator-only thin skills: `doctor-curated`, `mobile-doctor-curated`, `java-doctor-curated` (carries the HITL contract verbatim), `javamelody-curated`, `quality-gate`. New model-invocable `github-issue` skill; delete `github-issue-labels.mdc` and `cloud-gh-token.mdc`. Root keeps a keyword → skill table. **PR 1** (skills) lands before the root rewrite. Parked: `dependabot-curated` split (578 lines) in its own PR. |
| 2 | Cold-start compass + pointer table (A1, A3–A7, A13 placement, A26, A31–A33) | **Go as proposed.** Root opens with *Mission and compass* (3-line Ezkey identity + link, values once, fail-open and adopter posture inside values, "vision locks travel" on the methodology line). Tiers in 3 lines. Pointer table 27 → ~17 rows, one link each (skill rows → skills table, PR/contract rows → Done/Evidence, posture rows → values, self-referencing row dropped, Logging becomes a rule). A31 into the lifecycle row; A33 one rule line; A4 and A32 deleted; A3 kept; A6–A7 → `product-docs/GOVERNANCE.md` + 1-line pointer. |
| 3 | Done + Evidence blocks (A2 part, A8, A9, A12, A25, root part of A14/A24) | **Go as proposed.** *Done means*: PR + docs in same PR (decision trace), `ci-gate` green / no new alert / no open thread, API surface ⇒ Bruno + generated specs agree (never hand-edit; say pending), merge only on owner's go. *Evidence* table per touched surface (Java, API contract, Admin UI, mobile, docs-only) + manual test summary for workflows + "say what you did not run". No `Stop` hook. |
| 4 | Java and Windows rules | **Go as proposed.** One Java rule (canonical `.claude/rules/java.md`, `paths: **/*.java`; thin glob pointer `.cursor/rules/java.mdc`): constructor injection (`@Autowired` only where Spring cannot construct, e.g. JPA listener, else ask), Javadoc type vs record rule, `@since` current year on new types, unnamed `_`, `./scripts/build.sh` before targeted `-pl`. Delete `java-spring-cursor-rules`, `mapstruct` (MapStruct 1.6.3 converts enum↔String implicitly), `dto-match-domain` (false claim, dead glob), `javadoc-type-param`, `java-unused-lambda-unnamed`, `maven-build`. `openapi-specs` scoped (controllers, DTOs, `specs/**`), not always. Five Windows rules → one compact always-on `windows-agent-shell.mdc` (~40 lines, "PowerShell agent shells only"). One Windows line in `CLAUDE.md` (Bash tool is Git Bash). |
| 5 | Remaining duplicates and deletions | **Go as proposed.** Delete the 4 duplicate always rules (CLAUDE.md stops importing them). `voice-dictation` → one root line, rule deleted. A11, A14, A24, A32 leave the root (module guides cover them); complete `ezkey-core/AGENTS.md` with the three `Application` classes + startup-failure symptom. Delete `flyway-partitioned-tables`, `ezkey-mobile-android-build`, `ui-browser-test-autonomy` (module guides hold them; Claude reaches them via the pointer table until wave 3). A28–A30 leave the root. Keep `agent-ui-closeout-validation` (manual). Delete `commands/cr.md` (+2 refs) and `.github/copilot-instructions.md` after porting `@since` (fix live refs; leave ML-2026-07-19 as history). Keep the macOS block in `CLAUDE.md`; iOS worktree note → mobile guide in wave 3. |

## Delivery plan (after step 3)

1. **PR 1 — skills:** move to `.claude/skills/`, 5 thin operator skills + `github-issue` skill,
   delete `github-issue-labels` and `cloud-gh-token` rules, fix references, skills README; lane
   README + these notes + hygiene index entry.
2. **PR 2 — root surface:** new root `AGENTS.md` (mission and compass / rules / done / evidence /
   skills table / pointers), `CLAUDE.md` imports dropped + Windows line, rules from items 4–5,
   `cr.md` and Copilot retired, `GOVERNANCE.md` and `ezkey-core/AGENTS.md` receive their moves.
   Evidence: before/after measurement, link check, `/doctor` (operator).
3. **PR 3 — `dependabot-curated` split** (parked).
4. **PR 4+ — module guides** (wave 3) with per-module `CLAUDE.md` `@AGENTS.md`.
5. **Step 5 (hooks):** only candidate is a label check after `gh issue create`; decide after PR 2.
6. **Step 8 (validation):** cold-agent A/B after PR 2, then `critical-review`.
