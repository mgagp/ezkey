# Mobile doctor-curated — tool evaluation (2026-07-11)

## Purpose

Critical evaluation of open-source static-analysis tools for a **punctual, curated hygiene
pass** on Ezkey Mobile (`ezkey_mobile/`), mirroring Admin UI `doctor-curated` and Java
`java-doctor-curated`: high-signal shortlist, suppressions with reasons, P1/P2/P3 triage,
agent-operable keyword — **not** a CI fail-the-build gate and **not** a zero-warning campaign.

This document is the decision record for the **Go / No-Go** on the tool shortlist before
implementation (`I-2026-07-11-mobile-doctor-curated-hygiene`).

## Comparable models

| Surface | Keyword | Shape |
|---------|---------|-------|
| Admin UI | `doctor-curated` | One upstream tool (`react-doctor`) + local curator |
| Java | `java-doctor-curated` | SpotBugs + Semgrep + narrow PMD → curator + campaign notes |
| Mobile (target) | `mobile-doctor-curated` | Three complementary families → curator + campaign notes |

Java may use **1–3** analyzers because tool **families** differ; the reusable product is still
the **curator layer** + HITL campaign notes.

## Ezkey mobile baseline (do not duplicate in the curated pass)

| Layer | Tool | Role today |
|-------|------|------------|
| Lint / format gate | ESLint 9 + Prettier | `yarn lint` / lint-staged / CI |
| Types | `tsc --noEmit` | `yarn typecheck` / CI |
| Unit tests | Jest | `yarn test` / CI |
| Dependency monitoring | `yarn deps:monitor` | Separate hygiene lane |
| Early quality pipeline | Semgrep + Detekt + `code-quality-curator.mjs` | Exists; not yet elevated to doctor keyword / campaign track |
| Biome | Referenced as optional curator input | **Not configured** (no `biome.json` / scanner) — phantom |

## Hard filters

- OSS with active maintenance
- Report-friendly (JSON / SARIF) without forcing build failure
- Distinct family angle (avoid three tools that only restate style)
- Fit React Native Android-first Ezkey app (TS/JS + Kotlin native)
- Prefer signal over rule-count theatre
- Fit Ezkey 80/20 and stack pragmatism
- Compatible with short Windows path workspace (`C:\w\e`) used for Android/CMake

## Tool families

| Family | Optimizes for | Candidates | Curated-pass fit |
|--------|---------------|------------|------------------|
| React / RN health | Structure, perf, RN anti-patterns | react-doctor (RN pack) | **Yes — keep** |
| Security / project footguns | Insecure APIs, sensitive logging, storage | Semgrep (existing mobile pack) | **Yes — keep** |
| Kotlin maintainability | Smells, complexity, Kotlin conventions | Detekt (already wired) | **Yes — keep** (keep narrow) |
| Lint + format alternative | Conventions / formatting | Biome | **Reject v1** (duplicates ESLint+Prettier gate) |
| Dead code graph | Unused exports / deps / files | Knip (standalone) | Defer (Admin UI suppresses unused-file noise; optional later) |
| Style / types gates | Conventions, types | ESLint, Prettier, tsc | Already gates — **out of curated pass** |
| Aggregator platform | Dashboards + huge rule packs | SonarQube CE | Reject for v1 |
| Native Android Lint | Gradle Android Lint | Android Lint | Defer (Gradle weight; Detekt+Semgrep cover much of Kotlin) |
| Dependency CVEs | Vulnerable packages | Snyk / audit / deps:monitor | Out of scope (separate lane) |

## Inventory — pros / cons

### react-doctor (keep)

- **Family:** React / React Native correctness, performance, architecture anti-patterns
- **Pros:** Same tool family as Admin UI; detects `framework: react-native`; RN-specific rules
  (`rn-*`); JSON output; `npx` ergonomics; living community (Million / react.doctor)
- **Cons:** Some rules are high-volume / preference-adjacent (e.g. `rn-prefer-pressable`);
  dead-code / unused-file signal can be noisy (Admin UI already suppresses `unused-file`)
- **Smoke (2026-07-11, this workspace):** `npx react-doctor@latest . --scope full --no-score
  --blocking none --json --json-compact` → **version 0.7.5**, `ok: true`, framework
  **`react-native`**, **38** warnings across **28** files (132 source files). Top rules:
  `rn-prefer-pressable` (10), `no-barrel-import` (6), `unused-file` (3). Categories: Bugs 18,
  Performance 11, Maintainability 8, Correctness 1. Artifacts under
  `ezkey_mobile/logs/mobile-doctor/raw/` (gitignored via root `logs/`).
- **Verdict:** Primary app-layer input to the curator

### Semgrep OSS (keep)

- **Family:** Pattern matching + security-oriented rules; JS/TS + Kotlin in one runner
- **Pros:** Existing Ezkey pack `ezkey_mobile/semgrep/rules/mobile-security.yml` (focused rules:
  direct `fetch`, sensitive console, AsyncStorage keys, bearer literals, HTTP URLs, Kotlin
  Random/Base64/Log/AES-ECB); Docker fallback already in `semgrep-scan.mjs`; mirrors Java doctor
