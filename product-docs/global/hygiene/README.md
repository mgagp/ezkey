# Hygiene campaign notes

Peripheral HITL decision tracks for punctual **doctor-curated** passes (Admin UI React, Java,
mobile static analysis, Linux PAM), weekly **Dependabot** triage, the **major-upgrades** platform register
(line / semver-major decisions Dependabot does not open), **security-pentest** campaigns,
**javamelody-curated** live-stack performance extracts, on-demand **quality-gate** full-suite
runs (cloud agent), mandate-driven **assessment-curated** white-box investigation follow-ups,
**document-hygiene-curated** passes for legacy document canonicality / archive-vs-delete
decisions, **code-scanning** (CodeQL SAST) triage passes, **corpus-ablation** passes that
prune ephemeral plans/prompts into existing canon, and **host-os** hygiene for the community
Lightsail AL2023 VM (H1/H2/H3; #909).

These folders are **not** product vision, ADR, or backlog execution. They record per-campaign triage
(fix / suppress / skip / defer, or Dependabot merge / hold / defer) so cold sessions can see *why* a
finding or dependency PR was acted on or left alone — without inventing `I-*` / `TB-*` / GitHub
issues per routine item.

Doctor / Dependabot / pentest lanes start from **tool shortlists**. **assessment-curated** starts
from an operator **mandate** + white-box assessment register, then the same one-finding-at-a-time
HITL shape. See root [`AGENTS.md`](../../../AGENTS.md) for keywords.

## Lanes

| Path | Keyword | Role |
|------|---------|------|
| [`assessment-curated/`](assessment-curated/) | `assessment-curated` | **Method canon** — mandate → assessment → HITL → handoff |
| [`mobile-protocol-security/`](mobile-protocol-security/) | `assessment-curated` (instance) | Mobile protocol / crypto assessment campaigns |
| [`java-tink-deprecations/`](java-tink-deprecations/) | `assessment-curated` (instance) | Java Tink deprecated API migration campaigns |
| [`java-jpa-deprecations/`](java-jpa-deprecations/) | `assessment-curated` (instance) | Java JPA / Spring Data deprecated API inventory |
| [`java-json-deprecations/`](java-json-deprecations/) | `assessment-curated` (instance) | Java JSON / Jackson 3 deprecated tree APIs |
| [`java-bucket4j-deprecations/`](java-bucket4j-deprecations/) | `assessment-curated` (instance) | Java Bucket4j deprecated bandwidth / refill APIs |
| [`java-native-compilation/`](java-native-compilation/) | `assessment-curated` (instance) | Java GraalVM / native-image residual cleanup |
| [`java-controller-dead-paths/`](java-controller-dead-paths/) | `assessment-curated` (instance) | Java controller dead / phantom path hygiene |
| [`java-controller-role-validation/`](java-controller-role-validation/) | `assessment-curated` (instance) | Java controller role / tenant-boundary coherence |
| [`java-tenant-sql-isolation/`](java-tenant-sql-isolation/) | `assessment-curated` (instance) | Java service SQL / tenant isolation (TA, Auth, Integration API, API key) |
| [`java-transactional-boundaries/`](java-transactional-boundaries/) | `assessment-curated` (instance) | Java `@Transactional` placement, self-invocation, `*TxHelper` |
| [`hardcoded-values/`](hardcoded-values/) | `assessment-curated` (instance) | Hardcoded literals and magic numbers: default is keep in code; externalize only when someone has a legitimate reason to want another value |
| [`react-doctor/`](react-doctor/) | `doctor-curated` | Admin UI React Doctor curated passes |
| [`java-doctor/`](java-doctor/) | `java-doctor-curated` | Java SpotBugs / Semgrep / PMD curated passes |
| [`pam-doctor/`](pam-doctor/) | `pam-doctor-curated` | Linux PAM module (`ezkey-pam/src`) clang-format / clang-tidy / cppcheck passes, plus an on-demand ASan / UBSan / Valgrind replay (`./scripts/pam-doctor-sanitizers.sh`). macOS install track excluded |
| [`mobile-doctor/`](mobile-doctor/) | `mobile-doctor-curated` | Ezkey Mobile static-analysis curated passes |
| [`dependabot/`](dependabot/) | `dependabot-curated` | Near-continuous Dependabot triage (weekday light + Monday full), Orval O0–O3 grid, Java BOM / Mobile RN / Admin UI advisory pulses |
| [`major-upgrades/`](major-upgrades/) | (quarterly in `dependabot-curated` + monthly GHA) | Platform / runtime / base-image major-upgrade register (`registry.yaml`); light path vs dedicated issue; monthly [`endoflife-check.yml`](../../../.github/workflows/endoflife-check.yml) |
| [`report-intake/`](report-intake/) | (standing procedure) | First response within 14 days on every report channel: issue reminder workflow, GitHub private reports, `security@ezkey.org` mail contract |
| [`stabilization-lessons/`](stabilization-lessons/) | (read before HA, startup, exposure or test-helper work) | Lessons from the #1180 / #1259 stabilization, with evidence and checks |
| [`security-pentest/`](security-pentest/) | `security-pentest-curated` | Live API / DAST-style security campaigns |
| [`code-scanning/`](code-scanning/) | (CodeQL SAST triage) | GitHub CodeQL / code-scanning alert triage and advanced-setup config decisions; policy in `docs/SECURITY_POSTURE.md` |
| [`javamelody/`](javamelody/) | `javamelody-curated` | Live JavaMelody extract after a known workload (typically operational churn) |
| [`quality-gate/`](quality-gate/) | `quality-gate` | On-demand full-suite + stack-health gate (cloud agent; between merge batches) |
| [`document-hygiene/`](document-hygiene/) | `document-hygiene-curated` | Legacy document canonicality, discoverability, and archive-vs-delete decisions |
| [`corpus-ablation/`](corpus-ablation/) | `corpus-ablation` | Cursor plans / prompt scaffolds → canon + discoverability; skill `.claude/skills/corpus-ablation` (**not scheduled** — pending challenger repositioning, coordinator 2026-10-19) |
| [`agent-surface/`](agent-surface/) | (on demand) | Review of what a cold agent loads (root `CLAUDE.md` / `AGENTS.md`, Cursor rules, skills): mission → rules → done → evidence, soft 200-line root guardrail |
| [`critical-review/`](critical-review/) | `critical-review` | Launched adversarial / critical review (fresh cloud agent, forced ≠ author model family, read-only HITL); tools Stage 2 |
| [`host-os/`](host-os/) | `host-os` (H1/H2/H3) | Community Lightsail host OS hygiene (`ezkey-online`, AL2023); signal `scripts/lightsail/os-patch-check.sh`; **rootbeer-local only** (#909) |

## Cadence & automation

Standing cadence for **full-quality, automated, continuous hygiene** across the project
(approved by Marc **2026-10-19**). Times are **America/New_York (ET)** unless noted.
One owning pass per procedure per slot — back off on concurrent merges.

Repo-is-memory decisions (one line + reason):

- **Gitleaks baseline (working tree + full history)** — root `.gitleaksignore` holds fingerprints for classified false positives (docs curl examples, test fixtures, placeholders) plus historical `bruno/environments/local.bru` findings (file now gitignored; history not rewritten; rotation is a security decision). Suspected real historical secrets are not ignored. Rerun with latest `gitleaks` binary: `gitleaks dir --no-banner .` and `gitleaks git --no-banner .` (reason: catch committed secrets without rewriting history).
- **Issue-label hygiene (templates + opened-issue workflow)** — ~96 issues arrived unlabeled because Cursor vuln-scanner and GitHub MCP/Cursor-app creates bypassed `gh issue create --label`; rule + AGENTS now require labels for every create path with post-create verify, issue forms ship default labels, and `issues: [opened]` adds `status:needs-challenge` (plus `security:vuln-scanner` for `cursor[bot]`) when type/lane/component are missing.
- **google-java-format 2.0.0 adopted (keep cleanup default on)** — nested Spotless pin `1.36.1`→`2.0.0` (2026-10-30 layout engine; cleanup on by default). Spotless 3.10.4 compatible without bump; Checkstyle needed no alignment; `.git-blame-ignore-revs` lists the #1136 squash SHA `11f87001…` (not the pre-squash tip). Refs [#1136](https://github.com/mgagp/ezkey/pull/1136).
- **Dependabot inflow reduction** — minor+patch catch-all groups per directory, PR limit 5, ignore TS 7 / mobile ESLint 10 majors, CI regen of mobile licenses JSON, auto-merge of green semver-patch PRs (excl. Docker / Gradle ecosystems, RN-core / vision-camera / android-gradle / docker groups, deferred:* / hitl* labels, or crypto deps matching `tink|bouncycastle|bcprov|bcpkix|bcutil|conscrypt|crypto|sha256|sha2|sodium|@noble/|jose|jsonwebtoken|jwt|keychain|biometric`): open-PR stock fell but weekly inflow (~34) did not, so cut it at the source (Marc 2026-10-30).
- **Dependabot auto-merge gate posture** — Spring Boot / Spring Security and GitHub Actions patch bumps stay eligible for auto-merge; rely on the gate, and if a problem occurs understand it and add unit/functional tests before excluding (Marc 2026-10-30).
- **Dependabot curated at 09:14 ET weekdays (light) / Monday full** — fixed clock so agents and
  humans share one slot; avoids ad-hoc “whenever” drift.
- **Code-scanning triage Monday 10:15 ET** (moved from 09:17) — avoid collision with the
  Dependabot Monday full pass; weekday light still catches new high/critical.
- **Code-scanning remediation ≤3 real alerts/week** (same Monday 10:15 ET slot; severity then
  nearest SLA deadline; one fix PR each via cloud agent) — triage without a bounded fix budget
  leaves the open stock growing; security/crypto fixes need a security review, behavior changes
  need Marc's go, no deploy (Marc 2026-10-19). A 2026-11-13 proposal to also cover Scorecard
  and Dependabot as one grouped issue per rule was **not adopted**. Monday 10:15 stays CodeQL.
- **Doctors biweekly Java → mobile → React** — one surface per window keeps HITL cheap; campaign
  notes only when new P1/P2 appear.
- **Quality-gate Wednesday 19:00 ET** — weekly full-suite signal without weekday merge noise;
  report only when verdict is not GO.
- **endoflife.date monthly workflow** (first Monday ~14:30 UTC) — non-blocking standing issue;
  quarterly human register walk stays on Monday full (Jan/Apr/Jul/Oct). October 2026 walk is due.
- **Pentest monthly with a security review** — live DAST needs that review in the loop.
- **JavaMelody / perf pass** — monthly after a known workload (often post quality-gate churn).
- **document-hygiene quarterly** — low churn; archaeology does not need a weekly slot.
- **corpus-ablation not scheduled** — pending repositioning as a **challenger** (mandate to
  the coordinator, 2026-10-19); do not invent a calendar until that lands.
- **Mobile `yarn deps:monitor --strict` on Monday full** — empty Dependabot mobile queue is not
  proof the stack is current; strict monitor feeds the Mobile RN pulse.
- **Admin UI advisory pulse on Monday full** — `npm audit --json` in `ezkey-admin-ui/`, reduced to
  distinct GHSA. An empty Dependabot PR queue is not proof the lockfile is free of advisories.
  `package-lock.json` and `yarn.lock` for the same GHSA count once. A row already waiting on
  upstream, with its fallback date still ahead, stays waiting (Marc 2026-11-13).
- **Host OS weekly read-only check (hygiene pass)** — `os-patch-check.sh --target community` reports
  only when a newer dated AL2023 release has Important or Critical advisories (or the probe
  is `unknown`). Plain `dnf update` on the locked releasever is a no-op and hides those
  advisories (blind spot found 2026-11-22; Marc chose option 2). No dnf-automatic (#909).
- **Host OS H1** — `dnf upgrade --releasever=<dated release>` when that check reports;
  deploy, snapshot, reboot or docker restart, **Marc go**. Not an autonomous monthly patch (#909).
- **Host OS H2 quarterly with major-upgrades walk** — same dated `dnf upgrade --releasever=…`
  (never `latest`) + reboot; deploy; Marc go; snapshot + pg_dump on rootbeer (#909).
- **Host OS H3 ad hoc (plan by 2028)** — major / new instance blue-green before AL2023 EOL
  2029-06-30; Marc go, deploy executes, security review on posture (#909).
- **dnf-automatic not adopted on community host** — conflicts with deterministic `releasever`
  upgrades; no unattended swap (#909).
- **Hardcoded values stay in code unless someone legitimate needs another value** — externalization
  is the exception (credible requester, use case, issue). A weekly slot would pressure changes that
  should stay in code (Marc 2026-11-17, #1393).

| Procedure | Owner lane | Trigger | Cadence | Signal | Prioritization rule | Human gate |
|-----------|------------|---------|---------|--------|---------------------|------------|
| **Dependabot curated** (skill [`.claude/skills/dependabot-curated`](../../../.claude/skills/dependabot-curated/SKILL.md); docs [`dependabot/`](dependabot/)) | `dependabot-curated` | Cloud agent / operator keyword | **Weekdays 09:14 ET** light; **Monday** full | Open Dependabot PRs + Java BOM + Mobile RN + Admin UI advisory pulses | **T1–T4**; Orval **O0–O3** (Admin UI ≠ mobile) | Weekday light: autonomous T1–T3 + O0/O1; Monday full HITL default; Marc on T4 / O3 / crypto majors |
| **Mobile `deps:monitor --strict`** (`ezkey_mobile` `yarn deps:monitor --strict`) | `dependabot-curated` (Monday full) | Part of Monday full Mobile RN pulse | **Monday** with full pass | Actionable vs ecosystem-gated deferred | Feeds mobile lots (T1–T4 / Orval grid) | Same as Dependabot curated |
| **Admin UI advisory pulse** (`ezkey-admin-ui` `npm audit --json`, reduced to distinct GHSA) | `dependabot-curated` (Monday full) | Part of Monday full | **Monday** with full pass | Distinct GHSA vs waiting-upstream table; duplicate lockfile manifests count once | A lot only when the GHSA is new or the fallback date is due | Same as Dependabot curated |
| **Dependabot opens PRs** ([`.github/dependabot.yml`](../../../.github/dependabot.yml)) | Dependabot app | Schedule | **Daily** Maven + npm; **weekly** Actions + pip (cooldown ~3d patch/minor, ~7d major) | New upgrade PRs | N/A (feeds curated) | None — curation owns merge |
| **Maven dependency submission** ([`.github/workflows/maven-dependency-submission.yml`](../../../.github/workflows/maven-dependency-submission.yml)) | CI / Insights | Push to `main` (pom paths) + `workflow_dispatch` | On change | GitHub dependency graph | N/A | Maintainer: Automatic Dependency Submission stays **Disabled** |
| **Code scanning triage + remediation** ([`code-scanning/`](code-scanning/); policy [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md)) | `code-scanning` | Hygiene pass proposes; security review executes; a cloud session opens fix PRs | **Monday 10:15 ET** full; **weekday light** for new high/critical | Open CodeQL alerts | Exposure-aware **SLAs** (critical/high deployed → 24h triage / 14d fix; medium 60d; low 180d; KEV 3d; dev tooling 30/60/180d). Remediates **≤3 real alerts/week** (severity first, then nearest deadline); **one fix PR per alert** via cloud agent | Security review decides/executes dismissals and reviews security/crypto fix PRs; **Marc** for high-impact dismissals and any behavior-change fix; **no deploy** |
| **CodeQL workflow** ([`.github/workflows/codeql.yml`](../../../.github/workflows/codeql.yml)) | CI / security | PR + push + schedule | Every PR/`main`; weekly Sun **05:17 UTC** | New/fixed alerts | Feeds triage | None (analysis only) |
| **CI / `ci-gate`** ([`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)) | CI | PR + push | Every PR | Job failures | Block merge until green | Author fixes; merge only on owner go |
| **Java doctor** (`./scripts/java-doctor-curated.sh`; [`java-doctor/`](java-doctor/)) | `java-doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (Java week) | SpotBugs + Semgrep + PMD shortlist | **P1 / P2 / P3**; write campaign note **only on new P1/P2** | HITL one-finding; suppress-with-reason OK |
| **PAM doctor** (`./scripts/pam-doctor-curated.sh`; sanitizers `./scripts/pam-doctor-sanitizers.sh`; [`pam-doctor/`](pam-doctor/)) | `pam-doctor-curated` | Operator keyword | **On demand** (experimental module; not in the biweekly rotation) | clang-format Google drift + clang-tidy + narrow cppcheck; ASan / UBSan / Valgrind on the existing harnesses | **P1 / P2 / P3**; note on a pass that has a lot | HITL one-finding; suppress-with-reason OK |
| **Mobile doctor** (`ezkey_mobile` `yarn doctor:curated`; [`mobile-doctor/`](mobile-doctor/)) | `mobile-doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (mobile week) | react-doctor + Semgrep + Detekt | **P1 / P2 / P3**; note **only on new P1/P2** | HITL one-finding |
| **React / Admin UI doctor** (`ezkey-admin-ui` `yarn doctor:curated`; [`react-doctor/`](react-doctor/)) | `doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (React week) | React Doctor shortlist | **P1 / P2 / P3**; note **only on new P1/P2** | HITL one-finding |
| **Quality-gate** (`./scripts/quality-gate.sh`; [`quality-gate/`](quality-gate/)) | `quality-gate` | Cloud agent | **Wednesday 19:00 ET** weekly | Exit 0 GO · 1 NO-GO · 3 GO w/ reservations · 4 INCONCLUSIVE | Report **only if verdict ≠ GO** | Marc / operator on NO-GO or INCONCLUSIVE |
| **endoflife.date check** ([`.github/workflows/endoflife-check.yml`](../../../.github/workflows/endoflife-check.yml); `scripts/endoflife-check.py`) | `major-upgrades` | GHA schedule + `workflow_dispatch` | **Monthly** first Monday **~14:30 UTC** | Register cycles within **90 days** of EOL (or already EOL); source/`eol` drift logged | Standing issue labeled `hygiene`; `follow:` / `accept-risk` suppress proximity unless `eol` date drifts | Non-blocking; hygiene pass prepares the quarterly walk; Marc decides majors |
| **Major-upgrades quarterly review** ([`major-upgrades/`](major-upgrades/) `registry.yaml`) | `major-upgrades` | Monday full `dependabot-curated` | **Quarterly** Jan / Apr / Jul / Oct (**October 2026 due now**) | Registry `eol` / `last_review` / `accept_until` | `keep` · `plan` · `follow:` · `accept-risk`; light vs dedicated-issue paths | Hygiene pass prepares; security review on `security: true`; engineering review on build/mobile; **Marc** decides |
| **Security pentest** (skill + `./scripts/security-pentest-curated.sh`; [`security-pentest/`](security-pentest/)) | `security-pentest-curated` | Operator + security review | **Monthly** | Schemathesis + ZAP + Nuclei curated shortlist | **P1 / P2 / P3** (charter expected-context) | Security review + HITL one-finding |
| **JavaMelody / perf** (`./scripts/javamelody-curated.sh`; [`javamelody/`](javamelody/)) | `javamelody-curated` | After known workload (often post quality-gate churn) | **Monthly** | Ranked HTTP/SQL/Spring by total time | **P1 / P2 / P3**; high hit-count on churn paths ≠ signal | Performance pass + HITL one-finding |
| **Assessment curated** (skill [`.claude/skills/assessment-curated`](../../../.claude/skills/assessment-curated/SKILL.md); [`assessment-curated/`](assessment-curated/)) | `assessment-curated` | Operator **mandate** | **On mandate** (not calendar) | Assessment register | Severity / confidence in register; small lot | HITL one-finding; GO = fix + handoff |
| **Hardcoded values** ([`hardcoded-values/`](hardcoded-values/)) | `assessment-curated` (instance) | Operator mandate, plus a quarterly delta (`git diff` from the last pass SHA) | **On mandate**, at most **quarterly** (with the document-hygiene quarter). Quiet when nothing is new: no note, no message | Read-only `rg` in the pass note (no doctor script) | engineering-review question; zones (e) → (a) → (b) → (c) → (d); externalize ≤ 10–15% of a shortlist | HITL one lot at a time. Security review on security properties. Marc on any behavior change. **Not a CI gate** |
| **Document hygiene** (skill [`.claude/skills/document-hygiene-curated`](../../../.claude/skills/document-hygiene-curated/SKILL.md); [`document-hygiene/`](document-hygiene/)) | `document-hygiene-curated` | Operator | **Quarterly** | Legacy / duplicate docs | Keep / fold / archive / delete / retarget | HITL one document at a time |
| **Corpus ablation** (skill [`.claude/skills/corpus-ablation`](../../../.claude/skills/corpus-ablation/SKILL.md); [`corpus-ablation/`](corpus-ablation/)) | `corpus-ablation` | — | **Not scheduled** — pending **challenger** repositioning (coordinator mandate 2026-10-19) | Ephemeral plans / prompts | Discoverability-preserving Go packages | Coordinator when challenger lands |
| **Critical review** (skill [`.claude/skills/critical-review`](../../../.claude/skills/critical-review/SKILL.md); [`critical-review/`](critical-review/)) | `critical-review` | G1 brief / P0–P1 vision / high-risk plan / PR craft | **Event-driven** (gate), not weekly | ≤10 findings; forced ≠ author model family | Severity in review report | Launcher (coordinator / engineering review / security review) + author HITL |
| **Monthly digest** (skill [`.claude/skills/monthly-digest`](../../../.claude/skills/monthly-digest/SKILL.md)) | Publishing | Month closed | **Monthly** (first week after month end) | Distilled activity digest | N/A (comms, not code shortlist) | Editor / Marc before publish |
| **Ops: `check-ezkey-online-hosts`** / **`check-local-mgmt-port-publish`** (`scripts/ops/`; CI jobs) | Ops / CI | CI path filter | Every relevant CI run | Forbidden hosts / mgmt port publish | Fail = fix before merge | Author |
| **Ops: `docker-log-hygiene`** (`scripts/ops/docker-log-hygiene.sh`) | Ops / Lightsail | Pre-upgrade + after compose up | On community upgrade checklist | Missing `max-size` / `max-file` | Fix before `--remote-up` | Operator on VM |
| **Ops: `verify-javamelody-non-exposure`** / **`check-javamelody-spring-json`** / **`validate-caddy-observability-regexp`** | Ops / security | After JM or Caddy change; JM deploy smoke | On change + with monthly JM/pentest when JM in scope | Exposure / JSON drift / regexp | Fail = fix | Operator / security review |
| **Host OS detect** (weekly read-only; [`host-os/`](host-os/); `./scripts/lightsail/os-patch-check.sh --target community`) | `host-os` | Hygiene pass, rootbeer-local | **Weekly** | `RELEASE_UPDATE` line (latest dated release, not the locked repo) | Report only on newer release with Important/Critical, or on `unknown`; quiet when `no` or `yes` with both counts 0 | None (read-only) |
| **Host OS H1** (dated release with Important/Critical; `dnf upgrade --releasever=<dated>`; [`host-os/`](host-os/)) | `host-os` | Deploy, rootbeer-local | When the weekly check reports | Same signal | Snapshot before apply; rotate older snapshots; reboot or docker restart | **Marc go** |
| **Host OS H2** (quarterly dated release + reboot; [`host-os/`](host-os/)) | `host-os` | Deploy, rootbeer-local | **Quarterly** Jan / Apr / Jul / Oct | `dnf check-release-update` → pin `--releasever=<dated>` (never `latest`) | Snapshot + pg_dump (rootbeer) before apply; reboot or docker restart | **Marc go** |
| **Host OS H3** (major / new instance blue-green; [`host-os/`](host-os/)) | `host-os` | Ad hoc | **Plan by 2028** (AL2023 EOL 2029-06-30) | EOL proximity + register `amazon-linux-community` | `create-instance` + `bootstrap-host` + pg_dump restore + static IP move | **Marc go**; deploy executes; security review on posture |

Doctor biweekly rotation order (repeat): **Java → mobile → React → …**. Empty high-signal lot → no campaign note.
**PAM doctor** is on demand for the experimental Linux module; it is not inserted into that rotation.

## Hygiene vs program

Routine polish stays on the lightweight hygiene path (commit/PR + these notes). Aligns with
[`methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).
