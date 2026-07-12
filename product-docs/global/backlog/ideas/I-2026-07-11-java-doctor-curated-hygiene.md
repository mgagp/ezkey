# Backlog Idea — `I-2026-07-11` Java doctor-curated continuous hygiene

## Metadata

- **ID:** `I-2026-07-11-java-doctor-curated-hygiene`
- **Status:** `ready` (MVP tooling landed under `TB-2026-07-11-java-doctor-curated-mvp`; close when first hygiene campaign PR merges or TB exits)
- **Priority:** `P3`
- **Created at:** `2026-07-11`
- **Updated at:** `2026-07-11`
- **Last reviewed at:** `2026-07-11`
- **Progression markers:** `P3-polish`
- **Component tags:** `core`, `admin-api`, `auth-api`, `integration-api`, `docs`, `tooling`
- **Lane:** `C`
- **Captured by:** Marc (continuous-improvement intent; Plan-mode incubation 2026-07-11)
- **GitHub issue:** `#326`
- **Issue labels:** `lane:c`, `type:chore`, `component:core`, `component:admin-api`, `component:auth-api`, `component:integration-api`, `component:infra`, `priority:p3`, `status:ready`
- **Tracer bullet:** `TB-2026-07-11-java-doctor-curated-mvp`

## Intent

Give maintainers a **punctual, curated Java (and adjacent) static-analysis pass** — same operating
shape as Admin UI `doctor-curated`: run selected analyzers, suppress low-signal rules with reasons,
emit a weighted P1/P2/P3 shortlist, and let a cold agent apply a small hygiene fix set worth doing.

## Problem and value

- **Problem:** Day-to-day Java quality is limited to Spotless + Checkstyle (gates) and JaCoCo
  reports. There is no high-signal, non-CI triage loop for JVM correctness / security / smell
  findings comparable to Admin UI React Doctor curation. SpotBugs/PMD version pins exist unused.
- **Expected value:** A repeatable continuous-improvement circle: run → curated report → brief →
  small PR. Improves maintainability without Sonar-scale ceremony or zero-warning crusades.

## Why no separate `V-*`

Orientation already exists via the Admin UI doctor-curated pattern and project values (80/20,
hygiene vs program). This idea is **tooling capability**, not a product-direction vision. Durable
evaluation lives in the linked evaluation note; a vision note would add ceremony without new
product intent.

## Evaluation outcome (Go 2026-07-11)

Authoritative analysis:
[`../java-doctor-curated-evaluation-2026-07-11.md`](../java-doctor-curated-evaluation-2026-07-11.md).

**Analyzer shortlist (v1) — Go confirmed:**

| Tool | Family question |
|------|-----------------|
| SpotBugs (current 4.10.x line; not stale `4.7.3`) | JVM / bytecode defect? |
| Semgrep OSS (pinned small rule pack + JSON) | Security / pattern / multi-surface footgun? |
| PMD (pinned **narrow** design/maintainability ruleset) | Poorly designed or hard to maintain? |

**Deferred:** Error Prone, Find Security Bugs (unless Semgrep gap).
**Rejected for v1:** SonarQube, ArchUnit-in-report, OWASP DC-in-curator, Checkstyle re-export,
full unfiltered PMD or Semgrep catalogues.

**Method:** Bash entrypoint + curator → `logs/java-doctor/`; keyword
`java-doctor-curated`; AGENTS.md contract; report-only (not `scripts/build.sh` fail path).

## Scope

- **In scope (implementation):**
  - Wire SpotBugs + PMD report-only (upgrade version properties; do not fail baseline build by default)
  - Invoke Semgrep with pinned config; normalize JSON into curato
  - Pin a narrow PMD ruleset aimed at design/maintainability (not the full Java catalogue)
  - Curator script (suppress / weight / curated MD+JSON) mirroring Admin UI docto
  - Agent keyword + AGENTS.md (root); modest low-signal allotment doctrine
  - Default module scope: core + core-security + admin/auth/integration APIs
- **Out of scope (v1):**
  - Making findings a CI or `build.sh` gate
  - SonarQube / full PMD catalogue / Error Prone wiring
  - Dependency CVE scanning as part of this pass
  - Auto-fixing every finding; methodology `TB-*` theatre for each polish item

## Key assumptions

- Operator confirmed Go on the evaluation shortlist (2026-07-11).
- Semgrep and PMD remain **curated packs** (not entire registries).
- Hygiene posture matches Admin UI: briefing → small set → hygiene PR.
- Windows agents use Git Bash; Semgrep invocation must avoid MSYS path-conversion pitfalls.

## Risks and exceptions

- Semgrep Windows/Docker path friction observed during evaluation smoke — TB must prove a
  reliable invoke path (`MSYS_NO_PATHCONV=1`).
- SpotBugs needs compiled classes → curated script depends on a prior compile of target modules.
- Over-broad Semgrep or PMD packs will drown the curator; pin early and suppress with reasons.
- If FindSecBugs later proves necessary, add as SpotBugs plugin rather than a fourth platform.

## Candidate first slice (active TB)

[`TB-2026-07-11-java-doctor-curated-mvp`](../TB-2026-07-11-java-doctor-curated-mvp.md):

1. SpotBugs + narrow PMD report-only + Semgrep JSON on default production modules
2. Curator MVP: suppress map + P1/P2/P3 + curated MD/JSON under `logs/java-doctor/`
3. Keyword + AGENTS.md section
4. One dry-run campaign briefing (no obligation to fix everything)

## Promotion notes

- Moved to `ready` after operator Go on evaluation checklist (2026-07-11).
- TB created for curator MVP; GitHub issue opened with implementation session.

## Automation follow-up (optional)

- **Candidate:** Cursor keyword `java-doctor-curated` (AGENTS.md first; skill only if needed)
- **Trigger:** After curator script lands and one successful human/agent campaign

## Links

- Evaluation (decision record): [`../java-doctor-curated-evaluation-2026-07-11.md`](../java-doctor-curated-evaluation-2026-07-11.md)
- Tracer bullet: [`../TB-2026-07-11-java-doctor-curated-mvp.md`](../TB-2026-07-11-java-doctor-curated-mvp.md)
- Comparable: `ezkey-admin-ui/AGENTS.md` § React Doctor curated pass; `ezkey-admin-ui/scripts/doctor-curated.mjs`
- Hygiene vs program: `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`
- Plan incubation: ephemeral Plan-mode scaffold (2026-07-11); durable signal in evaluation + this `I-*`
