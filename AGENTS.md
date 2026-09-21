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
| Product intent, thesis, audience | [`product-docs/global/product-intent.md`](product-docs/global/product-intent.md) |
| Priority / what to implement next | [`product-docs/global/operational-readiness-prioritization-2026-09.md`](product-docs/global/operational-readiness-prioritization-2026-09.md), [`product-docs/global/backlog/index.md`](product-docs/global/backlog/index.md) |
| Protocol crypto (Auth API ↔ mobile / Demo Device) | [`docs/CRYPTO.md`](docs/CRYPTO.md), [`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md); relevant **section** of [`docs/ENDPOINT.md`](docs/ENDPOINT.md) only |
| At-rest encryption / Tink keyset storage | [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md), [`ezkey-core/CONFIGURATION.md`](ezkey-core/CONFIGURATION.md) § Encryption at Rest, [`product-docs/global/architecture-decisions.md`](product-docs/global/architecture-decisions.md) ADR-0008 + ADR-0011 |
| Normative posture | [`product-docs/global/normative-posture.md`](product-docs/global/normative-posture.md) — operational discipline; mapping vocabulary lives only there. Honest claims: [`docs/SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) |
| Lifecycle / eligibility / multi-entity operator design | Skim [`product-docs/global/lifecycle-model.md`](product-docs/global/lifecycle-model.md), then [`docs/LIFECYCLE_GOVERNANCE.md`](docs/LIFECYCLE_GOVERNANCE.md) when designing |
| Admin UI conventions | [`ezkey-admin-ui/AGENTS.md`](ezkey-admin-ui/AGENTS.md) |
| Mobile-primary work | [`ezkey_mobile/AGENTS.md`](ezkey_mobile/AGENTS.md), [`ezkey_mobile/docs/README.md`](ezkey_mobile/docs/README.md) |
| Mandate-driven hygiene assessment (HITL + handoff) | Keyword **`assessment-curated`** → [`product-docs/global/hygiene/assessment-curated/README.md`](product-docs/global/hygiene/assessment-curated/README.md) |
| Legacy document hygiene (canonicality / discoverability / archive-vs-delete) | Keyword **`document-hygiene-curated`** → [`product-docs/global/hygiene/document-hygiene/README.md`](product-docs/global/hygiene/document-hygiene/README.md) |
| New idea / method | [`product-docs/methodology/README.md`](product-docs/methodology/README.md) — the whole method fits in that one document since the 2026-08 ablation |
| Plan review / cold-agent hardening | [`product-docs/templates/cold-agent-plan-review.prompt.md`](product-docs/templates/cold-agent-plan-review.prompt.md) — 2-stage review pattern in [`product-docs/methodology/README.md`](product-docs/methodology/README.md) |
| API contract change | Controller + **section** of `docs/ENDPOINT.md` + OpenAPI refresh workflow (see OpenAPI rules) |
| Strategic positioning / public copy | [`docs/PROJECT_POSITIONING.md`](docs/PROJECT_POSITIONING.md) only when that layer is in scope |

## Terminology guardrail (phase vs milestone)

- Use **phase** only for the product-docs methodology workflow phases.
- Use **milestone** for product-level progression, roadmap, and release-orientation statements.
- When reporting current product progress, answer with milestone language, not phase language.

## Where are we? (release priority compass)

When the question is **what to implement next** or **current release priority** (September 2026
operable-release target), read first:

- [`product-docs/global/operational-readiness-prioritization-2026-09.md`](product-docs/global/operational-readiness-prioritization-2026-09.md)
- [`product-docs/global/backlog/index.md`](product-docs/global/backlog/index.md) § Current prioritization ancho

## Fresh-session workflow bootstrap (product-docs method)

When a new session starts and the user is bringing a **new idea**, read
[`product-docs/methodology/README.md`](product-docs/methodology/README.md). The 2026-08 ablation
condensed the entire method — documentary levels (`V-*`/`I-*`/`TB-*`/`ADR-*`), bidirectional
discoverability, the values compass, and the three rules worth keeping (hygiene vs. program,
ephemeral scaffold vs. retained plan, closed uncertainty stays closed) — into that single document.
There is no separate lane taxonomy or skill sequence to load first: prefer **Plan mode** for
research and comparing alternatives, then position the idea as a `V-*`, `I-*`, or `TB-*` in
`product-docs/global/` only when the methodology README's artifact-choice table calls for it.
For high-risk slices (security boundaries, session management, DB migrations, or autonomous cold-agent
handoffs), harden the plan via the **two-stage plan hardening pattern**
([`product-docs/templates/cold-agent-plan-review.prompt.md`](product-docs/templates/cold-agent-plan-review.prompt.md)).

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

