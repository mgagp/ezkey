# GitHub CI/CD and repository-settings best practices — Ezkey inventory and plan

**Audience:** eventual external evaluator comparing Ezkey to generally accepted GitHub /
OpenSSF practice for a multi-ecosystem public OSS security product (Java/Spring + Maven,
TypeScript/React Admin UI, React Native/Android, Python CLI, Docker).

**Scope:** documentation and prioritization only. This file does **not** change workflows or
repository Settings.

**Decision (2026-10-14):** Keep a living inventory and phased improvement plan in this document
(linked from [`docs/README.md`](../README.md) and [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md)
§ Continuous Integration) so evaluators and maintainers share one source of truth for CI/governance
parity — without inventing a methodology `I-*` / `TB-*` for routine hygiene.

**Inventory snapshot:** 2026-10-14 (repo `mgagp/ezkey`, default branch `main`). Items that could
not be verified via API or committed files are marked **to confirm by Marc in Settings**.

**Prior CI baseline:** PR [#697](https://github.com/mgagp/ezkey/pull/697) (merged 2026-10-07)
introduced path-filtered Admin UI / Maven / mobile checks and aggregate **`ci-gate`**. Living
operator notes: [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) § Continuous Integration. CodeQL advanced
setup and scanning policy: PR [#805](https://github.com/mgagp/ezkey/pull/805) (merged 2026-10-14)
plus [`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) § Code scanning policy.

---

## 1. Current posture inventory

### 1.1 Committed workflows and config

| Asset | Present? | Notes (verified in tree) |
|-------|----------|--------------------------|
| [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) | Yes | Aggregate CI: path filter → `admin-ui` / `backend` / `mobile` / `cli-python` → **`ci-gate`**. Top-level `permissions: contents: read`. Concurrency cancel-in-progress on PRs. Job timeouts. SHA-pinned third-party actions with `# vX.Y.Z` comments. `persist-credentials: false` on checkouts. No `pull_request_target`. |
| [`.github/workflows/ezkey-mobile-unit-tests.yml`](../../.github/workflows/ezkey-mobile-unit-tests.yml) | Yes | Reusable: Yarn `validate:ci`, license snapshot, Android JVM unit tests, **16 KB APK alignment**. JDK 17 for Android. |
| [`.github/workflows/maven-dependency-submission.yml`](../../.github/workflows/maven-dependency-submission.yml) | Yes | Submits Maven graph on `main` (JDK 25 + `checkstyle-config` bootstrap). Job `contents: write`. Not part of `ci-gate`. |
| [`.github/workflows/codeql.yml`](../../.github/workflows/codeql.yml) | Yes (since #805) | Advanced CodeQL: java-kotlin, javascript-typescript, python, c-cpp, actions; weekly cron; SHA-pinned `codeql-action`; local LogSanitizer model pack. |
| [`.github/codeql/codeql-config.yml`](../../.github/codeql/codeql-config.yml) | Yes | `paths-ignore: ezkey-tests/**`. |
| [`.github/dependabot.yml`](../../.github/dependabot.yml) | Yes | Daily/weekly version updates: Maven, npm (Admin UI, SDK, mobile, product-docs site), pip, github-actions, gradle (Android), docker, docker-compose. Cooldown + groups. Security updates not delayed by cooldown (Dependabot product behavior). |
| `.github/CODEOWNERS` | **No** | File absent. |
| [`.github/pull_request_template.md`](../../.github/pull_request_template.md) | Yes | Checklist includes `ci-gate`, CodeQL alerts, threads, secrets. |
| `.github/ISSUE_TEMPLATE/` | **No** | Directory absent. |
| [`SECURITY.md`](../../SECURITY.md) | Yes | Private vulnerability reporting URL; lists CI / CodeQL / Dependabot / secret scanning practices. Last updated 2026-10-08 (pre-#805 Advanced wording in places). |
| [`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) | Yes | Honest product security claims + § Code scanning policy (SLAs, dismiss rules). |
| Scorecard / dependency-review / SBOM / provenance / release / harden-runner workflows | **No** | Not in `.github/workflows/`. |

**Actions hardening already in place (good):** least-privilege default `contents: read`; no
`pull_request_target`; SHA-pinned actions; concurrency groups; timeouts; credential persistence
off on checkout. Aligns with OpenSSF Scorecard **Token-Permissions**, **Pinned-Dependencies**, and
**Dangerous-Workflow** guidance ([Scorecard checks](https://github.com/ossf/scorecard/blob/main/docs/checks.md)).

**Quality gates in `ci-gate` today:** Spotless + Checkstyle + Maven unit tests (excl. Docker
`ezkey-tests`); Admin UI tsc/lint/Vitest/build; mobile Yarn validate + Android JVM tests + 16 KB
gate; Python CLI pytest on 3.12/3.14. **Not** in PR CI: Playwright, full Docker functional suite,
doctor/pentest hygiene, Jacoco coverage thresholds, Android `lint`, SonarQube (mentioned as
aspiration in `DEVELOPMENT.md` but not wired).

### 1.2 Repository ruleset and merge settings (API-verified 2026-10-14)

| Setting | Observed | Source |
|---------|----------|--------|
| Ruleset | **Basic protection**, `enforcement: active`, targets `~DEFAULT_BRANCH` | `GET /repos/mgagp/ezkey/rulesets/7558701` |
| Rules | `deletion`, `non_fast_forward`, `required_status_checks` with context **`ci-gate`** only | Same |
| Strict up-to-date | `strict_required_status_checks_policy: false` | Same |
| Bypass | `RepositoryRole` `actor_id: 5`, `bypass_mode: always` (typical Admin role bypass) | Same |
| Required PR / approvals / CODEOWNERS review / signed commits / linear history | **Not** present on this ruleset | Same |
| Classic branch protection API | 403 from this token | **to confirm by Marc in Settings** if any classic rules remain |
| `allow_squash_merge` / `allow_merge_commit` / `allow_rebase_merge` | All **true** | Repo REST |
| `delete_branch_on_merge` | **false** | Repo REST |
| `allow_auto_merge` / `allow_update_branch` | **false** | Repo REST |
| Deploy environments | One env: `copilot`, **no** protection rules | `GET …/environments` |

Note: [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) still says `ci-gate` is “intended to become
required”; the ruleset already requires it (updated 2026-10-07). Treat the ruleset as authoritative.

### 1.3 Security product features (mixed verification)

| Feature | Evidence | Status |
|---------|----------|--------|
| **Private vulnerability reporting** | `GET …/private-vulnerability-reporting` → `enabled: true` | **Verified on** |
| **Dependency graph** | Dedicated Maven submission workflow exists; Insights expected; open PR [#798](https://github.com/mgagp/ezkey/pull/798) documents keeping graph on | **Assumed on** (workflow would fail if off); **to confirm by Marc** that Settings still shows Enabled |
| **Automatic dependency submission** | Docs say Disabled 2026-10-08; Actions still lists dynamic workflow type `Automatic Dependency Submission` | **to confirm by Marc in Settings** (listing ≠ enabled) |
| **Dependabot version updates** | `dependabot.yml` + open Dependabot PRs + dynamic “Dependabot Updates” workflow | **Verified on** |
| **Dependabot alerts / security updates** | `SECURITY.md` claims on; `GET …/dependabot/alerts` returned *“Dependabot alerts are disabled”* (2026-10-14) with a scoped token | **Conflict — to confirm by Marc in Settings** |
| **Secret scanning** | Claimed in `SECURITY.md`; alerts API 403 | **to confirm by Marc in Settings** |
| **Secret scanning push protection** | Claimed in `SECURITY.md`; not API-visible here | **to confirm by Marc in Settings** |
| **Code scanning — Default vs Advanced** | Committed advanced workflow (#805). Actions still lists **both** `.github/workflows/codeql.yml` **and** `dynamic/github-code-scanning/codeql`. Policy doc: SARIF upload may fail while default remains on | **to confirm by Marc:** switch CodeQL to **Advanced** and disable default setup |
| **Code scanning as required check / ruleset `code_scanning` rule** | Not on ruleset `7558701` | Absent (optional Phase 2) |
| **Security advisory / SECURITY.md** | Present; public alpha honesty | Good |

### 1.4 Process and hygiene (repo memory)

| Practice | Ezkey today |
|----------|-------------|
| Merge bar | Maintainer go + `ci-gate` green + CodeQL triage expectations (`AGENTS.md`, PR template) |
| Peer review | Soft process (“Manual Review”); **not** ruleset-enforced |
| Dependabot curation | `dependabot-curated` skill + campaign notes |
| CodeQL triage | Hygiene lane `product-docs/global/hygiene/code-scanning/` + SLAs in `SECURITY_POSTURE` |
| Deploy traceability | Git SHA ledger ([`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md)); **no** GitHub Releases / signed publish train yet |
| Labels | Rich lane/type/component/priority/status set (verified via `gh label list`) |
| Stale bot | Not present |

### 1.5 Role owners used in the plan

From [`product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md):

| Name | Plan use |
|------|----------|
| **Fred** | Hygiene / CI workflow and docs |
| **Christophe** | Security review (CodeQL, secret scanning, supply chain) |
| **Edgar** | Deploy / environments / OIDC / release surfaces |
| **Justin** | Mobile / Android build gates |
| **Marc** | Repository Settings admin toggles and merge authority |

---

## 2. Best-practice reference (external norms)

Sources (non-exhaustive; prefer primary docs over blog summaries):

| Area | References |
|------|------------|
| Branch rulesets / required checks | [About rulesets](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets), [Available rules](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets) |
| CODEOWNERS / required reviewers | [About code owners](https://docs.github.com/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners); [Required reviewer rule GA](https://github.blog/changelog/2026-02-17-required-reviewer-rule-is-now-generally-available/) |
| Actions hardening | GitHub Docs: [Security hardening for GitHub Actions](https://docs.github.com/actions/security-guides/security-hardening-for-github-actions); OpenSSF Scorecard **Token-Permissions**, **Pinned-Dependencies**, **Dangerous-Workflow** ([checks.md](https://github.com/ossf/scorecard/blob/main/docs/checks.md)); StepSecurity harden-runner (ecosystem norm for egress policy on public runners) |
| Supply chain | [Dependabot](https://docs.github.com/code-security/dependabot); [Dependency review](https://docs.github.com/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review); [SBOM](https://docs.github.com/code-security/supply-chain-security/understanding-your-software-supply-chain/about-supply-chain-security); SLSA ([slsa.dev](https://slsa.dev/)) provenance / attestations |
| Code / secret scanning | [Code scanning](https://docs.github.com/code-security/code-scanning); [Secret scanning](https://docs.github.com/code-security/secret-scanning); OWASP [CI/CD Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html) |
| Releases / OIDC | GitHub Docs: [Using OpenID Connect with reusable workflows](https://docs.github.com/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect); environment protection rules |
| Scorecard badge | [OpenSSF Scorecard](https://github.com/ossf/scorecard) |

Evaluator-facing bar for a project like Ezkey (public alpha, security-sensitive, multi-language):

1. Protected default branch with required CI + no force-push / no deletion.
2. Documented disclosure path (`SECURITY.md` + private reporting).
3. Least-privilege, SHA-pinned Actions; no dangerous `pull_request_target` patterns.
4. Dependabot (version + alerts) across ecosystems; dependency graph populated.
5. CodeQL (or equivalent SAST) on all first-party languages with triage policy.
6. Secret scanning + push protection.
7. Honest gap between “lab / alpha” and production release signing (SLSA, attestations) — do not fake maturity.

Ezkey already meets much of (1)–(5) in spirit; Settings confirmation and a few high-value
controls close the evaluator gap without overbuilding.

---

## 3. Gap table

Effort: **S** = settings toggle or small docs/config; **M** = one workflow or ruleset change +
tests; **L** = multi-surface program (release signing, full SLSA, coverage culture).

Value: evaluator / supply-chain / operator impact (High / Med / Low).

| # | Practice | Ezkey today | Gap | Effort | Value | Priority |
|---|----------|-------------|-----|--------|-------|----------|
| G1 | CodeQL Advanced only (no default conflict) | Advanced workflow merged (#805); default dynamic CodeQL still listed | Disable default setup so SARIF uploads and policy work | S | High | P0 |
| G2 | Dependabot **alerts** + security updates | Version updates strong; alerts API said disabled vs `SECURITY.md` claim | Enable alerts if off; align `SECURITY.md` | S | High | P0 |
| G3 | Secret scanning + push protection | Claimed; not API-verified | Confirm on; enable if off | S | High | P0 |
| G4 | Dependency graph on; automatic submission off | Documented; #798 open; automatic still listed | Confirm Settings; merge #798 docs | S | High | P0 |
| G5 | Ruleset: require PR before merge + ≥1 approval | Status checks only; admin bypass always | Add pull-request + approval rules (solo-maintainer-aware) | S–M | High | P1 |
| G6 | `CODEOWNERS` + optional required-reviewer paths | Absent | Add lightweight CODEOWNERS (`@mgagp`); path rules for crypto/workflows later | S–M | Med | P1 |
| G7 | Dependency review Action on PRs | Absent | Add `dependency-review-action` (fail on high/critical) | M | High | P1 |
| G8 | Update `SECURITY.md` for Advanced CodeQL | Still says “default setup, non-blocking” | Align with #805 policy | S | Med | P1 |
| G9 | `delete_branch_on_merge` | false | Enable for hygiene | S | Low | P2 |
| G10 | Prefer squash-only (or document merge strategy) | All three merge methods on | Pick one preferred; disable others or document | S | Low | P2 |
| G11 | Strict status checks (branch up to date) | `strict: false` | Enable when PR volume / flake tolerance allows | S | Med | P2 |
| G12 | Scorecard workflow + badge | Absent | Scheduled Scorecard; publish badge in README | M | Med | P2 |
| G13 | Harden-runner (egress audit) | Absent | Optional on CI jobs; noise vs benefit for public OSS | M | Med | P2 |
| G14 | Container image CVE scanning | Docker Dependabot version bumps only | Trivy/Grype job or registry scan; not a substitute for alerts | M | Med | P2 |
| G15 | SBOM generation (CycloneDX/SPDX) | Absent | Generate on `main` / release tags; attach artifacts | M | Med | P2 |
| G16 | Pin Docker base digests | Floating tags (`eclipse-temurin:25-jre-alpine`, etc.) | Digest pins + Dependabot digest updates | M | Med | P2 |
| G17 | Android Lint in CI | Unit tests + 16 KB only | Add `lintDebug` (or release lint) to mobile workflow | M | Med | P2 |
| G18 | Coverage gate (Jacoco/Vitest) | Jacoco in POM; “90%” aspirational in docs; not CI-enforced | Either enforce a realistic floor or remove aspirational claim | M | Med | P2 |
| G19 | Signed commits | Not required | Optional ruleset rule; costly for AI agents / Dependabot | S | Low | P3 |
| G20 | GitHub Releases + signed artifacts / image attestations (SLSA) | Explicitly not implemented; SHA ledger only | Needed when publishing packages — not for alpha SHA deploys | L | High *when releasing* | P3 / later |
| G21 | Deploy environments + OIDC (no long-lived cloud secrets) | Manual Lightsail/Pages; `copilot` env empty | When automation lands: environments + OIDC | L | High *when automating deploy* | P3 / later |
| G22 | Issue templates | Absent | Optional bug/security redirect templates | S | Low | P3 |
| G23 | Stale bot / stale policy | Absent | Usually noise for small maintainer set | S | Low | Skip |
| G24 | CodeQL as blocking required check | Policy says no merge with new high/critical; not ruleset-enforced | Ruleset `code_scanning` or required check after Advanced stable | M | Med | P2 |
| G25 | `pull_request_target` / over-privileged tokens | Already avoided | Keep regression watch in Scorecard / review | — | — | Maintain |
| G26 | Query suites / PR gating polish | Default queries; weekly cron | Consider `security-extended` later; watch noise | M | Low–Med | P3 |

---

## 4. Phased plan

### Phase 1 — Quick wins, high value (Settings + honesty)

Owner suggestions in parentheses. **Marc** required wherever Settings is named.

1. **Switch CodeQL to Advanced; disable default setup** (Marc + Christophe verify; Fred docs).
   - Unblocks #805 SARIF upload; removes dual-analyzer confusion.
2. **Confirm / enable Dependabot alerts and security updates** (Marc; Fred align `SECURITY.md`).
3. **Confirm / enable secret scanning and push protection** (Marc; Christophe spot-check).
4. **Confirm Dependency graph Enabled; Automatic dependency submission Disabled** (Marc; land or supersede [#798](https://github.com/mgagp/ezkey/pull/798)).
5. **Refresh `SECURITY.md` “practices in use”** to match Advanced CodeQL + verified toggles (Fred).
6. **Fix stale “ci-gate intended to become required” wording** in `DEVELOPMENT.md` (Fred) —
   ruleset already requires it.

Exit criteria: Settings checklist below signed off by Marc; docs match reality; no default/advanced
CodeQL conflict on a green `main` CodeQL run.

### Phase 2 — Structural parity (ruleset + supply-chain PR gate)

1. **Ruleset expansion** (Marc): require pull request before merge; require ≥1 approving review
   (accept that Marc/admin may retain bypass for emergency; document when bypass is OK).
2. **Add `.github/CODEOWNERS`** defaulting to `@mgagp` (Fred); optional path owners for
   `.github/workflows/**`, `ezkey-core-security/**`, crypto docs (Christophe review).
3. **Dependency review Action** on `pull_request` (Fred implement; Christophe severity policy).
4. **OpenSSF Scorecard** scheduled workflow + README badge (Fred); triage first report with
   Christophe (do not chase 10/10).
5. **Optional:** enable `delete_branch_on_merge`; prefer squash merges; consider strict status
   checks once flake rate is known (Marc).
6. **Mobile:** add Android Lint job or step (Justin + Fred).
7. **Container scanning** job on Dockerfiles / compose image refs (Edgar + Fred); keep separate
   from Dependabot version PRs.
8. **CodeQL required** via ruleset or status once Advanced is stable and alert noise is tolerable
   (Marc + Christophe) — or keep policy-only if noise is high (honest alpha choice).

Exit criteria: evaluator sees enforced PR + CI + dependency review + Scorecard signal; mobile lint
present; Settings documented.

### Phase 3 — Release-shaped supply chain (when publishing justifies it)

Defer until GitHub Releases / Maven Central / Play / signed images are real product goals
([`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md)):

1. SBOM attach on release tags (Fred).
2. Image/provenance attestations (SLSA-oriented) and digest-pinned runtime images (Edgar + Fred).
3. GitHub Environments with protection rules + OIDC to cloud (Edgar); retire long-lived deploy
   secrets where possible.
4. Signed release artifacts (Christophe security opinion + Marc).
5. Coverage floors that reflect reality (drop or enforce the “90%” aspiration) (Fred + Patrick craft).

### Diminishing returns — deliberately not doing (now)

| Item | Reason |
|------|--------|
| Mandatory signed commits for all contributors | Breaks Dependabot, many agents, and low-friction lab flow; Scorecard Branch-Protection tier is optional for solo public alpha |
| Full SLSA L3 + always-on provenance before first published artifact | No release train yet; SHA ledger is the honest deploy identity |
| SonarQube / enterprise SAST suite parallel to CodeQL | Accidental complexity; CodeQL + java-doctor/semgrep hygiene lanes already cover intent |
| Stale-bot auto-close | High false-close risk; labels + human triage suffice |
| Making Playwright / Docker `ezkey-tests` part of every PR `ci-gate` | Cost/time; covered by `quality-gate` / clean-start when needed |
| Zero Scorecard findings / 10/10 campaign | Hygiene theater; triage actionable checks only |
| Harden-runner on every job day-one | Useful later; Phase 1 Settings + dependency review give more evaluator signal per hour |
| Org-wide rulesets / GitHub Enterprise-only controls | Personal/`mgagp` public lab; out of scope |
| Issue-form bureaucracy | Optional templates later; security path already in `SECURITY.md` |

---

## 5. Settings Marc must confirm (checklist)

Copy into a maintainer note; check each in **Settings → Code security / Rules / General**:

- [ ] **CodeQL:** Advanced setup **on**; default setup **off** (required for #805 workflow success).
- [ ] **Dependency graph:** Enabled.
- [ ] **Automatic dependency submission:** Disabled (dedicated Maven workflow feeds the graph).
- [ ] **Dependabot alerts:** Enabled (reconcile with 2026-10-14 API “disabled” signal).
- [ ] **Dependabot security updates:** Enabled.
- [ ] **Secret scanning:** Enabled.
- [ ] **Push protection** (secret scanning): Enabled.
- [ ] **Private vulnerability reporting:** Enabled (API already `true` — reconfirm UI).
- [ ] **Ruleset “Basic protection”:** still requires **`ci-gate` only** for status checks (do not add path-filtered job names).
- [ ] Whether **classic branch protection** on `main` still exists alongside the ruleset.
- [ ] Desired merge button set (squash / merge commit / rebase) and **delete branch on merge**.
- [ ] Whether Admin **bypass** on the ruleset remains intentional for emergency merges.

Anything not checked stays marked **unverified** in future inventory refreshes — never guess.

---

## 6. Traceability and refresh

| Doc / artifact | Role |
|----------------|------|
| This file | Inventory + gap table + phased plan (evaluator + maintainer) |
| [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) § Continuous Integration | Operator how-to for running CI locally and what `ci-gate` means |
| [`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) § Code scanning policy | CodeQL SLAs and dismiss rules |
| [`SECURITY.md`](../../SECURITY.md) | Public disclosure + high-level practices |
| [`product-docs/global/hygiene/code-scanning/`](../../product-docs/global/hygiene/code-scanning/) | Triage campaign notes |
| [`product-docs/global/hygiene/dependabot/`](../../product-docs/global/hygiene/dependabot/) | Dependabot curation |
| PR #697 | Original `ci-gate` introduction |
| PR #805 | CodeQL Advanced + policy |

**Refresh rule:** when Settings or workflows change, update §1 and the gap row in the **same PR**
(repo-as-memory). Do not fork a second “CI audit” without linking here.

---

*Inventory date: 2026-10-14. Docs-only; no workflow changes in the introducing PR.*
