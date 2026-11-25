# Ezkey — agent guide (repo-wide)

For agents (Claude Code, Cursor) working anywhere in the repo. Module conventions live in each
module's `AGENTS.md` (for example `ezkey-admin-ui/`, `ezkey-core/`, `ezkey_mobile/`,
`sites/ezkey-org/`). Keep this file under about 200 lines: a longer guide is followed less well
(lane [`agent-surface`](product-docs/global/hygiene/agent-surface/README.md)).

## Mission and compass

Ezkey is an open-source **cryptographic MFA platform**: backend-first, its own protocol (not FIDO2,
not WebAuthn), self-hosted, with a mobile app as the secure device. It is the middle path for SMEs:
stronger than passwords and TOTP, not sized for large enterprises. A **public alpha** community
instance runs (lab · discreet · alpha ≠ production · no SLA). Canon:
[`product-intent.md`](product-docs/global/product-intent.md).

In a session, deliver the requested change so that it leaves `main` coherent, recorded and proven
(see *Done means* and *Evidence*).

Values for every analysis and design ([`design-principles.md`](product-docs/global/design-principles.md)):

- **Simplicity and pragmatism:** about 80% of the value for 20% of the complexity. Essential
  complexity is fine; accidental complexity (extra indirection, needless abstraction) is not.
- **Adopter posture:** Ezkey is never the adopter's core business. Every human surface (Admin UI,
  demos, tester pages, mobile copy) lets a person decide in about 3 seconds
  ([`operator-alignment-guide.md`](product-docs/global/operator-alignment-guide.md)).
- **Interfaces in interaction:** judge a gap on the whole interaction (integrating app and its
  login dialog, mobile app, Admin UI, APIs): who initiates, where that person is looking, what each
  surface already shows. Put information where attention already is; do not copy what another
  surface owns without a trust reason.
- **New mode → re-challenge what exists:** a new entry mode or use case triggers a keep / adapt /
  hide pass on existing UI and an inventory of the touched path (timeouts, errors, rate limits,
  logging), even when the maintainer asks for it.
- **Admin UI:** sober and pragmatic, no clutter. **Global Admin** = IT-style, instance-level
  (encryption keys…); **Tenant Admin** = business-oriented (end users, integrations, API keys).
  Design and copy reflect that split.
- **Comparables:** consider comparable projects and admin UIs; adopt widely recognised practice.
- **Stack:** stay within the existing stack; a new framework or library needs a strong reason.
- **Fail-open vs fail-closed:** at critical boundaries, name whether a failing control lets the
  primary path continue (keep the failure observable) or stops it (when continuing would silently
  weaken a claimed guarantee). A light compass, not a matrix (§17).

## Rules

- **The repo is the memory.** A decision that changes behaviour, a contract, a constraint or how we
  work lands in its owning doc in the same PR: one line + reason + issue #. Chat-only is unrecorded.
- **English is the repo language:** issues, PRs, commits, code, comments, docs and methodology are
  written in English, even when the maintainer dictates in French; existing French stays as is.
  Scope and exceptions: [`.github/DOCUMENTATION_GUIDELINES.md`](.github/DOCUMENTATION_GUIDELINES.md).
- **Code goes through PRs.** Local test hosts build and test only. Merge only on the owner's
  explicit go.
- **Controller change = contract change** until proven otherwise: review request and response
  shape, status codes, validation, error semantics, examples and operator workflow, and name the
  impacted `bruno/` folders up front. Generated OpenAPI (`specs/**` and dispatched copies such as
  `ezkey-admin-ui/openapi-spec.json`) is never edited by hand.
- **Lifecycle work** (entity relationships, eligibility, parent-chain blocking, reversible vs
  irreversible actions, admin identity vs admin MFA): read
  [`docs/LIFECYCLE_GOVERNANCE.md`](docs/LIFECYCLE_GOVERNANCE.md) before proposing a design. Changes
  to admin lifecycle or activation paths extend `bruno/` (and the Admin UI flow) in the same change.