**Local Maven version properties:** The parent POM defines `revision`, `changelist`, and an empty default `buildQualifier`. To override from the CLI for all reactor builds (for example a per–git-worktree suffix such as `-wt1` so local `install` artifacts do not clash), copy `.mvn/maven.config.example` to `.mvn/maven.config` and edit the last line. That file is gitignored and is not committed; CI and clones without the file use POM defaults only.

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

## Admin UI lint-polish keyword

- For Admin UI React lint or polish passes, the shared keyword is **`doctor-curated`**.
- Purpose: a **punctual curated pass** sharing the same hygiene operating model as
  `java-doctor-curated` — React Doctor → P1/P2/P3 shortlist. **Not** a CI gate and **not** a
  zero-warning campaign.
- Run the lightweight curated React Doctor workflow from `ezkey-admin-ui/` before broad analysis;
  details and output files live in `ezkey-admin-ui/AGENTS.md` § React Doctor curated pass.
- Campaign decision notes (HITL): `product-docs/global/hygiene/react-doctor/` (template + dated
  pass instances). Index: `product-docs/global/hygiene/README.md`. Do **not** invent `I-*` /
  `TB-*` / GitHub issues per finding.
- Before implementing a fix set: interactive HITL (one finding at a time; nest the short
  project-contextual continuous-learning briefing in each turn), then a dated campaign note, then
  a **dedicated hygiene branch + PR** — not methodology backlog artifacts. **Put that same short
  briefing in the PR body** and **link the campaign note** (durable learning trace). After
  high-signal items, reserve a **modest low-signal allotment** (cheap P2/P3 continuous-improvement
  wins) so light polish does not wait forever. Full HITL contract: `ezkey-admin-ui/AGENTS.md`
  § React Doctor curated pass.

## Java doctor-curated keyword

- For Java (and adjacent) static-analysis hygiene, the shared keyword is **`java-doctor-curated`**.
- Purpose: a **punctual curated pass** mirroring Admin UI `doctor-curated` — SpotBugs + Semgrep
  (pinned pack) + narrow PMD (design/maintainability) → P1/P2/P3 shortlist. **Not** a CI gate and
  **not** a zero-warning campaign. Sibling lane: Admin UI `doctor-curated` above.
- Default command from repo root (Git Bash on Windows):

```bash
./scripts/java-doctor-curated.sh
```

  Optional: `--skip-compile` when target classes already exist; `--modules csv` to narrow scope.
  On Windows + Semgrep Docker, the script confines `MSYS_NO_PATHCONV=1` to the Docker subshell and
  uses a slim scan workspace under `logs/java-doctor/scan-workspace`.
- Outputs under `logs/java-doctor/` (gitignored):
  - `java-doctor.curated.md` — human-readable shortlist + planning contract
  - `java-doctor.curated.json` — machine-readable summary
  - `raw/` — SpotBugs/PMD XML + Semgrep JSON inputs
- Config under `config/java-doctor/` (pinned Semgrep pack, narrow PMD ruleset, suppressions).
- Default modules: `ezkey-core`, `ezkey-core-security`, `ezkey-admin-api`, `ezkey-auth-api`,
  `ezkey-integration-api`.
- Campaign decision notes (HITL): `product-docs/global/hygiene/java-doctor/` (template + dated
  pass instances). Do **not** invent `I-*` / `TB-*` / GitHub issues per finding.

### HITL contract (mandatory for cold agents)

When the operator asks for a **`java-doctor-curated`** improvement pass:

1. Run the script; read `logs/java-doctor/java-doctor.curated.md`.
2. Propose a **small prioritized lot** (usually 3–6 items), not a zero-warning campaign. A short
   numbered **overview** of the lot is fine (rule + location hint only).
3. **Before any code change — interactive HITL loop (mandatory):** do **not** replace the dialogue
   with one dense options matrix that asks for a bulk reply (`1A, 2B, 3B…`). After the overview,
   **iterate explicitly, one finding at a time**: what the tool said, where to look in source,
   hypothesis, options, open question — then **wait** for the operator’s Go / No-Go / suppress /
   skip / clarifying questions on **that** item before presenting the next. The point of HITL here
   is shared code review and refinement, not a single synthetic table. A compact decision table may
   appear later in the **campaign note** after decisions are made — not as the primary briefing.
4. **Fuzzy signal rule:** if the finding cannot be tied clearly to source without opening bytecode,
   **skip** — do not invent a problem. Prefer suppress-with-reason only when the pattern is
   understood and intentionally accepted.
