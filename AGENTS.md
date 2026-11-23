# Ezkey — Agent notes (repo-wide)

For agents working anywhere in the repo. For module-specific conventions and patterns, see the `AGENTS.md` in each module (e.g. `ezkey-admin-ui/AGENTS.md`, `ezkey-admin-api/AGENTS.md`, `sites/ezkey-org/AGENTS.md` for the public static site and Cloudflare Pages context).

## Cold-start context (tiered)

Do **not** ritual-read full `docs/ENDPOINT.md`, root `PRD.md`, or `docs/PROJECT_POSITIONING.md` at session start. Always-applied Cursor rules already carry most operating contracts. Load product and domain docs **by tier**:

| Tier | When | What to load |
|------|------|--------------|
| **0 — Compass** | Every session | This file + always-applied rules. Use the domain pointer table below. No bulk file ritual. |
| **1 — Product framing** | Identity, scope, open-ended work, or "what is Ezkey" | [`product-docs/global/product-intent.md`](product-docs/global/product-intent.md). Optionally skim [`README.md`](README.md) § What Exists Today. |
| **2 — Domain on demand** | After work direction is clear | Section-scoped reads only (see pointer table). |

Root [`PRD.md`](PRD.md) is a **stub** that points at `product-intent.md` (canon per [`product-docs/GOVERNANCE.md`](product-docs/GOVERNANCE.md)).

### Domain pointer table