- **Logging:** a caller-controlled value goes through `org.ezkey.util.LogSanitizer.sanitizeForLog`;
  secrets are never logged at any level; prove it on runtime output (log-capture test).
- **Configuration:** a new or changed `@ConfigurationProperties` updates the module's
  `CONFIGURATION.md` (and [`docs/configuration/README.md`](docs/configuration/README.md) for a new
  prefix).
- **Java:** conventions in [`.claude/rules/java.md`](.claude/rules/java.md) (loaded automatically
  for Java files).
- **New idea or design:** Plan mode first, then [`product-docs/methodology/README.md`](product-docs/methodology/README.md).
  Classify **hygiene vs program** before creating `I-*` / `TB-*`. P0/P1 visions and G1 briefs get a
  `critical-review` (different model family) before the gate, and every brief maps each vision lock
  to a section or an explicit deferral.
- **Terminology:** *phase* only for the methodology workflow; *milestone* for product progress.
- **Dictation:** the maintainer dictates; read phonetic near-misses as project terms (Kadi /
  Caddie → Caddy, iziki / easy key → Ezkey, Light sail → Lightsail, Wisp → Wispr).

## Done means

- The work is on a branch with a PR: complete description, related docs updated in the same PR.
- `ci-gate` green (CodeQL included), no new code-scanning alert, no unresolved review thread.
- API surface changed (controller, DTO, validation, status codes): the `bruno/` requests and the
  generated specs describe the same behaviour. If the stack cannot run, say the refresh is pending.
- Merge only on the owner's explicit go, never on green CI alone.

## Evidence

Run what the change touches and report the command and its result. Say what you did not run and
why.

| Touched | Check | Detail |
|---|---|---|
| Java | `./scripts/build.sh` (full reactor, Checkstyle included; never bypass) | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) |
| API contract | clean-start → `./scripts/update-specs.sh` → `npm run generate:api`; Bruno smoke | `.cursor/rules/openapi-specs.mdc`, [`bruno/README.md`](bruno/README.md) |
| Admin UI | `npm run lint && npm test && npm run build`; Playwright when a workflow, confirmation path or Demo Device flow changes (state the judgment either way) | [`AGENT_UI_VALIDATION.md`](docs/testing/AGENT_UI_VALIDATION.md) |
| Mobile | `yarn lint && yarn typecheck && yarn test` | [`ezkey_mobile/AGENTS.md`](ezkey_mobile/AGENTS.md) |
| Docs only | relative links resolve | — |

A workflow change a human should try gets a short manual test summary (clean-start baseline, empty
database, Admin UI demo-mode presets; a few steps and expected results).

## Where to read (load on demand)

No ritual reads at session start: not the full `docs/ENDPOINT.md`, not `PRD.md` (a stub), not
`docs/PROJECT_POSITIONING.md`. Read product framing when identity or scope matters, then only the
sections the work needs.