5. **If it ain't broken, don't fix it:** when diagnosis is **clear** but the flagged code is
   harmless local over-defense (redundant guards obvious within a few nearby lines), **leave the
   code** and suppress with reason. Do not “perfect” it: extra characterization tests and
   crypto-adjacent churn for zero product gain is a witch-hunt, not hygiene. Clarity of the
   finding does **not** oblige a rewrite.
6. Record decisions in a dated campaign note under `product-docs/global/hygiene/java-doctor/`
   (copy `TEMPLATE.md`). Machine-facing suppressions go in `config/java-doctor/suppressions.json`
   (and SpotBugs exclude when class-level).
7. Only then implement accepted fixes on a **dedicated hygiene branch + PR**; put the same short
   briefing in the PR body; link the campaign note. Modest low-signal allotment after high-signal
   items is allowed when the operator agrees.
8. Security-sensitive zones (Tink keyset, HMAC keys, login/logout, encryption listeners): require
   careful counter-analysis; characterization / complementary tests before refactor when behavio
   might change. If the chosen decision is leave+suppress, skip those tests.

- Authority: `product-docs/global/java-doctor-curated-evaluation-2026-07-11.md`,
  `I-2026-07-11-java-doctor-curated-hygiene`, `TB-2026-07-11-java-doctor-curated-mvp`,
  `product-docs/global/hygiene/java-doctor/README.md`.

## Dependabot curated keyword

- For weekly Dependabot dependency-update triage, the shared keyword is **`dependabot-curated`**.
- Purpose: a **punctual curated pass** that classifies open Dependabot PRs into risk-tiered lots
  (T1 patch → T4 major/disruptor), decides them via HITL **or** (when the operator grants it)
  **autonomous validation mode**, applies bumps, then runs a **session closeout** validation
  ladder with evidence. **Not** silent auto-merge and **not** a full `I-*` / `TB-*` program for
  routine bumps. Sibling hygiene lanes: `doctor-curated` and `java-doctor-curated` above.
- Skill: [`.cursor/skills/dependabot-curated/SKILL.md`](.cursor/skills/dependabot-curated/SKILL.md).
- **Cursor Cloud `GH_TOKEN`:** the harness read-only `gh` note is the default `cursor`/`ghs_`
  identity. If env `GH_TOKEN` is set, probe `gh auth status` (never print the secret) and use that
  PAT to squash-merge Dependabot PRs and to comment/close superseded PRs after a hygiene PR lands.
  Do **not** invent a hygiene branch solely because `gh` was labeled read-only. Kickoff line and
  identity table: skill § *Cloud GitHub identities*; rule
  [`.cursor/rules/cloud-gh-token.mdc`](.cursor/rules/cloud-gh-token.mdc).
- Campaign decision notes: `product-docs/global/hygiene/dependabot/` (template + dated pass
  instances). Index: `product-docs/global/hygiene/README.md`.
- **Primary axis:** SemVer risk + known disruptors. **Secondary axis:** ecosystem / surface
  (Maven, Admin UI, mobile, SDK, Actions). Defer disruptive bumps with a PR comment and, when
  investigation cost must persist, a single `I-*` (e.g. TypeScript 7 → later release train).
  Long-lived parks use GitHub label **`deferred:later-train`** (see dependabot hygiene README) so
  weekly passes skip HITL on those PRs.
- Config that reduces future atomization: [`.github/dependabot.yml`](.github/dependabot.yml)
  groups. Do not invent methodology backlog for the weekly Dependabot habit itself.

### HITL contract (default for cold agents)

When the operator asks for a **`dependabot-curated`** pass:

1. List open Dependabot PRs (`gh pr list --author "app/dependabot" --state open`). Probe
   Cursor Cloud `GH_TOKEN` (`gh auth status`; never print the secret) before choosing
   hygiene-branch vs merging the existing Dependabot PRs.
2. Peel off any PR labeled `deferred:*` (skip weekly lots). Run the **Java BOM pulse** (skill
   `dependabot-curated` § *Java BOM pulse*): compare `spring-boot.version` to the latest
   same-minor Boot release; if newer and no Dependabot PR, propose a hygiene-branch lot. After a
   Boot bump, review SEC-019 overrides. Check `google-java-format.version` only as a tooling pin.
   Same weight: run the **Mobile RN pulse** (skill § *Mobile RN pulse*): record declared
   `react` / `react-native` from `ezkey_mobile/package.json`, prefer `yarn deps:monitor`, peel
   Dependabot mobile PRs; empty mobile queue ≠ stack current.