| Need | Read |
|------|------|
| Fresh clone / first host Java build (empty `~/.m2`, Windows or macOS/Linux) | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) § *First clone on a new workstation*; entrypoint `./scripts/build.sh` (`--diagnose-only` if JDK/Maven look wrong). Do not start with bare `mvn checkstyle:check`. |
| Node.js / Yarn for Admin UI, mobile, SDK | Root [`.nvmrc`](.nvmrc) is the single source of truth (**24** LTS). Mobile CI uses `node-version-file: '.nvmrc'`. **nvm-windows** does not read `.nvmrc` automatically — run `nvm install 24` then `nvm use 24`, then `corepack enable` for Yarn 4. Details: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) prerequisites. |
| What runs on PRs / required check | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) § Continuous Integration; workflow `.github/workflows/ci.yml`; required check **`ci-gate`**. Evaluator inventory / phased plan: [`docs/engineering/ci-cd-best-practices-plan.md`](docs/engineering/ci-cd-best-practices-plan.md). |
| Product intent, thesis, audience | [`product-docs/global/product-intent.md`](product-docs/global/product-intent.md) |
| Priority / what to implement next | [`product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md`](product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md), [`product-docs/global/backlog/index.md`](product-docs/global/backlog/index.md) (historical Waves A–C + freeze D: [`product-docs/global/operational-readiness-prioritization-2026-09.md`](product-docs/global/operational-readiness-prioritization-2026-09.md)) |
| Protocol crypto (Auth API ↔ mobile / Demo Device) | [`docs/CRYPTO.md`](docs/CRYPTO.md), [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md); relevant **section** of [`docs/ENDPOINT.md`](docs/ENDPOINT.md) only |
| Challenge-code direction / MFA fatigue / number matching | [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) § Challenge-code direction (Decision #960); inverted mode parked: [`I-2026-10-21-inverted-challenge-code-mode`](product-docs/global/backlog/ideas/I-2026-10-21-inverted-challenge-code-mode.md) |
| At-rest encryption / Tink keyset storage | [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md), [`ezkey-core/CONFIGURATION.md`](ezkey-core/CONFIGURATION.md) § Encryption at Rest, [`product-docs/global/architecture-decisions.md`](product-docs/global/architecture-decisions.md) ADR-0008 + ADR-0011 |
| Logging / log-injection hardening | Caller-controlled value → `org.ezkey.util.LogSanitizer.sanitizeForLog`; secrets never logged at any level; prove on runtime output (log-capture test). Mandate and SLAs: [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) § Code scanning policy. Production wrap backlog: [#801](https://github.com/mgagp/ezkey/issues/801). |
| Normative posture | [`product-docs/global/normative-posture.md`](product-docs/global/normative-posture.md) — operational discipline; mapping vocabulary lives only there. Honest claims: [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) |
| Enrollment bind session (scan and challenge form one sequence; delivery modes; abandoned bind) | [`ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md`](ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) § *Product intent: one bind session* |
| Lifecycle / eligibility / multi-entity operator design | Skim [`product-docs/global/lifecycle-model.md`](product-docs/global/lifecycle-model.md), then [`docs/LIFECYCLE_GOVERNANCE.md`](docs/LIFECYCLE_GOVERNANCE.md) when designing |
| Admin UI conventions | [`ezkey-admin-ui/AGENTS.md`](ezkey-admin-ui/AGENTS.md) |
| Adding or changing an Admin UI list, record page, or link between them (dialog vs page, prev/next, audit-log context link) | [`product-docs/components/admin-ui/screens-and-wireflow.md`](product-docs/components/admin-ui/screens-and-wireflow.md) § Record anatomy |
| Mobile-primary work | [`ezkey_mobile/AGENTS.md`](ezkey_mobile/AGENTS.md), [`ezkey_mobile/docs/README.md`](ezkey_mobile/docs/README.md) |
| Experimental PAM (host sshd lab) | `ezkey-pam` is **experimental** (personal lab; loaded in host root sshd/login, not containerised) — see [`ezkey-pam/README.md`](ezkey-pam/README.md) (line 3). Hardening backlog: [`I-2026-10-13-pam-tmp-log-hardening`](product-docs/global/backlog/ideas/I-2026-10-13-pam-tmp-log-hardening.md). |
| Dismissing or accepting a dependency vulnerability (`tolerable_risk`, risk acceptance, security evaluation) | Check upstream first: [`product-docs/global/hygiene/dependabot/README.md`](product-docs/global/hygiene/dependabot/README.md) § *Check upstream before dismissing a vulnerability* |
| Answering reports on time (14-day promise, `security@ezkey.org`, issue reminder) | [`product-docs/global/hygiene/report-intake/README.md`](product-docs/global/hygiene/report-intake/README.md) |
| Mandate-driven hygiene assessment (HITL + handoff) | Keyword **`assessment-curated`** → [`product-docs/global/hygiene/assessment-curated/README.md`](product-docs/global/hygiene/assessment-curated/README.md) |
| Legacy document hygiene (canonicality / discoverability / archive-vs-delete) | Keyword **`document-hygiene-curated`** → [`product-docs/global/hygiene/document-hygiene/README.md`](product-docs/global/hygiene/document-hygiene/README.md) |
| New idea / method | [`product-docs/methodology/README.md`](product-docs/methodology/README.md) — the whole method fits in that one document since the 2026-08 ablation |
| Plan review / cold-agent hardening (aliases: `critical-review`, adversarial review, revue critique, plan hardening Stage 2) | Canon [`product-docs/global/hygiene/critical-review/README.md`](product-docs/global/hygiene/critical-review/README.md); prompt [`product-docs/templates/critical-review.prompt.md`](product-docs/templates/critical-review.prompt.md); skill [`.claude/skills/critical-review/SKILL.md`](.claude/skills/critical-review/SKILL.md); execution-hardening angle [`product-docs/templates/cold-agent-plan-review.prompt.md`](product-docs/templates/cold-agent-plan-review.prompt.md); pattern [`product-docs/methodology/README.md`](product-docs/methodology/README.md) § two-stage |
| Adopter posture / 3-second test | [`product-docs/global/operator-alignment-guide.md`](product-docs/global/operator-alignment-guide.md) — Ezkey is never the adopter's core business; every human surface must stay simple, fluid, operationally clear |
| Startup or HA behaviour, locks with transactions, removing an exposed field, test helpers, a CI failure that looks random | [`product-docs/global/hygiene/stabilization-lessons/README.md`](product-docs/global/hygiene/stabilization-lessons/README.md) — rules, evidence and checks from the #1180 / #1259 stabilization |
| Fail-open vs fail-closed | [`product-docs/global/design-principles.md`](product-docs/global/design-principles.md) §17 — name whether failure continues (observable) or stops at critical boundaries |
| API contract change | Controller + **section** of `docs/ENDPOINT.md` + OpenAPI refresh workflow (see OpenAPI rules) |
| Strategic positioning / public copy | [`docs/PROJECT_POSITIONING.md`](docs/PROJECT_POSITIONING.md) only when that layer is in scope |
| Public alpha label / deploy “what is live” | [`docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md`](docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md); community ledger [`docs/lightsail/community/DEPLOYED.md`](docs/lightsail/community/DEPLOYED.md) |
| Working conventions (merge-ready, test hosts, ngrok, decision trace) | This file § *Working conventions*; team role one-liners: [`product-docs/global/team-roles.md`](product-docs/global/team-roles.md) |

## Working conventions

- **Repo is the memory.** Any decision that changes behavior, a contract, a constraint, or how we work lands in the owning doc in the same PR (one line + reason + issue #). Chat-only or mandate-only counts as unrecorded. A missing trace is a review nit, not a merge blocker.
- **Merge-ready:** all checks green (including CodeQL), zero *new* unresolved code-scanning alerts introduced by the PR; the existing stock follows [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) § Code scanning policy; zero unresolved review threads, complete PR description, related docs updated. Merge only on the owner's explicit go — never on CI green alone.
- **Code via PRs.** Local test hosts build and test only; they do not author product code changes.
- **Local test hosts:** **Rootbeer** (Windows) runs a single Docker clean-start stack with fixed ports by design (see [`docs/LOCAL_STACK_PORTS.md`](docs/LOCAL_STACK_PORTS.md)). **Marc's Mac** checkout is `~/w/ezkey-main`. Isolation for parallel work comes from cloud agents or the Mac, not a second Rootbeer stack.
- **ngrok:** one session at a time. Switch by stopping on one host and starting on the other with the static domain.
- **Roles:** named reviewers and operators are defined in [`product-docs/global/team-roles.md`](product-docs/global/team-roles.md).

## Terminology guardrail (phase vs milestone)

- Use **phase** only for the product-docs methodology workflow phases.
- Use **milestone** for product-level progression, roadmap, and release-orientation statements.
- When reporting current product progress, answer with milestone language, not phase language.

## Where are we? (release priority compass)

When the question is **what to implement next** or **current release priority**, read first:

- [`product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md`](product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md)
  — live public alpha posture closeout (lab · discreet · alpha ≠ prod · no SLA; no P0 backlog)
- [`product-docs/global/backlog/index.md`](product-docs/global/backlog/index.md) § Current prioritization anchor
- Historical Waves A–C + freeze D only:
  [`product-docs/global/operational-readiness-prioritization-2026-09.md`](product-docs/global/operational-readiness-prioritization-2026-09.md)

## Fresh-session workflow bootstrap (product-docs method)

When a new session starts and the user is bringing a **new idea**, read
[`product-docs/methodology/README.md`](product-docs/methodology/README.md). The 2026-08 ablation
condensed the entire method — documentary levels (`V-*`/`I-*`/`TB-*`/`ADR-*`), bidirectional
discoverability, the values compass, and the three rules worth keeping (hygiene vs. program,
ephemeral scaffold vs. retained plan, closed uncertainty stays closed) — into that single document.
There is no separate lane taxonomy or skill sequence to load first: prefer **Plan mode** for
research and comparing alternatives, then position the idea as a `V-*`, `I-*`, or `TB-*` in
`product-docs/global/` only when the methodology README's artifact-choice table calls for it.

**Critical-review gate:** P0/P1 visions and G1 briefs get a **`critical-review`** before the gate
(fresh cloud agent, **forced different model family** from the author, read-only) — the main lever
for AI-factory review quality and continuous improvement of autonomy. Canon:
[`product-docs/global/hygiene/critical-review/README.md`](product-docs/global/hygiene/critical-review/README.md);
skill [`.claude/skills/critical-review/SKILL.md`](.claude/skills/critical-review/SKILL.md).

For autonomous cold-execution plans and other high-risk slices, Stage 2 also uses the
execution-hardening angle
([`product-docs/templates/cold-agent-plan-review.prompt.md`](product-docs/templates/cold-agent-plan-review.prompt.md))
via the same keyword.

The always-applied rule
[`.cursor/rules/product-docs-workflow-bootstrap.mdc`](.cursor/rules/product-docs-workflow-bootstrap.mdc)
points here — do not maintain a second full copy of this procedure.

## Public methodology explorer ordering

For curated documentation packs exposed in the methodology explorer (`product-docs/methodology/`,
`product-docs/templates/`), keep `README.md` first in each pack so it opens with orientation.

## Methodology publication boundary

`product-docs/` plays a dual role in this repository: the official Ezkey working documentation
system, and the source for the publishable methodology product (explorer + download pack).

- Publish method-level canon only: `product-docs/methodology/README.md`, `product-docs/templates/`,
  `product-docs/glossary.md`.
- Do **not** publish regular Ezkey delivery artifacts from `product-docs/global/`,
  `product-docs/components/`, backlog / roadmap / vision execution records, or editor-local
  `.cursor/` assets as part of the methodology product.
- If a source-project artifact contains a reusable methodological lesson, restate or promote that
  lesson into the methodology README instead of broadening the publication surface ad hoc.

When a task involves **entity relationships, lifecycle semantics, operational eligibility, parent-child propagation, reversible vs irreversible actions, or operator analysis across multiple entity types**, it is also mandatory to read **`docs/LIFECYCLE_GOVERNANCE.md`** before proposing a design, plan, or implementation direction. Treat that document as the source of truth for how Ezkey models:

- entity hierarchy and cross-entity relationships,
- local lifecycle state versus effective operational status,
- parent-chain eligibility blocking,
- admin identity lifecycle versus admin MFA enrollment lifecycle,
- operator guardrails and action semantics.

Do not rely only on endpoint shape or isolated module behavior for this class of analysis; check `docs/LIFECYCLE_GOVERNANCE.md` first and align the recommendation with it.

When the change modifies **administrator lifecycle transitions** or **activation / onboarding**
operator paths, reconcile the implementation explicitly with `docs/LIFECYCLE_GOVERNANCE.md` and
extend Bruno collections under `bruno/` (and the Admin UI workflow, when applicable) in the same
change set when the recovery surface changes.

When the task is primarily about the React Native mobile app, also start with `ezkey_mobile/AGENTS.md` and `ezkey_mobile/docs/README.md`. For **Android debug build/install on a device**, use `ezkey_mobile/scripts/build-install-debug-clean.sh` (see mobile `AGENTS.md` § Android debug build; do not guess `JAVA_HOME` or use JDK 25). For Play release or publishing work, prefer the current mobile release docs (`MOBILE_RELEASE_SIGNING.md`, `MOBILE_PLAY_PUBLISHING.md`, `MOBILE_PLAY_RELEASE_READINESS_AUDIT.md`, and `MOBILE_RELEASE_DECISION_MEMO.md`) over any deleted or historical upgrade-analysis notes.

For configuration properties, each backend module has a colocated **`CONFIGURATION.md`** (property tables, obligation levels, profile matrix, Docker env var mapping). The central index is **`docs/configuration/README.md`**. When adding or changing a `@ConfigurationProperties` class, update the relevant `CONFIGURATION.md` and, if a new prefix is introduced, the index.

---

## Maven and formatting (before any build)

After implementing or changing Java (or other Spotless-covered) code, use the safe Maven baseline
from the repository root in **Bash**:

1. `mvn spotless:apply`
2. `mvn -pl checkstyle-config install -DskipTests` (fresh `~/.m2`)
3. `mvn checkstyle:check`
4. `mvn clean`
5. `mvn install -DskipTests`

This is the default autonomous validation path because Checkstyle depends on the reactor-built
`checkstyle-config` module. Only after that baseline succeeds should you run targeted follow-up
commands such as `mvn test -pl 'ezkey-admin-api,!ezkey-tests'`. **`./scripts/build.sh`** (from Git
Bash on Windows, Linux, or macOS) is the single canonical entrypoint — it runs the full baseline
including unit tests. When Cursor's agent shell is PowerShell, invoke it via explicit Git Bash:
`& "C:\Program Files\Git\bin\bash.exe" -lc './scripts/build.sh'`. Do not use `.cmd` build wrappers.
See `.cursor/rules/maven-build.mdc` for the authoritative rule.

**Non-negotiable quality gate:** a top-level reactor build is mandatory for Java changes. Do not
classify Checkstyle/reactor failures as "parasitic" or bypass them with module-only shortcuts.
Checkstyle is a cornerstone guardrail against formatting and convention drift, and the
reactor-built `checkstyle-config` dependency is part of that contract.

**Docker-only alternative:** `./scripts/build-docker.sh` runs Spotless apply in a bind-mounted
container plus the `build-validation` Docker target (see `docs/DEVELOPMENT.md`).

**Local Maven version properties:** The parent POM defines CI-friendly `revision` and `changelist` (product SemVer, ADR-0017). To override from the CLI for all reactor builds (for example a per–git-worktree suffix such as `-Drevision=0.2.0-wt1` so local `install` artifacts do not clash), copy `.mvn/maven.config.example` to `.mvn/maven.config` and edit the revision line. That file is gitignored and is not committed; CI and clones without the file use POM defaults only. Cross-ecosystem sync: `./scripts/sync-version.sh` / `./scripts/check-version-alignment.sh`.

---

## GitHub Actions CI (PR / main)

- Aggregate workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Path-filtered jobs for
  Admin UI, Java backend, and mobile; final job **`ci-gate`** always runs and is the single check
  intended to become **required** on `main`.
- Agents must wait for **`ci-gate`** green on their PRs before treating CI as done. Details and
  local equivalents: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) § Continuous Integration.
- Hygiene passes (`doctor-curated`, `java-doctor-curated`, …) remain **not** CI gates.
- There is **no** tag-triggered release / Maven Central publish workflow yet.

---

## Java Javadoc and Checkstyle (`@param` on types)

Checkstyle `JavadocType` validates Javadoc on **classes, interfaces, enums, and record types**.

- **Classes / interfaces / enums:** do **not** put `@param`, `@return`, or `@throws` on the type
  itself (`Unused @param tag … [JavadocType]`). Document parameters on constructors or methods
  (`JavadocMethod`).
- **Records (Checkstyle 13.9+):** document each **record component** with `@param` on the **type**
  Javadoc. Missing component tags fail the build.

See `.cursor/rules/javadoc-type-param.mdc`.

---

## `scanBasePackages` — keeping Application classes in sync

Each boot module (`AdminApplication`, `AuthApplication`, `IntegrationApiApplication`, …) declares an **explicit** `scanBasePackages` list. When a new top-level package is added to `ezkey-core` (e.g. `org.ezkey.service`), every boot application that transitively uses a bean from that package must include the new entry, or Spring will fail to start with a *"required a bean … that could not be found"* error.

**Rule:** whenever you introduce a new `org.ezkey.<package>` in `ezkey-core` that contains `@Service`, `@Component`, or `@Repository` classes, check and update `scanBasePackages` in **all three** application classes:

- `ezkey-admin-api` → `AdminApplication.java`
- `ezkey-auth-api` → `AuthApplication.java`
- `ezkey-integration-api` → `IntegrationApiApplication.java`

The symptom is a clean compile but a startup failure — not caught by unit tests.

---

## Contract refresh and Bruno collections

`scripts/update-specs.sh` refreshes the generated OpenAPI artifacts under `specs/` and dispatched
copies such as the Admin UI and SDK specs. It does **not** update Bruno collections under `bruno/`.

### Controller changes imply contract review

When you change a **controller**, assume you are changing an API contract unless you have verified
otherwise. This must become a default analysis and design reflex, not an afterthought.

Before finalizing a plan or implementation that touches controller code:

- review whether the request shape, response shape, status codes, validation behavior, erro
  semantics, examples, or operator workflow changed;
- identify the impacted Bruno folder(s) under `bruno/` up front as part of the design, not only at
  the end;
- treat Bruno updates as part of the same change set whenever the controller change affects how
  an endpoint is called, understood, tested, or demonstrated.

**Rule:** whenever an endpoint, DTO, validation contract, example payload, or operator workflow
changes and you run `update-specs`, review and update every impacted Bruno request in the same
change set. A backend contract refresh is not considered complete until both the generated OpenAPI
files and the affected Bruno collection describe the same behavior.

**Stronger practical rule:** if you modify controller behavior in a way that affects the API
surface, you should assume the Bruno collection must also be updated. Do not wait for a late
"docs pass" to decide. The default should be:

1. controller change,
2. contract review,
3. Bruno collection update (`bruno/`),
4. generated spec refresh (clean-start + `./scripts/update-specs.sh` + client regen when applicable).

**Bruno CLI smoke:** after script or chaining changes, prefer
`./scripts/bruno-health.sh --suite g0` (no auth) when Crypto/public are touched. Catalog smoke
(`--suite g4`) needs a bearer token. See [`bruno/README.md`](bruno/README.md).

**OpenAPI refresh is part of contract-changing work by default** — not a separate approval gate. See
`.cursor/rules/openapi-specs.mdc`. Agents must never hand-edit generated OpenAPI under `specs/**`
**or dispatched copies** (e.g. `ezkey-admin-ui/openapi-spec.json`). When the stack is available,
run clean-start, then `./scripts/update-specs.sh`, then regenerate dependent clients (e.g.
`npm run generate:api` in `ezkey-admin-ui`). Skip only when the user explicitly defers refresh o
the environment cannot run Docker; report pending refresh instead of patching specs by hand.
Generated output should be reviewed for obvious scope drift and reported in the close-out.

---

## Project values (analysis and design)

- **Simplicity and pragmatism**: 80–20 rule — target ~80% of the value with ~20% of the complexity. Prefer the simplest solution that meets the need.
- **Adopter posture.** Ezkey is never the adopter's core business: every human surface (Admin UI, demos, tester pages, mobile copy) must let a person decide in ~3 seconds — simple, fluid, operationally clear. See `product-docs/global/operator-alignment-guide.md`.
- **New mode → re-challenge what exists.** A new entry mode or use case triggers a keep/adapt/hide pass on existing UI content and an inventory of pre-existing conditions on the touched path (timeouts, errors, rate limits, logging) — including when the request comes from the maintainer.
- **Interfaces in interaction.** Before judging a gap real or a product decision sound, look at the whole Ezkey interaction, not one screen: which surfaces take part (integrating app and its login dialog, mobile app, Admin UI, APIs), who initiates, where that person is looking at that moment, and what each surface already shows. Put information where the person's attention already is; do not duplicate on another surface what one already owns without a trust reason — a second copy adds accidental complexity and can drift.
- **Vision locks travel.** Every brief carries a lock-trace table (vision lock → brief section or explicit deferral); an unmapped lock blocks G1.
- **Admin UI**: Sober, pragmatic interface that makes the operator's and their team's life easier. Avoid clutter and unnecessary decoration.
- **Admin roles**: **Global Admin** = IT-style; manages core/instance-level concerns (e.g. encryption keys). **Tenant Admin** = business-oriented; manages end users, integrations, API keys. Design and copy must reflect this split.
- **Comparables and best practices**: For any analysis or design, consider comparable projects and admin UIs; adopt widely recognised best practices from those comparables.
- **Stack and ecosystem**: Stay within the existing stack; avoid new frameworks or libraries unless there is a strong justification. Prefer what developers expect and what is considered best practice for the stack.
- **Complexity**: **Essential complexity** (required for the feature) is acceptable. **Accidental complexity** (extra indirection, unnecessary abstraction) must be minimised to keep maintenance and evolution manageable.
- **Fail-open vs fail-closed**: At critical boundaries, name whether failure of a control lets the primary path **continue** (fail-open; keep failure observable) or **stop** (fail-closed; when continuing would silently weaken a claimed guarantee). Light compass — not a ceremony. See `product-docs/global/design-principles.md` §17.

---

## Admin UI Browser Validation

- Agent validation ladder (build → API → Playwright / MCP smoke): [`docs/testing/AGENT_UI_VALIDATION.md`](docs/testing/AGENT_UI_VALIDATION.md); closeout trigger rule [`.cursor/rules/agent-ui-closeout-validation.mdc`](.cursor/rules/agent-ui-closeout-validation.mdc)
- The repository includes a **Playwright** browser suite for the Admin UI in `ezkey-admin-ui/`.
- The preferred validation model is **real end to end** with the standard clean-start stack plus the pre-seeded **Demo Device**.
- Use the browser suite when a change materially affects Admin UI behavior or the Admin UI ↔ Demo Device flow.
- Do **not** treat browser tests as mandatory for every trivial UI tweak.
- See `ezkey-admin-ui/AGENTS.md` for the concrete commands and execution modes.
- **Cursor MCP browser:** use `http://localhost:…` (not `127.0.0.1`) on Windows; Demo Device path is
  `/phone/ezkey` → enrollment → Approve → `[data-testid=demo-device-back-to-enrollments]`; full
  recipe in `AGENT_UI_VALIDATION.md` § *Step 4 — MCP browser*. Demo Device handshake notes also
  live in `ezkey-demo-device/AGENTS.md`.

## Skills (hygiene lanes and recurring procedures)

Project skills live in [`.claude/skills/`](.claude/skills/) (read by Claude Code and Cursor). The
operator starts a lane by saying its keyword or `/keyword`; read the skill before acting. Lane
skills are punctual curated passes: **not** CI gates, **not** zero-warning campaigns, and no
`I-*` / `TB-*` / GitHub issue per finding. Before any code change they walk findings **one at a
time** with the operator (HITL). Hygiene index and cadence:
[`product-docs/global/hygiene/README.md`](product-docs/global/hygiene/README.md).

| Keyword | Use for |
|---|---|
| `doctor-curated` | Admin UI React lint / polish pass |
| `java-doctor-curated` | Java static analysis (SpotBugs, Semgrep, PMD) |
| `mobile-doctor-curated` | Ezkey Mobile static analysis |
| `pam-doctor-curated` | Experimental PAM C module (format, clang-tidy, cppcheck, sanitizers) |
| `dependabot-curated` | Dependabot PR triage and merges; platform majors via [`major-upgrades`](product-docs/global/hygiene/major-upgrades/README.md) |
| `security-pentest-curated` | Live local-stack DAST / OpenAPI fuzzing |
| `javamelody-curated` | Performance pass on JavaMelody counters after a known workload |
| `quality-gate` | On-demand full-suite and stack-health gate |
| `assessment-curated` | Mandate-driven white-box assessment → HITL → handoff |
| `document-hygiene-curated` | Legacy document canonicality / archive-vs-delete |
| `critical-review` | Read-only adversarial review by a different model family |
| `corpus-ablation` | Prune ephemeral scaffolds into canon |
| `monthly-digest` | Monthly activity digest for ezkey.org |

Opening a GitHub issue: use the `github-issue` skill (five mandatory label groups, verified after
create).

## UI Test Autonomy

- Treat browser UI tests as a **pragmatic judgment call**, not a mechanical checklist item.
- When a task changes a **critical UI workflow**, a security-sensitive action, a confirmation path, conditional navigation, or the Admin UI ↔ Demo Device interaction, explicitly evaluate whether the existing browser tests are:
  - already sufficient,
  - worth running as-is,
  - or worth extending with a **targeted** new scenario.
- State that judgment clearly in analysis or close-out instead of leaving it implicit.
- Prefer a **small representative test addition** over broad UI coverage when a gap is real.
- If the change is minor (copy, spacing, low-risk presentation only), do **not** over-recommend browser testing.

## Manual Exploratory Test Summaries

- When a task changes a meaningful workflow and a human validation pass would add value, consider proposing a **brief manual exploratory test summary** in addition to automated checks.
- The default assumption for that summary is a **clean-start baseline**:
  - standard `clean-start`
  - empty database
  - default Docker parameters
  - normal local developer/test posture
- When it helps make the workflow concrete, prefer examples that use the Admin UI **demo-mode themes** and presets rather than abstract placeholder data.
- Keep these summaries short and practical:
  - a few high-signal steps
  - expected outcomes
  - no bloated checklist unless the user asks for one
- Use this when it improves QA handoff, human review, or future historical traceability in plans and task summaries.

## Lightweight Hygiene Workflow (GitHub visibility without full methodology)

For local **code-hygiene** passes (dependency bumps with a known recipe, config alignment after a
completed program, React Doctor triage, lint-polish), default to lightweight GitHub visibility
instead of creating full methodology artifacts (`I-*`, `TB-*`, `TSP-*`, `ML-*`).

**Challenge rule:** when the operator invites a "methodological closeout" or full traceability,
classify **hygiene vs program** before materializing backlog artifacts. If hygiene, propose
commit/PR + targeted docs and explain what would justify `I-*` / `TB-*`. See
[`product-docs/methodology/README.md`](product-docs/methodology/README.md) § *Three rules worth
keeping*.

**GitHub issue vs canon:** `product-docs` (`I-*` / `TB-*`) is the decision canon; GitHub issues are
optional visibility (PR board, labels, `Closes #NNN`). Proactively state at start or closeout of a
program slice whether an issue helps — retroactive issues are valid.

## GitHub issues — labels mandatory on create

Every new issue carries the five label groups (lane, type, component, priority, status), checked
after create: see the [`github-issue`](.claude/skills/github-issue/SKILL.md) skill. An unlabeled
issue is incomplete.

## Git commit on Windows (agent shell)

On this workstation, Cursor agents often execute shell commands in **PowerShell**, while Ezkey
scripts and commit-message HEREDOCs expect **Git Bash** (see `.cursor/rules/shell-preferences.mdc`).

**Typical failure:** the agent shell rewrites `git commit` to add
`--trailer "Co-authored-by: Cursor <cursoragent@cursor.com>"`. PowerShell parses `<` as
redirection → commit never runs. Retrying the same PowerShell command wastes turns.

**Maintainer setup (fix at source):** Cursor Settings → **Agents → Attribution** → disable **Commit
Attribution** (and PR attribution if undesired). Restart Cursor. For CLI/cloud agents, also set
`attributeCommitsToAgent: false` in `%USERPROFILE%\.cursor\cli-config.json` if trailers persist.

A second failure mode is **multi-layer quoting**: even through `git-commit.sh`, an **inline** message
crossing PowerShell → `bash -lc '...'` → `git commit` mangles `( ) " ' # ! < >`. Conventional prefixes
like `docs(product):` always contain `()`, so inline `-m` is fragile. The reliable principle: **neve
let the message text cross a shell boundary** — pass a file path instead.

**Agent procedure when committing (canonical zero-argument flow):**

1. `git add <paths>` — either shell is fine; stage only this commit's files.
2. Write the full commit message (subject + optional blank line + body) to **`.ezkey/commit-msg.txt`**
   with the file-writing tool. Any characters are safe; the text never touches a shell.
3. Run the **single constant command** (the `&` call operator is required for the quoted path):

   ```text
   & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-commit.sh'
   ```

   The script commits with `git commit -F .ezkey/commit-msg.txt` and removes the file afterward.
   Inside Git Bash, just run `./scripts/git-commit.sh`.
4. Do **not** use bare PowerShell `git commit`, HEREDOC, `&&` chains, inline `-m "type(scope):"`, o
   WSL bash; use **Git Bash** (`C:\Program Files\Git\bin\bash.exe`). Power-user forms still work:
   `./scripts/git-commit.sh -F <file>` or `-m "subject"`.

Authoritative rule: `.cursor/rules/git-commit-windows.mdc`. Copilot mirror:
`.github/copilot-instructions.md` § *Git Standards*.

## GitHub pull request on Windows (agent shell)

Creating a PR hits the **same quoting failure mode** as commits: PowerShell re-parses
`gh pr create --title "fix(security): …"` or HEREDOC `--body "$(cat <<'EOF' …)"` before
Git Bash/`gh` run. Conventional titles always contain `()`. Do **not** invent a one-off temp
script as the durable workflow — use the repo helper next to `git-commit.sh`.

**Agent procedure when opening a PR (canonical zero-argument flow):**

1. Push the branch if needed (`git push -u origin HEAD`).
2. Write the PR title (single line) to **`.ezkey/pr-title.txt`** with the file-writing tool.
3. Write the PR body (Markdown) to **`.ezkey/pr-body.md`** with the file-writing tool.
4. Run the **single constant command**:

   ```text
   & "C:\Program Files\Git\bin\bash.exe" -lc './scripts/git-pr.sh'
   ```

   Inside Git Bash: `./scripts/git-pr.sh`. The script calls `gh pr create --title … --body-file …`
   and removes the default title/body files afterward.
5. Do **not** use bare PowerShell `gh pr create` with inline `--title` / HEREDOC `--body`, or WSL
   bash. Power-user forms: `./scripts/git-pr.sh --title-file <file> --body-file <file>`,
   `--draft`, `--base <branch>`.

Authoritative rule: `.cursor/rules/git-pr-windows.mdc`. Copilot mirror:
`.github/copilot-instructions.md` § *Opening pull requests on Windows*.

## Windows Bash selection (anti-WSL ambiguity)

On Windows, many shells resolve bare `bash` to `C:\Windows\System32\bash.exe` (WSL shim). For
Ezkey repo scripts, this is not acceptable unless explicitly requested by the operator.

Required default for agents and maintainers:

- Use Git Bash from Git for Windows: `C:\Program Files\Git\bin\bash.exe`
- From PowerShell/CMD/agent shells, invoke Bash explicitly:

  ```text
  & "C:\Program Files\Git\bin\bash.exe" -lc '<command>'
  ```

- Do not use bare `bash` when launched from Windows-hosted shells.
- Do not use `C:\Windows\System32\bash.exe` for repository workflows.

Quick check when uncertain:

```text
& "C:\Program Files\Git\bin\bash.exe" -lc 'command -v bash; uname -a'
```

Expected shell family is `MINGW`/`MSYS`.
