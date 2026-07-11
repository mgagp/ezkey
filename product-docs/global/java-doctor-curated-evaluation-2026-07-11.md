# Java doctor-curated — tool evaluation (2026-07-11)

## Purpose

Critical evaluation of open-source Java (and adjacent) static-analysis tools for a **punctual,
curated hygiene pass** mirroring Admin UI `doctor-curated`: high-signal shortlist, suppressions with
reasons, P1/P2/P3 triage, agent-operable keyword — **not** a CI fail-the-build gate and **not** a
zero-warning campaign.

This document is the decision record for the **Go / No-Go** on the tool shortlist before
implementation (`I-2026-07-11-java-doctor-curated-hygiene`).

## Comparable model (Admin UI)

Admin UI does **not** merge three analyzers. It runs one upstream tool (`react-doctor`) and a local
curator ([`ezkey-admin-ui/scripts/doctor-curated.mjs`](../../ezkey-admin-ui/scripts/doctor-curated.mjs)):

1. Suppress low-signal rules (with written reasons)
2. Reweight remaining findings to P1 / P2 / P3
3. Emit `logs/react-doctor/*.curated.md|json` with an embedded planning contract
4. Keyword `doctor-curated` in `ezkey-admin-ui/AGENTS.md` (no dedicated skill)

Java may use **1–3** analyzers because tool **families** differ, but the reusable product is still
the **curator layer**.

## Ezkey baseline (do not duplicate in the curated pass)

| Layer | Tool | Role today |
|-------|------|------------|
| Format | Spotless | Enforced in `scripts/build.sh` |
| Style / Javadoc | Checkstyle | Enforced in `scripts/build.sh` |
| Coverage reports | JaCoCo | Bound on `test`; not a gate |
| Opt-in recipes | OpenRewrite | Parent POM; not in baseline |

Root POM still pins unused `${spotbugs.version}` (`4.7.3`, stale for JDK 25) and `${pmd.version}`
with **no plugins wired**. There is no Java equivalent of `doctor-curated` today.

## Hard filters

- OSS with active maintenance
- Report-friendly (JSON / SARIF / XML) without forcing build failure
- Compatible with **JDK 25** (Ezkey baseline)
- Distinct family angle (avoid three tools that only restate style)
- Prefer signal over rule-count theatre
- Fit Ezkey 80/20 and stack pragmatism

## Tool families

| Family | Optimizes for | Candidates | Curated-pass fit |
|--------|---------------|------------|------------------|
| JVM bug patterns (bytecode) | Correctness, concurrency, classic defect patterns | SpotBugs | **Yes — keep** |
| Compiler checks | High-signal Google patterns at compile time | Error Prone | Defer (wiring cost) |
| Source smells / design / maintainability | “Is this poorly designed or hard to maintain?” | PMD | **Yes — keep** (narrow ruleset only) |
| Pattern / security / multi-surface | Insecure APIs, footguns, Docker/TS later | Semgrep | **Yes — keep** |
| JVM security plugin | Web/crypto injection patterns on bytecode | Find Security Bugs | Defer (overlap with Semgrep) |
| Dependency CVEs | Vulnerable jars | OWASP DC / Snyk SCA | Out of scope (separate lane) |
| Style | Naming / Javadoc conventions | Checkstyle | Already a gate |
| Architecture as tests | Package/layer rules | ArchUnit | Out of scope (unit tests) |
| Aggregator platform | Dashboards + huge rule packs | SonarQube CE | Reject for v1 |
| Nullness type system | Proven null contracts | NullAway / Checker | Defer (annotation culture) |

## Inventory — pros / cons

### SpotBugs (keep)

- **Family:** JVM correctness via bytecode
- **Pros:** Mature FindBugs successor; strong reputation for real bugs with manageable FPs; Maven
  plugin (`spotbugs-maven-plugin` **4.10.2.0** / SpotBugs **4.10.2**); Java 25 support from
  SpotBugs **≥ 4.9.7**; natural report-only `spotbugs:spotbugs` goal; unused version property
  already exists (must be upgraded)
- **Cons:** Needs compiled classes; weaker on source-only / config / Docker; stale `4.7.3` pin in
  repo must not be used as-is
- **Verdict:** Primary JVM-bug input to the curator

### Semgrep OSS (keep)

- **Family:** Pattern matching + security-oriented rules; multi-language (Java, TypeScript, Docker, …)
- **Pros:** Clean `--json` / SARIF; fast CLI; no compile step; excellent curator ergonomics; can
  later extend the same runner to Dockerfiles and (optionally) Admin UI TS without a second
  platform; active community / frequent releases (image smoke pulled **1.168.0**)
- **Cons:** Does not replace deep JVM bytecode analysis; “many rules” is a **noise risk** unless
  packs are pinned small; CLI outside Maven (acceptable for punctual hygiene, same shape as
  `npx react-doctor`); Windows path / Docker mount friction observed in this session’s smoke
- **Verdict:** Primary security / multi-surface input; preferred over FindSecBugs for v1

### Find Security Bugs (defer)

- **Family:** SpotBugs security plugin
- **Pros:** Maven-native; good Java/web security detectors
- **Cons:** Overlaps Semgrep’s security angle; another ruleset to suppress; Java-only
- **Verdict:** Revisit only if Semgrep packs leave a clear JVM-security gap after first campaigns

### Error Prone (defer)

- **Family:** Compiler plugin correctness
- **Pros:** Very high signal; Google-backed; JDK 21+ runner
- **Cons:** Invasive `javac` / `--add-exports` wiring on modern JDKs; awkward as a punctual
  report-only hygiene tool; overlap with SpotBugs; higher accidental complexity for v1
- **Verdict:** Strong future candidate if compile integration is cheap after the three-tool curator settles