3. Classify remaining PRs T1–T4; propose **3–6 lots** (overview only).
4. **Default — interactive HITL:** iterate **one lot at a time** (members, tier, blast radius, CI
   status) → wait for Go / No-Go / hold / defer on **that** lot before merging or presenting the
   next. Do not replace this with a bulk options matrix.
5. **Autonomous validation mode** (opt-in): when the operator explicitly delegates validation /
   waives per-lot Go (e.g. “full ladder yourself”, “autonomous”), proceed on T1–T3 without waiting;
   still pause on T4 / hard escalators unless also waived. **Still merge the existing Dependabot
   PRs in each lot** so GitHub closes them — autonomy does not mean re-applying bumps on a second
   branch. Run the full closeout ladder yourself and present evidence. Hygiene-branch re-apply is
   only for explicit single-PR review or unmergeable Dependabot branches + companion fixes; then
   close superseded PRs after land. Details: skill `dependabot-curated` § *Autonomous validation
   mode*.
6. On Go / autonomous proceed: merge each green Dependabot PR in the lot individually. On defer:
   comment; apply `deferred:later-train` when the PR should stay out of weekly lots; optional one
   `I-*` if the investigation should not be lost.
7. Session closeout proportional to highest accepted tier (always `./scripts/build.sh`; stack /
   functional / Playwright per skill ladder; T1-only shortcut allowed when recorded).
8. Write a dated campaign note under `product-docs/global/hygiene/dependabot/` (copy `TEMPLATE.md`).

## Mobile doctor-curated keyword

- For Ezkey Mobile (`ezkey_mobile/`) static-analysis hygiene, the shared keyword is
  **`mobile-doctor-curated`**.
- Purpose: punctual curated pass — react-doctor + Semgrep (Ezkey mobile pack) + Detekt → P1/P2/P3
  shortlist. **Not** a CI gate and **not** a zero-warning campaign.
- Default command from `ezkey_mobile/` (Git Bash on Windows):

```bash
yarn doctor:curated
# or
./scripts/mobile-doctor-curated.sh
```

- Outputs under `ezkey_mobile/logs/mobile-doctor/` (gitignored):
  - `mobile-doctor.curated.md` — human-readable shortlist + planning contract
  - `mobile-doctor.curated.json` — machine-readable summary
  - `raw/` — analyzer inputs
- Config: `ezkey_mobile/config/mobile-doctor/suppressions.json`
- Campaign notes: `product-docs/global/hygiene/mobile-doctor/`
- HITL and operating rules: `ezkey_mobile/AGENTS.md` § Mobile doctor-curated pass
- Authority: `product-docs/global/mobile-doctor-curated-evaluation-2026-07-11.md`,
  `I-2026-07-11-mobile-doctor-curated-hygiene`, `TB-2026-07-11-mobile-doctor-curated-mvp`

## Security pentest curated keyword

- For live local Docker-stack DAST / OpenAPI fuzzing hygiene, the shared keyword is
  **`security-pentest-curated`**.
- Purpose: punctual curated pass — Schemathesis + OpenAPI-aware ZAP + first-party Nuclei →
  P1/P2/P3 shortlist against `--prod-safe --with-proxy` Caddy ports. **Not** a CI gate and **not** a
  zero-warning campaign. Sibling lanes: `java-doctor-curated`, `doctor-curated`.
- Skill: [`.cursor/skills/security-pentest-curated/SKILL.md`](.cursor/skills/security-pentest-curated/SKILL.md)
- Default command from repo root (Git Bash on Windows):

```bash
./scripts/security-pentest-curated.sh
```

  Preflight / dry-run: `./scripts/security-pentest-curated-preflight.sh`,
  `./scripts/security-pentest-curated.sh --dry-run`. Details: `scripts/README.md`.
- Config: `config/security-pentest/`. Outputs under `logs/security-pentest/` (gitignored).
- Campaign notes: `product-docs/global/hygiene/security-pentest/`
- HITL: same one-finding loop as java-doctor — small lot, wait per finding, suppress with reason.
  Do **not** invent `I-*` / `TB-*` per finding. Do not re-lot Auth device **200** + domain
  `FAILED` (charter § expected-context; pass-04).
- Authority: `product-docs/global/security-pentest-tool-evaluation-2026-07-12.md`,
  `I-2026-07-12-security-pentest-curated-hygiene`, `TB-2026-07-12-security-pentest-curated-mvp`

## JavaMelody curated keyword

- For live local Docker-stack JavaMelody extracts after a **known workload**, the shared keyword is
  **`javamelody-curated`**.
