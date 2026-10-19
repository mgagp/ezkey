# Hygiene campaign notes

Peripheral HITL decision tracks for punctual **doctor-curated** passes (Admin UI React, Java,
mobile static analysis), weekly **Dependabot** triage, the **major-upgrades** platform register
(line / semver-major decisions Dependabot does not open), **security-pentest** campaigns,
**javamelody-curated** live-stack performance extracts, on-demand **quality-gate** full-suite
runs (cloud agent), mandate-driven **assessment-curated** white-box investigation follow-ups,
**document-hygiene-curated** passes for legacy document canonicality / archive-vs-delete
decisions, **code-scanning** (CodeQL SAST) triage passes, and **corpus-ablation** passes that
prune ephemeral plans/prompts into existing canon.

These folders are **not** product vision, ADR, or backlog execution. They record per-campaign triage
(fix / suppress / skip / defer, or Dependabot merge / hold / defer) so cold sessions can see *why* a
finding or dependency PR was acted on or left alone — without inventing `I-*` / `TB-*` / GitHub
issues per routine item.

Doctor / Dependabot / pentest lanes start from **tool shortlists**. **assessment-curated** starts
from an operator **mandate** + white-box assessment register, then the same one-finding-at-a-time
HITL shape. See root [`AGENTS.md`](../../AGENTS.md) for keywords.

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
| [`java-transactional-boundaries/`](java-transactional-boundaries/) | `assessment-curated` (instance) | Java `@Transactional` placement, self-invocation, `*TxHelper` |
| [`react-doctor/`](react-doctor/) | `doctor-curated` | Admin UI React Doctor curated passes |
| [`java-doctor/`](java-doctor/) | `java-doctor-curated` | Java SpotBugs / Semgrep / PMD curated passes |
| [`mobile-doctor/`](mobile-doctor/) | `mobile-doctor-curated` | Ezkey Mobile static-analysis curated passes |
| [`dependabot/`](dependabot/) | `dependabot-curated` | Near-continuous Dependabot triage (weekday light + Monday full), Orval O0–O3 grid, Java BOM / Mobile RN pulses |
| [`major-upgrades/`](major-upgrades/) | (quarterly in `dependabot-curated` + monthly GHA) | Platform / runtime / base-image major-upgrade register (`registry.yaml`); light path vs dedicated issue; monthly [`endoflife-check.yml`](../../../.github/workflows/endoflife-check.yml) |
| [`security-pentest/`](security-pentest/) | `security-pentest-curated` | Live API / DAST-style security campaigns |
| [`code-scanning/`](code-scanning/) | (CodeQL SAST triage) | GitHub CodeQL / code-scanning alert triage and advanced-setup config decisions; policy in `docs/SECURITY_POSTURE.md` |
| [`javamelody/`](javamelody/) | `javamelody-curated` | Live JavaMelody extract after a known workload (typically operational churn) |
| [`quality-gate/`](quality-gate/) | `quality-gate` | On-demand full-suite + stack-health gate (cloud agent; between merge batches) |
| [`document-hygiene/`](document-hygiene/) | `document-hygiene-curated` | Legacy document canonicality, discoverability, and archive-vs-delete decisions |
| [`corpus-ablation/`](corpus-ablation/) | `corpus-ablation` | Cursor plans / prompt scaffolds → canon + discoverability; skill `.cursor/skills/corpus-ablation` (**not scheduled** — pending challenger repositioning, Mathieu 2026-10-19) |
| [`critical-review/`](critical-review/) | `critical-review` | Launched adversarial / critical review (fresh cloud agent, forced ≠ author model family, read-only HITL); tools Stage 2 |

## Cadence & automation

Standing cadence for **full-quality, automated, continuous hygiene** across the project
(approved by Marc **2026-10-19**). Times are **America/New_York (ET)** unless noted.
One owning pass per procedure per slot — back off on concurrent merges.

Repo-is-memory decisions (one line + reason):

- **Dependabot curated at 09:14 ET weekdays (light) / Monday full** — fixed clock so agents and
  humans share one slot; avoids ad-hoc “whenever” drift.
- **Code-scanning triage Monday 10:15 ET** (moved from 09:17) — avoid collision with the
  Dependabot Monday full pass; weekday light still catches new high/critical.
- **Code-scanning remediation ≤3 real alerts/week** (same Monday 10:15 ET slot; severity then
  nearest SLA deadline; one fix PR each via cloud agent) — triage without a bounded fix budget
  leaves the open stock growing; security/crypto fixes need Christophe's review, behavior changes
  need Marc's go, no deploy (Marc 2026-10-19).
- **Doctors biweekly Java → mobile → React** — one surface per window keeps HITL cheap; campaign
  notes only when new P1/P2 appear.
- **Quality-gate Wednesday 19:00 ET** — weekly full-suite signal without weekday merge noise;
  report only when verdict is not GO.