- **Cons:** Broad public packs are noisy — keep **pinned small** pack; Windows Docker path
  friction (same lessons as Java doctor)
- **Verdict:** Primary security / multi-surface input

### Detekt (keep — narrow)

- **Family:** Kotlin source AST — design quality and maintainability
- **Pros:** Already wired (`detekt/detekt.yml`, `detekt-scan.mjs`, SARIF); complements Semgrep
  on Kotlin (keystore / native crypto path); natural third angle for Android-first Ezkey
- **Cons:** Default Detekt packs can be noisy; current config already disables some style noise
  (`MagicNumber`); keep **narrow** and suppress aggressively in curator
- **Verdict:** Third analyzer input; operator-confirmed complement

### Biome (reject v1)

- **Pros:** Fast lint+format; was an earlier mobile quality bet
- **Cons:** Not actually configured; would duplicate ESLint+Prettier; accidental dual-linter
  complexity
- **Verdict:** Drop from pipeline expectations until a deliberate migration program (if ever)

### Knip (defer)

- **Pros:** Unused exports / dependencies / files
- **Cons:** Overlaps react-doctor unused-* noise; Admin UI experience shows unused-file is often
  low-signal for curated first passes
- **Verdict:** Revisit after first campaigns if dead-code cleanup becomes a deliberate lot

### SonarQube Community (reject v1)

- Same rationale as Java evaluation: ops weight, wrong shape for punctual curated shortlist

### Android Lint / SCA (out of curated pass)

- Android Lint: defer (Gradle coupling). Dependency CVE: keep on `deps:monitor` lane.

## Recommended shortlist (Go confirmed 2026-07-11)

Operator confirmation (session 2026-07-11): react-doctor + Semgrep + Detekt; keyword
`mobile-doctor-curated`.

| Slot | Tool | Family question |
|------|------|-----------------|
| 1 | **react-doctor** | Is this React / RN code structurally or operationally unhealthy? |
| 2 | **Semgrep OSS** with **pinned** Ezkey mobile pack | Is there a security / project footgun (JS or Kotlin)? |
| 3 | **Detekt** with **narrow** config | Is Kotlin Android code poorly designed or hard to maintain? |

**Explicit rejects for v1:** Biome-in-pipeline, SonarQube, Android Lint-in-curator, unfiltered
Semgrep/Detekt catalogues, CI fail-on-findings, Knip as third analyzer (deferred).

## Working method to implement next (after Go)

Mirror Java doctor (more evolved HITL) + Admin UI curator:

1. Entrypoint from `ezkey_mobile/` (prefer evolving `quality-pipeline.mjs` / curator toward doctor
   shape; add Bash wrapper if Windows agents need parity with `java-doctor-curated.sh`)
2. Curator: normalize → suppress map with reasons → P1/P2/P3 → `logs/mobile-doctor/*.curated.*`
3. Keyword `mobile-doctor-curated` in `ezkey_mobile/AGENTS.md` + root `AGENTS.md`
4. Campaign notes: `product-docs/global/hygiene/mobile-doctor/` (TEMPLATE + dated passes)
5. Hygiene posture: overview → HITL one finding at a time → small fix set → hygiene PR
6. Remove Biome phantom from default inputs / docs
7. Priority weighting hint: Semgrep security + react-doctor correctness/bugs → lean P1;
   Detekt design / react-doctor maintainability → lean P2/P3 unless severity is high

## Go / No-Go checklist (operator)

- [x] **Go** on react-doctor + Semgrep + Detekt as the three analyzer inputs
- [x] **Go** on keyword `mobile-doctor-curated`
- [x] **Go** on rejecting Biome for v1 curated pass
- [x] **Go** on punctual curated report (not CI gate)
- [x] Smoke: react-doctor 0.7.5 on `ezkey_mobile/` (38 warnings, RN framework detected)
- [x] After Go: promote `I-2026-07-11-mobile-doctor-curated-hygiene` + first `TB-*`

## Links

- Deleted Cursor plan (materialized); see backlog I/TB below, `ezkey_mobile/AGENTS.md` § Mobile
  doctor-curated pass, `yarn doctor:curated`
- Backlog idea: [`backlog/ideas/I-2026-07-11-mobile-doctor-curated-hygiene.md`](backlog/ideas/I-2026-07-11-mobile-doctor-curated-hygiene.md)
- Tracer bullet: [`backlog/TB-2026-07-11-mobile-doctor-curated-mvp.md`](backlog/TB-2026-07-11-mobile-doctor-curated-mvp.md)
- Java comparable: [`java-doctor-curated-evaluation-2026-07-11.md`](java-doctor-curated-evaluation-2026-07-11.md)
- Admin UI comparable: `ezkey-admin-ui/AGENTS.md` § React Doctor curated pass
- Existing mobile assets: `ezkey_mobile/scripts/quality-pipeline.mjs`, Semgrep + Detekt packs
- Hygiene vs program: `product-docs/methodology/decisions/2026-06-06-methodological-closeout-vs-code-hygiene.md`