### PMD (keep — narrow design / maintainability pack)

- **Family:** Source AST — design quality and maintainability (primary question: *is this poorly
  designed or hard to maintain?*)
- **Pros:** Complements SpotBugs (correctness) and Semgrep (security/patterns) with a **third
  distinct angle**; Maven-native; unused `${pmd.version}` already in parent POM; strong categories
  for design / complexity / questionable constructs when packs are chosen deliberately
- **Cons:** Default / broad packs are noisy and can overlap Checkstyle (style-adjacent) or SpotBugs
  (some correctness); without a **pinned narrow ruleset**, it fights the doctor-curated goal
- **Constraint for v1:** ship only a small curated ruleset focused on design/maintainability
  (e.g. selected `design` / complexity / empty-catch style maintainability rules) — **not** the
  full PMD Java catalogue; suppress aggressively in the curator like Admin UI low-signal rules
- **Verdict:** Third analyzer input; operator-confirmed complement to SpotBugs + Semgrep

### SonarQube Community (reject v1)

- **Pros:** Broad coverage, UI, history
- **Cons:** Ops weight, rule overlap, tuning cost vs Ezkey 80/20; wrong shape for a punctual
  curated shortlist script
- **Verdict:** Out

### ArchUnit (reject for this script)

- **Pros:** Excellent for enforceable architecture invariants
- **Cons:** Belongs in unit tests, not a triage report
- **Verdict:** Out of curated-pass scope

### OWASP Dependency-Check / SCA (reject for this script)

- **Pros:** Dependency CVE signal
- **Cons:** Different lane (supply chain); already discussed elsewhere as ad hoc / not wired
- **Verdict:** Out of code-hygiene curated pass

### OpenRewrite (complement later)

- **Pros:** Already in parent POM; applies fixes
- **Cons:** Recipe runner, not a triage reporter
- **Verdict:** May help *after* curated findings, not as analyzer #1

## Smoke notes (this session)

| Attempt | Result |
|---------|--------|
| Semgrep Docker `returntocorp/semgrep:latest` | Image pulled; engine **1.168.0**. After fixing Git Bash path conversion (`MSYS_NO_PATHCONV`), scan of `ezkey-core/src/main/java` with `--config=p/java` completed: **229 files scanned, 0 findings, 0 errors**. A follow-up Node one-liner failed on shell quoting (not on Semgrep). |
| SpotBugs Maven | Not wired; no throwaway plugin commit (evaluation-only session). |
| Local `semgrep` binary | Not installed on PATH. |

**Implication for implementation:** first TB must still document a **reproducible invoke** (pinned Semgrep binary or Docker + `MSYS_NO_PATHCONV=1` on Windows, plus SpotBugs and PMD report-only). The zero-hit `p/java` smoke on `ezkey-core` is encouraging for Semgrep noise, but v1 should still **pin small explicit packs** for Semgrep and PMD rather than enabling full catalogues. Also run SpotBugs + narrow-PMD smoke before claiming coverage baselines.

## Recommended shortlist (pending Go / No-Go)

Three complementary families (operator revision 2026-07-11 — PMD added for design/maintainability):

| Slot | Tool | Family question |
|------|------|-----------------|
| 1 | **SpotBugs** (≥ 4.9.7 / prefer current 4.10.x via plugin 4.10.2.0) | Is there a real JVM / bytecode defect? |
| 2 | **Semgrep OSS** with a **pinned, small** rule pack | Is there a security / pattern / multi-surface footgun? |
| 3 | **PMD** with a **pinned, narrow** design/maintainability ruleset | Is this poorly designed or hard to maintain? |

**Explicit rejects for v1:** SonarQube, ArchUnit-in-report, OWASP DC-in-curator, Checkstyle re-export,
FindSecBugs (unless Semgrep gap proven), Error Prone (unless compile wiring becomes cheap later),
unfiltered Semgrep or PMD catalogues, CI fail-on-findings.

## Working method to implement next (after Go)

Mirror Admin UI:

1. Bash entrypoint (Git Bash on Windows): invoke SpotBugs + PMD (Maven report-only) + Semgrep (JSON)
2. Curator (Node or Python): normalize → suppress map → P1/P2/P3 → `logs/java-doctor/*.curated.*`
3. Keyword (proposed: `java-doctor-curated`) + AGENTS.md section; skill only if keyword+AGENTS insufficient
4. Hygiene posture: briefing → small fix set + modest low-signal allotment → hygiene PR; not methodology theatre for routine polish
5. Default analysis scope: production modules (`ezkey-core`, `ezkey-core-security`, `ezkey-admin-api`,
   `ezkey-auth-api`, `ezkey-integration-api`); demos/tests optional later
6. Priority weighting hint: SpotBugs correctness / Semgrep security → lean P1; PMD design smells → lean P2/P3 unless severity is high

## Go / No-Go checklist (operator)

- [ ] **Go** on SpotBugs + Semgrep + **narrow PMD** as the three analyzer inputs
- [ ] **Go** on pinning small Semgrep and PMD packs (not full catalogues)
- [ ] **Go** on deferring FindSecBugs / Error Prone as documented
- [ ] **Go** on punctual curated report (not CI gate)
- [ ] After Go: promote `I-2026-07-11-java-doctor-curated-hygiene` toward `ready` / first `TB-*`

## Links

- Backlog idea: [`backlog/ideas/I-2026-07-11-java-doctor-curated-hygiene.md`](backlog/ideas/I-2026-07-11-java-doctor-curated-hygiene.md)
- Admin UI comparable: `ezkey-admin-ui/AGENTS.md` § React Doctor curated pass
- Hygiene vs program: `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`
- Incubation: Plan mode (ephemeral scaffold; durable signal lives in this note + `I-*`)
