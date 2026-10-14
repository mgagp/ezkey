# GitHub CI/CD and repository-settings best practices — Ezkey inventory and plan

**Audience:** eventual external evaluator comparing Ezkey to generally accepted GitHub /
OpenSSF practice for a multi-ecosystem public OSS security product (Java/Spring + Maven,
TypeScript/React Admin UI, React Native/Android, Python CLI, Docker).

**Scope:** documentation and prioritization only, except where a follow-up PR explicitly lands a
named workflow tweak (e.g. `persist-credentials` on CodeQL checkout). This file does **not**
change repository Settings.

**Decision (2026-10-14, PR [#808](https://github.com/mgagp/ezkey/pull/808)):** Keep a living
inventory and phased improvement plan in this document (linked from
[`docs/README.md`](../README.md) and [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) § Continuous
Integration) so evaluators and maintainers share one source of truth for CI/governance parity —
without inventing a methodology `I-*` / `TB-*` for routine hygiene.

**Inventory snapshot:** 2026-10-14 (repo `mgagp/ezkey`, default branch `main`). Items that could
not be verified via API or committed files are marked **to confirm by Marc in Settings**.
**Refresh cadence:** re-check §1 during the Monday `dependabot-curated` full pass (and whenever
Settings or workflows change in the same PR).

**Prior CI baseline:** PR [#697](https://github.com/mgagp/ezkey/pull/697) (merged 2026-10-07)
introduced path-filtered Admin UI / Maven / mobile checks and aggregate **`ci-gate`**. Living
operator notes: [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) § Continuous Integration. CodeQL advanced
setup and scanning policy: PR [#805](https://github.com/mgagp/ezkey/pull/805) (merged 2026-10-14,
head `f26cbe55`) plus [`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) § Code scanning policy —
five language analyses uploaded on `main` at that commit.

---

## 1. Current posture inventory

### 1.1 Committed workflows and config

| Asset | Present? | Notes (verified in tree) |
|-------|----------|--------------------------|
| [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) | Yes | Aggregate CI: path filter → `admin-ui` / `backend` / `mobile` / `cli-python` → **`ci-gate`**. Top-level `permissions: contents: read`. Concurrency cancel-in-progress on PRs. Job timeouts. Third-party actions SHA-pinned; version comments are usually `# vX.Y.Z`, but some are major-only (`# v7` on `setup-node` / `upload-artifact` at `ci.yml` ~L134 and ~L224). `persist-credentials: false` on checkouts in this workflow. No `pull_request_target`. |
| [`.github/workflows/ezkey-mobile-unit-tests.yml`](../../.github/workflows/ezkey-mobile-unit-tests.yml) | Yes | Reusable: Yarn `validate:ci`, license snapshot, Android JVM unit tests, **16 KB APK alignment**. **JDK 17** for Android (LTS toolchain policy below). |
| [`.github/workflows/maven-dependency-submission.yml`](../../.github/workflows/maven-dependency-submission.yml) | Yes | See §1.1.1. Not part of `ci-gate`. |
| [`.github/workflows/codeql.yml`](../../.github/workflows/codeql.yml) | Yes (since #805) | Advanced CodeQL matrix: `java-kotlin`, `javascript-typescript`, `python`, `c-cpp`, `actions` (`codeql.yml` ~L37–45). **Swift intentionally excluded.** Weekly cron; SHA-pinned `codeql-action`; local LogSanitizer model pack. |
| [`.github/codeql/codeql-config.yml`](../../.github/codeql/codeql-config.yml) | Yes | `paths-ignore: ezkey-tests/**`. |
| [`.github/dependabot.yml`](../../.github/dependabot.yml) | Yes | See §1.1.2. |
| `.github/CODEOWNERS` | **No** | File absent. |
| [`.github/pull_request_template.md`](../../.github/pull_request_template.md) | Yes | Checklist includes `ci-gate`, CodeQL alerts, threads, secrets. |
| `.github/ISSUE_TEMPLATE/` | **No** | Directory absent (see diminishing-returns). |
| [`SECURITY.md`](../../SECURITY.md) | Yes | Private vulnerability reporting URL; lists CI / CodeQL / Dependabot / secret scanning practices. Last updated **2026-10-14** (#808). |
| [`docs/SECURITY_POSTURE.md`](../SECURITY_POSTURE.md) | Yes | Honest product security claims + § Code scanning policy (SLAs, dismiss rules). |
| Scorecard / dependency-review / SBOM / provenance / release / harden-runner workflows | **No** | Not in `.github/workflows/`. |

#### 1.1.1 Maven dependency submission

Workflow [`.github/workflows/maven-dependency-submission.yml`](../../.github/workflows/maven-dependency-submission.yml):

- **Triggers:** `push` to `main` when paths match `pom.xml`, `**/pom.xml`, `checkstyle-config/**`,
  `.mvn/**`, or the workflow file itself; plus `workflow_dispatch`.
- **Permissions:** workflow top-level `permissions: {}`; job `submit-maven` sets
  `contents: write` for graph upload. Checkouts use `persist-credentials: false`.
- **Bootstrap:** JDK **25** + install unpublished `checkstyle-config` (same as backend CI).
- **Implication for “main green”:** a docs-only push to `main` does **not** re-run this
  workflow. Graph freshness is judged on the last POM/`checkstyle-config`/`.mvn` change (or a
  manual dispatch), not on every green `ci-gate` on `main`.

#### 1.1.2 Dependabot version updates (`.github/dependabot.yml`)

| Ecosystem | Directory / paths | Cadence | Limit | Cooldown |
|-----------|-------------------|---------|-------|----------|
| Maven | `/` | **daily** | 10 | default/patch/minor 3d; major 7d |
| npm | `/ezkey-admin-ui`, `/ezkey-sdk/javascript`, `/ezkey_mobile` | **daily** | 10 | same |
| github-actions | `/` | **weekly** | 10 | default 3d only |
| pip | `/ezkey-cli-python` | **weekly** | 10 | 3d / major 7d |
| npm | `/product-docs/site` | **weekly** | 10 | 3d / major 7d |
| gradle | `/ezkey_mobile/android` | **weekly** | 10 | 3d / major 7d |
| docker | `/docker`, `/docker/bootstrap-init`, `/docker/javamelody`, `/ezkey-admin-ui/docker` | **weekly** | 10 | default 3d |
| docker-compose | `/docker`, `/experimental-hybrid/lightsail` | **weekly** | 10 | default 3d |

**Ignores (line refs in `dependabot.yml`):**

- `@types/node` semver-major — Admin UI (~L48–51) and SDK (~L80–84); track root `.nvmrc` Node 24.
- Gradle: `com.android.tools.build:gradle`, `org.jetbrains.kotlin:*`, `gradle-wrapper`, and all
  semver-majors (~L214–219). AGP / Kotlin / wrapper follow React Native upgrades, not Dependabot
  (~L200–201).
- Docker / docker-compose: all semver-majors (~L250–252, ~L305–307).
- Docker `maven` image: non-LTS Temurin ranges (JDK 26–28 style) from the #764 / #762 lineage
  (~L253–276).

**Groups:** Maven SpotBugs/Checkstyle/Rewrite; Admin UI eslint/typescript/vite; mobile tooling /
RN-core / vision-camera / navigation; site / android-gradle / docker / compose minor-patch groups
(see file). **Orval is not grouped** (solo PRs → Orval risk grid).

**Known gap — Android Gradle CVE alerts:** version updates only; native Android CVE alerts are
**not** produced because the dependency graph does not capture these Gradle deps
(`dependabot.yml` ~L196–201; automatic Gradle submission declined for `includeBuild` /
`node_modules` coupling).

**Stale comment flag:** `dependabot.yml` ~L170–174 still says `product-docs/site` `package-lock.json`
is gitignored so alerts cover direct deps only. That is **stale since [#763](https://github.com/mgagp/ezkey/pull/763)** —
the lockfile is committed and root `.gitignore` has `!product-docs/site/package-lock.json`. Fix the
comment in a Dependabot/docs hygiene PR (not blocking this inventory).

Security updates are not delayed by cooldown (Dependabot product behavior).

#### 1.1.3 LTS toolchain policy (CI-enforced)

| Surface | Pin | Where |
|---------|-----|-------|
| Node.js | **24** LTS | Root [`.nvmrc`](../../.nvmrc); Admin UI + mobile CI `node-version-file` |
| Backend JDK | **25** LTS (Temurin) | `ci.yml` backend + Maven dependency submission; `scripts/build.sh` |
| Android / Gradle JDK | **17** | `ezkey-mobile-unit-tests.yml` (not JDK 25) |

#### 1.1.4 Actions hardening notes

**Already in place (good):** least-privilege default `contents: read` on CI; no
`pull_request_target`; SHA-pinned actions; concurrency groups; timeouts; `persist-credentials: false`
on checkouts in `ci.yml`, mobile, and Maven submission. CodeQL checkout historically omitted that
flag (fixed in #808 follow-up). Aligns with OpenSSF Scorecard **Token-Permissions**,
**Pinned-Dependencies**, and **Dangerous-Workflow**
([checks.md](https://github.com/ossf/scorecard/blob/main/docs/checks.md)).

**Quality gates in `ci-gate` today:** Spotless + Checkstyle + Maven unit tests (excl. Docker
`ezkey-tests`); Admin UI tsc/lint/Vitest/build; mobile Yarn validate + Android JVM tests + 16 KB
gate; Python CLI pytest on 3.12/3.14. **Not** in PR CI: Playwright, full Docker functional suite,
doctor/pentest hygiene, Jacoco coverage thresholds, Android `lint`, SonarQube (mentioned as
aspiration in `DEVELOPMENT.md` but not wired — see G18 hygiene).

### 1.2 Repository ruleset and merge settings (API-verified 2026-10-14)

| Setting | Observed | Source |
|---------|----------|--------|
| Ruleset | **Basic protection**, `enforcement: active`, targets `~DEFAULT_BRANCH` | `GET /repos/mgagp/ezkey/rulesets/7558701` |
| Rules | `deletion`, `non_fast_forward`, `required_status_checks` with context **`ci-gate`** only | Same |
| Strict up-to-date | `strict_required_status_checks_policy: false` | Same |
| Bypass | `RepositoryRole` `actor_id: 5`, `bypass_mode: always` (Admin role bypass) | Same |
| Required PR / approvals / CODEOWNERS review / signed commits / linear history | **Not** present on this ruleset | Same |
| Classic branch protection API | 403 from this token | **to confirm by Marc in Settings** if any classic rules remain |
| `allow_squash_merge` / `allow_merge_commit` / `allow_rebase_merge` | All **true** | Repo REST |
| `delete_branch_on_merge` | **false** | Repo REST |
| `allow_auto_merge` / `allow_update_branch` | **false** | Repo REST |
| Deploy environments | One env: `copilot`, **no** protection rules | `GET …/environments` |

**Ruleset vs bot autonomy:** Required status is **`ci-gate` only** — path-filtered jobs may skip;
Dependabot and agent PRs merge when `ci-gate` is green and Marc (or Admin bypass) merges. Agents
and Dependabot **cannot** self-approve. Admin bypass (`actor_id: 5`, always) is the emergency /
solo-maintainer escape hatch — **to confirm by Marc** that it remains intentional. Soft merge bar
(#787) still applies in process even when bypass is available.

**`allow_auto_merge`:** currently **false**. Options for later: enable for **T1-only** Dependabot
lots under `dependabot-curated`, or keep an **explicit decision not to enable** (preferred until
alerts + dependency-review land). Record the choice in the Monday campaign note when decided.

### 1.3 Security product features (mixed verification)

| Feature | Evidence | Status |
|---------|----------|--------|
| **Private vulnerability reporting** | `GET …/private-vulnerability-reporting` → `enabled: true` | **Verified on** |
| **Dependency graph** | Was **disabled** (Maven submission failed on runs [38298454193](https://github.com/mgagp/ezkey/actions/runs/38298454193) and [38447903477](https://github.com/mgagp/ezkey/actions/runs/38447903477)). Marc **re-enabled** 2026-10-13. Cause + operator posture documented in draft PR [#798](https://github.com/mgagp/ezkey/pull/798) head **`5079f27f`** (awaiting Marc merge). | **G4 done** (Settings); only #798 docs remain to merge |
| **Automatic dependency submission** | Docs say Disabled 2026-10-08; Actions still lists dynamic workflow type | **to confirm by Marc in Settings** (listing ≠ enabled) |
| **Dependabot version updates** | `dependabot.yml` + open Dependabot PRs + dynamic “Dependabot Updates” workflow | **Verified on** |
| **Dependabot alerts / security updates** | `SECURITY.md` announces them; `GET …/dependabot/alerts` returned *“Dependabot alerts are disabled”* (2026-10-14) | **Conflict — Marc’s call in Settings (new P0)** |
| **Secret scanning** | Claimed in `SECURITY.md`; alerts API 403 | **to confirm by Marc in Settings** |
| **Secret scanning push protection** | Claimed in `SECURITY.md`; not API-visible here | **to confirm by Marc in Settings** |
| **Code scanning — Advanced** | #805 merged; five analyses uploaded on `main` at `f26cbe55` | **G1 done** |
| **CodeQL open-alert baseline on `main`** | Placeholder for Christophe’s count | **_(Christophe to provide)_** |
| **Code scanning as required check / ruleset `code_scanning` rule** | Not on ruleset `7558701` | Absent (optional Phase 2) |
| **Security advisory / SECURITY.md** | Present; public alpha honesty | Good |

### 1.4 Process and hygiene (repo memory)

| Practice | Ezkey today |
|----------|-------------|
| Merge bar | Maintainer go + `ci-gate` green + **zero open CodeQL alerts** + **zero unresolved review threads** (recorded in [#787](https://github.com/mgagp/ezkey/pull/787); also PR template / `AGENTS.md`) |
| Peer review | Soft process (“Manual Review”); **not** ruleset-enforced (solo: author cannot approve own PR) |
| Dependabot curation | [#678](https://github.com/mgagp/ezkey/pull/678) risk model: tiers **T1–T4**; Orval **O0–O3** grid; Docker + Android Gradle under HITL until [#702](https://github.com/mgagp/ezkey/pull/702); labels `deferred:later-train` and `deferred:rn-upgrade`; weekday **autonomous light** passes for **T1–T3** and Orval **O0/O1**. Canon: [`product-docs/global/hygiene/dependabot/`](../../product-docs/global/hygiene/dependabot/) + skill `dependabot-curated` |
| CodeQL triage | Hygiene lane `product-docs/global/hygiene/code-scanning/` + SLAs in `SECURITY_POSTURE` |
| Deploy traceability | Git SHA ledger ([`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md)); **no** GitHub Releases / signed publish train yet |
| Labels | Rich lane/type/component/priority/status set (verified via `gh label list`) |
| Stale bot | Not present (diminishing returns) |

### 1.5 Role owners used in the plan

Plan owner suggestions use the names in
[`product-docs/global/team-roles.md`](../../product-docs/global/team-roles.md)
(Fred = hygiene/CI, Christophe = security, Edgar = deploy/edge, Justin = Android builds, Marc =
Settings + merge authority). Do not duplicate that table here.

---

## 2. Best-practice reference (external norms)

Primary references:

- GitHub Docs: [About rulesets](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets),
  [Available rules](https://docs.github.com/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets),
  [Security hardening for GitHub Actions](https://docs.github.com/actions/security-guides/security-hardening-for-github-actions),
  [Dependabot](https://docs.github.com/code-security/dependabot),
  [Dependency review](https://docs.github.com/code-security/supply-chain-security/understanding-your-software-supply-chain/about-dependency-review),
  [Code scanning](https://docs.github.com/code-security/code-scanning),
  [Secret scanning](https://docs.github.com/code-security/secret-scanning),
  [OIDC hardening](https://docs.github.com/actions/deployment/security-hardening-your-deployments/about-security-hardening-with-openid-connect).
- OpenSSF Scorecard checks (Branch-Protection, Token-Permissions, Pinned-Dependencies,
  Dangerous-Workflow, SAST, Dependency-Update-Tool, Security-Policy, etc.):
  [docs/checks.md](https://github.com/ossf/scorecard/blob/main/docs/checks.md).
- SLSA ([slsa.dev](https://slsa.dev/)) for release provenance when publishing starts.
- OWASP [CI/CD Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/CI_CD_Security_Cheat_Sheet.html).

Evaluator-facing bar for a project like Ezkey (public alpha, security-sensitive, multi-language):

1. Protected default branch with required CI + no force-push / no deletion.
2. Documented disclosure path (`SECURITY.md` + private reporting).
3. Least-privilege, SHA-pinned Actions; no dangerous `pull_request_target` patterns.
4. Dependabot (version + alerts) across ecosystems; dependency graph populated.
5. CodeQL (or equivalent SAST) on the languages Ezkey chooses to analyze (here: Java/Kotlin, JS/TS,
   Python, C/C++, Actions — **not** Swift) with a triage policy.
6. Secret scanning + push protection.
7. Honest gap between “lab / alpha” and production release signing (SLSA, attestations) — do not fake maturity.

Ezkey already meets much of (1)–(5) in spirit; Settings confirmation and a few high-value
controls close the evaluator gap without overbuilding.

---

## 3. Gap table

Effort: **S** = settings toggle or small docs/config; **M** = one workflow or ruleset change +
tests; **L** = multi-surface program (release signing, full SLSA).

Value: evaluator / supply-chain / operator impact (High / Med / Low).

| # | Practice | Ezkey today | Gap | Effort | Value | Priority |
|---|----------|-------------|-----|--------|-------|----------|
| G1 | CodeQL Advanced only (no default conflict) | #805 merged; 5 analyses on `main` at `f26cbe55` | None — **done** | — | — | **Done** |
| G2 | Dependabot **alerts** + security updates | Version updates strong; API said disabled vs `SECURITY.md` | Enable alerts if off; align `SECURITY.md` | S | High | **P0** |
| G3 | Secret scanning + push protection | Claimed; not API-verified | Confirm on; enable if off | S | High | **P0** |
| G4 | Dependency graph on; automatic submission off | Graph re-enabled 2026-10-13; #798 draft `5079f27f` | Merge #798 docs; confirm automatic submission still Disabled | S | High | **Done** (Settings); docs = merge #798 |
| G7 | Dependency review Action on PRs | Absent | After G2: add action limited to PRs that touch manifests; fail on high/critical | M | High | **P1** |
| G8 | `SECURITY.md` Advanced CodeQL wording | Updated in #808 | Reconfirm after Marc verifies Settings toggles | S | Med | P1 |
| G5 | Ruleset: require **PR** before merge (no approval) | Status checks only; admin bypass always | Require a pull request **without** required approvals (solo: author cannot approve own PR). Approvals stay soft / #787 | S–M | Med | **P2** |
| G9 | `delete_branch_on_merge` | false | Enable for hygiene | S | Low | P2 |
| G10 | Prefer squash-only (or document merge strategy) | All three merge methods on | Pick one preferred; disable others or document | S | Low | P2 |
| G12 | Scorecard workflow + badge | Absent | Scheduled Scorecard; publish badge in README | M | Med | P2 |
| G14 | Container image CVE scanning | Docker Dependabot version bumps only | Trivy/Grype job or registry scan; not a substitute for alerts | M | Med | P2 |
| G15 | SBOM generation (CycloneDX/SPDX) | Absent | Generate on `main` / release tags; attach artifacts | M | Med | P2 |
| G17 | Android Lint in CI | Unit tests + 16 KB only | Add `lintDebug` (or release lint) to mobile workflow | M | Med | P2 |
| G18 | Coverage gate vs aspirational “90%” | Jacoco in POM; “90%” in `DEVELOPMENT.md`; not CI-enforced | Hygiene: either enforce a realistic floor or remove the aspirational claim from `DEVELOPMENT.md` (not a release-train item) | M | Med | P2 (hygiene) |
| G24 | CodeQL as blocking required check | Policy / #787 process; not ruleset-enforced | Ruleset `code_scanning` or required check if noise is tolerable — or keep policy-only | M | Med | P2 |
| G6 | `CODEOWNERS` + optional required-reviewer paths | Absent | Only useful if paired with G5-style enforcement later; otherwise soft ownership docs | S–M | Low–Med | **P3** (tie to G5) |
| G11 | Strict status checks (branch up to date) | `strict: false` | Skip for now — daily Dependabot PRs would force serial rebases | S | Low | **Skip / P3** |
| G13 | Harden-runner (egress audit) | Absent | Optional later; see diminishing-returns | M | Low–Med | **P3** |
| G19 | Signed commits | Not required | Optional ruleset rule; costly for AI agents / Dependabot | S | Low | P3 |
| G20 | GitHub Releases + signed artifacts / image attestations (SLSA) | Explicitly not implemented; SHA ledger only | Needed when publishing packages — not for alpha SHA deploys | L | High *when releasing* | P3 / later |
| G21 | Deploy environments + OIDC (no long-lived cloud secrets) | Manual Lightsail/Pages; `copilot` env empty | When automation lands: environments + OIDC | L | High *when automating deploy* | P3 / later |
| G16 | Pin Docker base digests | Floating tags (`eclipse-temurin:25-jre-alpine`, etc.) | Digest pins + Dependabot digest updates | M | Med | **Phase 3** |
| G25 | `pull_request_target` / over-privileged tokens | Already avoided | Keep regression watch in Scorecard / review | — | — | Maintain |
| G26 | Query suites / PR gating polish | Default queries; weekly cron | Consider `security-extended` later; watch noise | M | Low–Med | P3 |
| — | `allow_auto_merge` for Dependabot T1 | `false` today | Enable T1-only **or** record explicit “do not enable” | S | Med | P2 decision |

---

## 4. Phased plan

### Phase 1 — Quick wins, high value (Settings + honesty)

Owner suggestions in parentheses. **Marc** required wherever Settings is named.

1. **Confirm / enable Dependabot alerts and security updates** (Marc; Fred align `SECURITY.md`) —
   **new P0** (API “disabled” vs SECURITY announcement).
2. **Confirm / enable secret scanning and push protection** (Marc; Christophe spot-check).
3. **Merge [#798](https://github.com/mgagp/ezkey/pull/798)** head `5079f27f` (Marc) — Dependency
   graph must stay enabled; automatic submission stays Disabled. Settings re-enable already done
   2026-10-13 (**G4**).
4. **Refresh `SECURITY.md` “practices in use”** after the Settings toggles above are verified (Fred) —
   Advanced CodeQL wording already updated in #808.
5. ~~Switch CodeQL to Advanced~~ — **done** (#805; analyses on `main` at `f26cbe55`).
6. ~~Fix stale “ci-gate intended to become required”~~ — **done** in #808 (`DEVELOPMENT.md`).

Exit criteria: Marc signs the Settings checklist for G2/G3 (+ automatic submission); #798 merged;
docs match verified toggles.

### Phase 2 — Structural parity (supply-chain PR gate + light ruleset)

1. **Dependency review Action** after G2 (Fred; Christophe severity): run on PRs that **touch
   manifests**; fail on **high/critical** (G7 before G6).
2. **Ruleset: require a pull request** before merge to `main`, **without** required approvals
   (Marc) — solo maintainer cannot approve PRs opened as `mgagp` (G5 → P2).
3. **OpenSSF Scorecard** scheduled workflow + README badge (Fred); triage with Christophe (no 10/10
   campaign).
4. **Optional hygiene:** `delete_branch_on_merge`; document preferred merge method; decide
   `allow_auto_merge` T1-only vs never (Marc + Fred).
5. **Mobile:** Android Lint job or step (Justin + Fred) (G17).
6. **Container scanning** job (Edgar + Fred) (G14) — separate from Dependabot version PRs.
7. **Coverage honesty (G18 hygiene):** remove or enforce the “90%” claim in `DEVELOPMENT.md`
   (Fred + Patrick) — not a Phase 3 release item.
8. **CODEOWNERS (G6)** only if/when G5-style enforcement is revisited (P3).
9. **CodeQL required check (G24)** only if alert noise is tolerable (Marc + Christophe).

Exit criteria: dependency review on manifest PRs; PR-required ruleset (no fake self-approval);
Scorecard signal; Settings still documented.

### Phase 3 — Release-shaped supply chain (when publishing justifies it)

Defer until GitHub Releases / Maven Central / Play / signed images are real product goals
([`VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md)):

1. SBOM attach on release tags (Fred) (G15).
2. Image/provenance attestations (SLSA-oriented) and **digest-pinned** runtime images (Edgar + Fred)
   (G16 + G20).
3. GitHub Environments with protection rules + OIDC to cloud (Edgar) (G21).
4. Signed release artifacts (Christophe security opinion + Marc).

### Diminishing returns — deliberately not doing (now)

| Item | Reason |
|------|--------|
| Mandatory signed commits for all contributors (#808 decision) | Breaks Dependabot, many agents, and low-friction lab flow; Scorecard Branch-Protection tier is optional for solo public alpha |
| Full SLSA L3 + always-on provenance before first published artifact (#808) | No release train yet; SHA ledger is the honest deploy identity |
| SonarQube / enterprise SAST suite parallel to CodeQL (#808) | Accidental complexity; CodeQL + java-doctor/semgrep hygiene lanes already cover intent |
| Stale-bot auto-close / issue-form bureaucracy (#808) | High false-close risk; labels + human triage; security path already in `SECURITY.md` |
| Making Playwright / Docker `ezkey-tests` part of every PR `ci-gate` (#808) | Cost/time; covered by `quality-gate` / clean-start when needed |
| Zero Scorecard findings / 10/10 campaign (#808) | Hygiene theater; triage actionable checks only |
| Harden-runner on every job (G13 / #808) | P3 at best; Phase 1 Settings + dependency review give more evaluator signal per hour — keep out of Phase 1–2 |
| Strict status checks with daily Dependabot (G11 / #808) | Forces serial rebases; keep `strict: false` |
| Required approving reviews for solo `mgagp` PRs (G5 approvals / #808) | Author cannot approve own PR; require PR without approval instead |
| Org-wide rulesets / GitHub Enterprise-only controls (#808) | Personal/`mgagp` public lab; out of scope |

---

## 5. Settings Marc must confirm (checklist)

Copy into a maintainer note; check each in **Settings → Code security / Rules / General**:

- [x] **CodeQL Advanced** — done via #805 uploads on `main` (`f26cbe55`); reconfirm UI still Advanced if desired.
- [x] **Dependency graph:** Enabled (Marc 2026-10-13; document via #798).
- [ ] **Automatic dependency submission:** Disabled (dedicated Maven workflow feeds the graph).
- [ ] **Dependabot alerts:** Enabled (reconcile with 2026-10-14 API “disabled” signal) — **P0**.
- [ ] **Dependabot security updates:** Enabled — **P0**.
- [ ] **Secret scanning:** Enabled — **P0**.
- [ ] **Push protection** (secret scanning): Enabled — **P0**.
- [ ] **Private vulnerability reporting:** Enabled (API already `true` — reconfirm UI).
- [ ] **Ruleset “Basic protection”:** still requires **`ci-gate` only** for status checks (do not add path-filtered job names).
- [ ] Whether **classic branch protection** on `main` still exists alongside the ruleset.
- [ ] Desired merge button set (squash / merge commit / rebase) and **delete branch on merge**.
- [ ] Whether Admin **bypass** on the ruleset remains intentional for emergency / bot merges.
- [ ] **`allow_auto_merge`:** keep off, or enable for Dependabot T1 only (record decision).

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
| [`product-docs/global/hygiene/dependabot/`](../../product-docs/global/hygiene/dependabot/) | Dependabot curation (#678 model) |
| PR [#697](https://github.com/mgagp/ezkey/pull/697) | Original `ci-gate` introduction |
| PR [#787](https://github.com/mgagp/ezkey/pull/787) | Merge-ready bar (CodeQL / threads) |
| PR [#798](https://github.com/mgagp/ezkey/pull/798) (`5079f27f`) | Dependency graph must stay enabled |
| PR [#805](https://github.com/mgagp/ezkey/pull/805) (`f26cbe55`) | CodeQL Advanced + policy |
| PR [#808](https://github.com/mgagp/ezkey/pull/808) | This inventory |

**Refresh rule:** when Settings or workflows change, update §1 and the gap row in the **same PR**
(repo-as-memory). Also refresh §1 at the **Monday Dependabot full pass**. Do not fork a second
“CI audit” without linking here.

---

*Inventory date: 2026-10-14 (review-applied). Docs-first; CodeQL checkout `persist-credentials`
aligned in the same PR when touched.*