- Purpose: punctual performance pass — freeze collector XML → filter Actuator/scrape noise → rank
  HTTP/SQL/Spring by total time → small P1/P2/P3 HITL lot. **Not** a CI gate and **not** a
  zero-warning campaign. Sibling lanes: `java-doctor-curated`, `security-pentest-curated`.
- Default command from repo root (Git Bash on Windows):

```bash
./scripts/javamelody-curated.sh
```

  Re-parse a frozen dump without HTTP: `./scripts/javamelody-curated.sh --offline`.
- Do **not** clean-start until `logs/javamelody/raw/` holds the current counters (in-memory
  per-request stats are lost on API restart).
- Outputs under `logs/javamelody/` (gitignored):
  - `javamelody.curated.md` — human-readable shortlist + workload-bias banner
  - `javamelody.curated.json` — machine-readable summary
  - `raw/` — collector XML + lastValue dumps
- Config: `config/javamelody/` (noise paths, expected-hot churn routes).
- Campaign notes: `product-docs/global/hygiene/javamelody/`
- HITL: same one-finding loop as java-doctor — small lot, wait per finding. High hit count on
  churn protocol paths is **not** a signal; look for mean, SQL-per-request, errors, or pool wait.
  Integration API silence under operational churn is **coverage**, not health. Do **not** invent
  `I-*` / `TB-*` per finding.

## Assessment curated keyword

- For **mandate-driven white-box hygiene** (focused investigation → assessment register →
  one-finding HITL → handoff), the shared keyword is **`assessment-curated`**.
- Purpose: punctual deep look when OSS doctor/pentest shortlists are the wrong entry signal (e.g.
  mobile crypto/protocol, Java transactional boundaries). **Not** a methodology program lane and
  **not** a substitute for `doctor-curated` / `dependabot-curated` / `security-pentest-curated` /
  `javamelody-curated`.
- Method canon: [`product-docs/global/hygiene/assessment-curated/README.md`](product-docs/global/hygiene/assessment-curated/README.md)
- Cursor skill: [`.cursor/skills/assessment-curated/SKILL.md`](.cursor/skills/assessment-curated/SKILL.md)
- Copilot mirror: `.github/copilot-instructions.md` § Assessment curated
- Campaign template + handoff template live next to the method README.
- Topic-lane instance (first): `product-docs/global/hygiene/mobile-protocol-security/`
- Do **not** invent `I-*` / `TB-*` / GitHub issues per finding; promote only when the operator
  funds a program-sized redesign.

### HITL contract (mandatory for cold agents)

When the operator asks for an **`assessment-curated`** pass:

1. Lock mandate: surface, attention axes, non-goals.
2. Produce or update a durable assessment register with a small lot (usually 3–6).
3. Open/amend a dated campaign note under `product-docs/global/hygiene/<lane>/`.
4. **One finding at a time:** briefing with code citations + observation scenario + options →
   wait for a HITL reply → decision → handoff if `fix`. **GO means `fix` + write the handoff;
   it does not mean “next finding” and does not start coding** unless the operator also says
   implement now. Do not ask a bare “GO / No-Go.” Canon: assessment-curated README § *HITL
   replies*. Then amend the campaign note.
5. Delete completed handoffs on closeout; consolidate PR links into the campaign note.
6. If the operator only asks **how to invoke** / how to phrase a kickoff: summarize
   `product-docs/global/hygiene/assessment-curated/README.md` § **How to invoke (operator cheat sheet)**
   (paste-ready examples). Do not start a full assessment unless they also give a mandate.

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

When opening a GitHub issue (operator request, program slice visibility, or retroactive board
anchor):

1. Apply **all five label groups** via `gh issue create --label ...` (lane, type, component, priority, status) — see [`.cursor/rules/github-issue-labels.mdc`](.cursor/rules/github-issue-labels.mdc).
2. Verify with `gh issue view <N> --json labels` before reporting the issue URL.
3. Record `#NNN` (and label list) in `I-*` / `TB-*` metadata.

An unlabeled issue is **incomplete** — same severity as missing TB traceability.

- Keep scope small and local: high-signal fixes first, no broad refactor campaign.
- Use issue + branch + iterative PR as the default visibility path.
- Prefer these labels for this lane when applicable:
  - lane:c
  - type:refactor (or type:chore for tooling-only work)
  - component:<target component>
  - priority:p1/p2 according to triage
  - status:ready once coding can start

Windows shell note for GitHub CLI reliability:

- In some PowerShell sessions, gh may not resolve from PATH.
- If `gh` is not found, use the absolute executable path:
  - C:\Program Files\GitHub CLI\gh.exe
- Recommended pre-check before issue/PR automation:
  - `gh auth status` (or equivalent call via the absolute path above)

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