- **endoflife.date monthly workflow** (first Monday ~14:30 UTC) — non-blocking standing issue;
  quarterly human register walk stays on Monday full (Jan/Apr/Jul/Oct). October 2026 walk is due.
- **Pentest monthly with Christophe** — live DAST needs the security owner in the loop.
- **JavaMelody / perf with Carl** — monthly after a known workload (often post quality-gate churn).
- **document-hygiene quarterly** — low churn; archaeology does not need a weekly slot.
- **corpus-ablation not scheduled** — pending repositioning as a **challenger** (mandate to
  Mathieu, 2026-10-19); do not invent a calendar until that lands.
- **Mobile `yarn deps:monitor --strict` on Monday full** — empty Dependabot mobile queue is not
  proof the stack is current; strict monitor feeds the Mobile RN pulse.

| Procedure | Owner lane | Trigger | Cadence | Signal | Prioritization rule | Human gate |
|-----------|------------|---------|---------|--------|---------------------|------------|
| **Dependabot curated** (skill [`.cursor/skills/dependabot-curated`](../../../.cursor/skills/dependabot-curated/SKILL.md); docs [`dependabot/`](dependabot/)) | `dependabot-curated` | Cloud agent / operator keyword | **Weekdays 09:14 ET** light; **Monday** full | Open Dependabot PRs + Java BOM + Mobile RN pulses | **T1–T4**; Orval **O0–O3** (Admin UI ≠ mobile) | Weekday light: autonomous T1–T3 + O0/O1; Monday full HITL default; Marc on T4 / O3 / crypto majors |
| **Mobile `deps:monitor --strict`** (`ezkey_mobile` `yarn deps:monitor --strict`) | `dependabot-curated` (Monday full) | Part of Monday full Mobile RN pulse | **Monday** with full pass | Actionable vs ecosystem-gated deferred | Feeds mobile lots (T1–T4 / Orval grid) | Same as Dependabot curated |
| **Dependabot opens PRs** ([`.github/dependabot.yml`](../../../.github/dependabot.yml)) | Dependabot app | Schedule | **Daily** Maven + npm; **weekly** Actions + pip (cooldown ~3d patch/minor, ~7d major) | New upgrade PRs | N/A (feeds curated) | None — curation owns merge |
| **Maven dependency submission** ([`.github/workflows/maven-dependency-submission.yml`](../../../.github/workflows/maven-dependency-submission.yml)) | CI / Insights | Push to `main` (pom paths) + `workflow_dispatch` | On change | GitHub dependency graph | N/A | Maintainer: Automatic Dependency Submission stays **Disabled** |
| **Code scanning triage + remediation** ([`code-scanning/`](code-scanning/); policy [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md)) | `code-scanning` | Fred proposes; Christophe executes; cloud agent opens fix PRs | **Monday 10:15 ET** full; **weekday light** for new high/critical | Open CodeQL alerts | Exposure-aware **SLAs** (critical/high deployed → 24h triage / 14d fix; medium 60d; low 180d; KEV 3d). Remediates **≤3 real alerts/week** (severity first, then nearest deadline); **one fix PR per alert** via cloud agent | Christophe decides/executes dismissals; **Christophe** reviews security/crypto fix PRs; **Marc** for high-impact dismissals and any behavior-change fix; **no deploy** |
| **CodeQL workflow** ([`.github/workflows/codeql.yml`](../../../.github/workflows/codeql.yml)) | CI / security | PR + push + schedule | Every PR/`main`; weekly Sun **05:17 UTC** | New/fixed alerts | Feeds triage | None (analysis only) |
| **CI / `ci-gate`** ([`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)) | CI | PR + push | Every PR | Job failures | Block merge until green | Author fixes; merge only on owner go |
| **Java doctor** (`./scripts/java-doctor-curated.sh`; [`java-doctor/`](java-doctor/)) | `java-doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (Java week) | SpotBugs + Semgrep + PMD shortlist | **P1 / P2 / P3**; write campaign note **only on new P1/P2** | HITL one-finding; suppress-with-reason OK |
| **Mobile doctor** (`ezkey_mobile` `yarn doctor:curated`; [`mobile-doctor/`](mobile-doctor/)) | `mobile-doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (mobile week) | react-doctor + Semgrep + Detekt | **P1 / P2 / P3**; note **only on new P1/P2** | HITL one-finding |
| **React / Admin UI doctor** (`ezkey-admin-ui` `yarn doctor:curated`; [`react-doctor/`](react-doctor/)) | `doctor-curated` | Routine + cloud agent | **Biweekly** rotation slot (React week) | React Doctor shortlist | **P1 / P2 / P3**; note **only on new P1/P2** | HITL one-finding |
| **Quality-gate** (`./scripts/quality-gate.sh`; [`quality-gate/`](quality-gate/)) | `quality-gate` | Cloud agent | **Wednesday 19:00 ET** weekly | Exit 0 GO · 1 NO-GO · 3 GO w/ reservations · 4 INCONCLUSIVE | Report **only if verdict ≠ GO** | Marc / operator on NO-GO or INCONCLUSIVE |
| **endoflife.date check** ([`.github/workflows/endoflife-check.yml`](../../../.github/workflows/endoflife-check.yml); `scripts/endoflife-check.py`) | `major-upgrades` | GHA schedule + `workflow_dispatch` | **Monthly** first Monday **~14:30 UTC** | Register cycles within **90 days** of EOL (or already EOL); source/`eol` drift logged | Standing issue labeled `hygiene`; `follow:` / `accept-risk` suppress proximity unless `eol` date drifts | Non-blocking; Fred prepares quarterly walk; Marc decides majors |
| **Major-upgrades quarterly review** ([`major-upgrades/`](major-upgrades/) `registry.yaml`) | `major-upgrades` | Monday full `dependabot-curated` | **Quarterly** Jan / Apr / Jul / Oct (**October 2026 due now**) | Registry `eol` / `last_review` / `accept_until` | `keep` · `plan` · `follow:` · `accept-risk`; light vs dedicated-issue paths | Fred prepares; Christophe on `security: true`; Patrick build/mobile; **Marc** decides |
| **Security pentest** (skill + `./scripts/security-pentest-curated.sh`; [`security-pentest/`](security-pentest/)) | `security-pentest-curated` | Operator + Christophe | **Monthly with Christophe** | Schemathesis + ZAP + Nuclei curated shortlist | **P1 / P2 / P3** (charter expected-context) | Christophe + HITL one-finding |
| **JavaMelody / perf** (`./scripts/javamelody-curated.sh`; [`javamelody/`](javamelody/)) | `javamelody-curated` | After known workload (often post quality-gate churn) | **Monthly with Carl** | Ranked HTTP/SQL/Spring by total time | **P1 / P2 / P3**; high hit-count on churn paths ≠ signal | Carl + HITL one-finding |
| **Assessment curated** (skill [`.cursor/skills/assessment-curated`](../../../.cursor/skills/assessment-curated/SKILL.md); [`assessment-curated/`](assessment-curated/)) | `assessment-curated` | Operator **mandate** | **On mandate** (not calendar) | Assessment register | Severity / confidence in register; small lot | HITL one-finding; GO = fix + handoff |
| **Document hygiene** (skill [`.cursor/skills/document-hygiene-curated`](../../../.cursor/skills/document-hygiene-curated/SKILL.md); [`document-hygiene/`](document-hygiene/)) | `document-hygiene-curated` | Operator | **Quarterly** | Legacy / duplicate docs | Keep / fold / archive / delete / retarget | HITL one document at a time |
| **Corpus ablation** (skill [`.cursor/skills/corpus-ablation`](../../../.cursor/skills/corpus-ablation/SKILL.md); [`corpus-ablation/`](corpus-ablation/)) | `corpus-ablation` | — | **Not scheduled** — pending **challenger** repositioning (Mathieu mandate 2026-10-19) | Ephemeral plans / prompts | Discoverability-preserving Go packages | Mathieu when challenger lands |
| **Critical review** (skill [`.cursor/skills/critical-review`](../../../.cursor/skills/critical-review/SKILL.md); [`critical-review/`](critical-review/)) | `critical-review` | G1 brief / P0–P1 vision / high-risk plan / PR craft | **Event-driven** (gate), not weekly | ≤10 findings; forced ≠ author model family | Severity in review report | Launcher (Mathieu / Patrick / Christophe) + author HITL |
| **Monthly digest** (skill [`.cursor/skills/monthly-digest`](../../../.cursor/skills/monthly-digest/SKILL.md)) | Publishing | Month closed | **Monthly** (first week after month end) | Distilled activity digest | N/A (comms, not code shortlist) | Editor / Marc before publish |
| **Ops: `check-ezkey-online-hosts`** / **`check-local-mgmt-port-publish`** (`scripts/ops/`; CI jobs) | Ops / CI | CI path filter | Every relevant CI run | Forbidden hosts / mgmt port publish | Fail = fix before merge | Author |
| **Ops: `docker-log-hygiene`** (`scripts/ops/docker-log-hygiene.sh`) | Ops / Lightsail | Pre-upgrade + after compose up | On community upgrade checklist | Missing `max-size` / `max-file` | Fix before `--remote-up` | Operator on VM |
| **Ops: `verify-javamelody-non-exposure`** / **`check-javamelody-spring-json`** / **`validate-caddy-observability-regexp`** | Ops / security | After JM or Caddy change; JM deploy smoke | On change + with monthly JM/pentest when JM in scope | Exposure / JSON drift / regexp | Fail = fix | Operator / Christophe |

Doctor biweekly rotation order (repeat): **Java → mobile → React → …**. Empty high-signal lot → no campaign note.

## Hygiene vs program

Routine polish stays on the lightweight hygiene path (commit/PR + these notes). Aligns with
[`methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`](../../methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md).