| Need | Read |
|------|------|
| First build on a new workstation | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) § *First clone on a new workstation*; `./scripts/build.sh` (`--diagnose-only` if JDK/Maven look wrong) |
| Node.js / Yarn | [`.nvmrc`](.nvmrc) (24). nvm-windows ignores it: `nvm install 24`, `nvm use 24`, `corepack enable` |
| CI and the required check | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) § Continuous Integration (`ci-gate`) |
| What to implement next | [`V-2026-09-26-public-alpha-posture-closeout.md`](product-docs/global/vision/V-2026-09-26-public-alpha-posture-closeout.md), [`backlog/index.md`](product-docs/global/backlog/index.md) |
| Protocol crypto (Auth API ↔ mobile / Demo Device) | [`CRYPTO.md`](docs/CRYPTO.md), [`ENROLLMENT_SIGNATURE_PAYLOAD.md`](docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), [`AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md); the relevant section of [`ENDPOINT.md`](docs/ENDPOINT.md) |
| Challenge-code direction, MFA fatigue | [`SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) § Challenge-code direction (Decision #960) |
| At-rest encryption, Tink keysets | [`SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md), [`ezkey-core/CONFIGURATION.md`](ezkey-core/CONFIGURATION.md) § Encryption at Rest, ADR-0008 / ADR-0011 in [`architecture-decisions.md`](product-docs/global/architecture-decisions.md) |
| Honest claims, operational discipline | [`normative-posture.md`](product-docs/global/normative-posture.md), [`SECURITY_POSTURE.md`](docs/SECURITY_POSTURE.md) |
| Enrollment bind session | [`MOBILE_APP_STATE_MACHINE.md`](ezkey_mobile/docs/MOBILE_APP_STATE_MACHINE.md) § *Product intent: one bind session* |
| Lifecycle, eligibility, multi-entity design | [`lifecycle-model.md`](product-docs/global/lifecycle-model.md), then [`LIFECYCLE_GOVERNANCE.md`](docs/LIFECYCLE_GOVERNANCE.md) |
| Admin UI list, record page, links between them | [`screens-and-wireflow.md`](product-docs/components/admin-ui/screens-and-wireflow.md) § Record anatomy |
| Android build or install (JDK 17, never the repo JDK 25) | [`ezkey_mobile/AGENTS.md`](ezkey_mobile/AGENTS.md) § *Android debug build*: `./scripts/build-install-debug-clean.sh` |
| Work inside a module | that module's `AGENTS.md` (Admin UI, Admin API, Core, Mobile, public site…) |
| Experimental PAM (host sshd lab, not containerised) | [`ezkey-pam/README.md`](ezkey-pam/README.md) |
| Dismissing or accepting a dependency vulnerability | [`dependabot/README.md`](product-docs/global/hygiene/dependabot/README.md) § *Check upstream before dismissing a vulnerability* |
| Answering reports (14-day promise) | [`report-intake/README.md`](product-docs/global/hygiene/report-intake/README.md) |
| Startup or HA, locks with transactions, removing an exposed field, test helpers, CI caches, CI that looks random | [`stabilization-lessons/README.md`](product-docs/global/hygiene/stabilization-lessons/README.md) |
| Strategic positioning, public copy | [`PROJECT_POSITIONING.md`](docs/PROJECT_POSITIONING.md) |
| Public alpha label, what is deployed | [`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](docs/VERSIONING_AND_DEPLOY_TRACEABILITY.md), [`DEPLOYED.md`](docs/lightsail/community/DEPLOYED.md) |
| Test hosts, stack ports, ngrok | [`LOCAL_STACK_PORTS.md`](docs/LOCAL_STACK_PORTS.md) (Rootbeer runs one fixed-port stack; one ngrok session at a time) |
| Verification functions (reviews, walk, decisions) | [`team-roles.md`](product-docs/global/team-roles.md) |
| Publishing the methodology product | [`product-docs/GOVERNANCE.md`](product-docs/GOVERNANCE.md) § Methodology publication boundary |

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

Opening a GitHub issue: use the [`github-issue`](.claude/skills/github-issue/SKILL.md) skill (five
mandatory label groups, verified after create). An unlabeled issue is incomplete.

## Platforms

- **Windows, PowerShell agent shell (Cursor):** follow `.cursor/rules/windows-agent-shell.mdc`
  (Git Bash launcher, `git-commit.sh` / `git-pr.sh` file-based flow, JDK 25 path). Never bare
  `bash` (it may resolve to WSL).
- **Git Bash, macOS, Linux:** run repo scripts directly; plain `git commit` with a HEREDOC and
  `gh pr create --body-file` work.
- **Parallel worktrees:** to keep local Maven artifacts apart, copy `.mvn/maven.config.example` to
  `.mvn/maven.config` (gitignored) and set a revision suffix such as `-Drevision=0.2.0-wt1`.
