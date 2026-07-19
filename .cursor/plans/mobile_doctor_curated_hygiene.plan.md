# Mobile doctor-curated hygiene — working plan (incubation)

**Status:** evaluation Go confirmed; canon materialized (2026-07-11); MVP implementation next  
**Workspace note:** `C:\w\e` short path for Android/CMake; this lane stays mobile-local.  
**Comparable canon:** Admin UI `doctor-curated`, Java `java-doctor-curated` (+ campaign notes).  
**Method:** plan-incubation → evaluation Go/No-Go → `I-*` / `TB-*` + hygiene folder (mirror Java).

## Operator decisions (locked 2026-07-11)

| Decision | Choice |
|----------|--------|
| Shortlist | **react-doctor** + **Semgrep** + **Detekt** |
| Keyword | **`mobile-doctor-curated`** |
| Sequence | Smoke → evaluation → canon → MVP implementation |
| Biome | Reject for v1 curated pass |
| react-doctor smoke | **0.7.5**, RN detected, **38** warnings / 28 files |

Durable evaluation: `product-docs/global/mobile-doctor-curated-evaluation-2026-07-11.md`  
Idea / TB: `I-2026-07-11-mobile-doctor-curated-hygiene`, `TB-2026-07-11-mobile-doctor-curated-mvp`

---

## Intent

Give Ezkey Mobile the same **punctual continuous-hygiene loop** as Admin UI and Java:

1. Run 2–3 complementary analyzers (not CI fail-the-build).
2. Curate → suppress low-signal with reasons → P1/P2/P3 shortlist.
3. HITL briefing (one finding at a time) → small fix lot → hygiene branch/PR.
4. Dated campaign notes under `product-docs/global/hygiene/mobile-doctor/` (template + pass instances).

**Not** a zero-warning crusade. **Not** Sonar. **Not** a substitute for `yarn validate:ci`.

---

## What already exists (baseline — do not reinvent blindly)

| Layer | Today | Role |
|-------|--------|------|
| Format / lint gate | ESLint 9 + Prettier + `tsc` via `yarn validate:ci` | **CI gate** — out of curated pass |
| Dependency hygiene | `yarn deps:monitor` | Separate lane |
| Security patterns | `semgrep/rules/mobile-security.yml` + `scripts/semgrep-scan.mjs` | Already a strong Semgrep angle |
| Kotlin AST | `detekt/detekt.yml` + `scripts/detekt-scan.mjs` | Already wired |
| Curator | `scripts/code-quality-curator.mjs` + `quality-pipeline.mjs` | Early curator; Lane A/B notes in scripts README |
| Biome | Referenced as optional input; **no** `biome.json` / scanner script | **Phantom** — drop or wire deliberately |

**Maturity gap vs Java doctor:** no keyword in `AGENTS.md`, no evaluation Go/No-Go, no `product-docs/global/hygiene/…` campaign track, HITL contract weaker than Java, outputs under `.monitor/` rather than a clear `logs/mobile-doctor/` doctor shape, Biome gap.

---

## Hard filters (same spirit as Java evaluation)

- OSS, living community, report-friendly (JSON / SARIF)
- Distinct **family questions** (avoid three tools that restate style)
- Fit RN Android-first Ezkey app (TS/React Native + Kotlin native)
- 80/20; curator layer is the product; analyzers are inputs
- Prefer signal over rule-count theatre
- Do not duplicate ESLint/Prettier/`tsc` gates inside the curated pass

---

## Tool inventory (research)

### Families and candidates

| Family question | Candidates | Fit for curated pass |
|-----------------|------------|----------------------|
| React / RN structure, perf, anti-patterns | **react-doctor** (RN pack since 2026; same tool as Admin UI) | **Strong keep candidate** |
| Security / custom footguns (JS + Kotlin) | **Semgrep** (existing mobile pack) | **Keep** (already Ezkey-specific) |
| Kotlin design / maintainability | **Detekt** (already wired) | **Keep** as third angle (narrow config) |
| Dead code / unused exports-deps | Knip (standalone; also historically near react-doctor) | Optional later; Admin UI suppresses unused-file noise |
| Lint+format Rust alternative | Biome | **Defer / drop** — conflicts with ESLint+Prettier gate unless migration program |
| Style / conventions | ESLint + Prettier | Already gate — **out of curated pass** |
| Types | `tsc --noEmit` | Already gate |
| Aggregator | SonarQube CE | **Reject** (same as Java) |
| Dependency CVEs | Snyk / yarn audit / deps:monitor | Separate lane |
| Native Android Lint (Gradle) | Android Lint | Defer — heavier Gradle coupling; Detekt+Semgrep cover much of Kotlin surface |

### Recommended shortlist (provisional Go — confirm in evaluation doc)

Mirror Java’s three distinct questions:

| # | Tool | Family question | Maps to Java analogue |
|---|------|-----------------|------------------------|
| 1 | **react-doctor** | Is this React / RN code structurally or operationally unhealthy? | SpotBugs + PMD (app layer) / Admin UI doctor |
| 2 | **Semgrep** (pinned mobile pack) | Security / project footgun across JS+Kotlin? | Semgrep |
| 3 | **Detekt** (narrow) | Is Kotlin Android code poorly designed / hard to maintain? | Narrow PMD |

**Why not Biome as #1 for “conventions”:** conventions are already gated. Biome was an incomplete earlier bet; adding it now creates dual-linter accidental complexity.

**Why react-doctor over “ESLint plugins only”:** Admin UI already operates this tool; RN-specific rules exist; JSON + curator pattern is proven; complementary to Semgrep (security) and Detekt (Kotlin).

**Why keep Detekt:** Ezkey mobile security path is heavily Kotlin (Keystore / signing). Semgrep catches patterns; Detekt catches Kotlin smells/complexity — complementary.

---

## Target operating model (after MVP)

| Element | Target |
|---------|--------|
| Keyword | `mobile-doctor-curated` (or `doctor-curated-mobile`) — document in `ezkey_mobile/AGENTS.md` + root `AGENTS.md` |
| Entrypoint | Prefer one Bash/Node script from `ezkey_mobile/` (align with `java-doctor-curated.sh` pragmatism; reuse/evolve `quality-pipeline.mjs`) |
| Outputs | `logs/mobile-doctor/*.curated.md|json` + `raw/` (gitignored); retire dual mental model of `.monitor/` vs doctor, or document `.monitor` as raw only |
| Config | Pin Semgrep pack + Detekt config + suppressions with reasons (like `config/java-doctor/`) |
| Campaign notes | `product-docs/global/hygiene/mobile-doctor/` — README + TEMPLATE + dated passes |
| Posture | Hygiene lane: branch + PR; no `I-*` per finding; HITL one-item-at-a-time (copy Java contract) |

---

## Work phases

### Phase 0 — Evaluation record (this research → durable)

- Write `product-docs/global/mobile-doctor-curated-evaluation-YYYY-MM-DD.md` (mirror Java evaluation).
- Confirm Go/No-Go on the three-tool shortlist; record rejects (Biome v1, Sonar, Android Lint defer).
- Smoke: one `npx react-doctor` run on `ezkey_mobile/` (JSON) to size noise before curator design.

### Phase 1 — Canon materialization

- `I-2026-…-mobile-doctor-curated-hygiene` (Lane C tooling capability; no separate `V-*`).
- `TB-2026-…` MVP: keyword + entrypoint + curator shortlist + AGENTS contract + hygiene folder template.
- Optional GitHub issue for board visibility (like #326 for Java).

### Phase 2 — Curator MVP (implementation)

- Add react-doctor invocation to the pipeline (Admin UI `doctor-curated.mjs` as template).
- Keep Semgrep + Detekt; **remove or explicitly gate Biome** so the pipeline does not pretend it runs.
- Suppress map with written reasons; P1/P2/P3; embedded planning contract in curated MD.
- Align paths/naming with Java (`logs/mobile-doctor/`, suppressions file committed under `ezkey_mobile/` or `config/mobile-doctor/`).
- Document keyword + HITL in `ezkey_mobile/AGENTS.md` and root `AGENTS.md`.

### Phase 3 — First campaign

- Run `mobile-doctor-curated`; HITL lot (3–6); campaign note; small hygiene PR.
- Learn suppressions; tighten Detekt/Semgrep packs if noise dominates.

### Out of scope for v1

- Replacing ESLint/Prettier with Biome
- Making the curated pass a CI gate
- iOS-specific rule packs
- Dependency CVE scanning inside the doctor
- Broad zero-warning cleanup

---

## Decisions (confirmed with operator 2026-07-11)

1. **Shortlist Go** — react-doctor + Semgrep + Detekt.
2. **Keyword** — `mobile-doctor-curated`.
3. **Evolve vs rewrite** — Prefer evolve `quality-pipeline.mjs` / curator (TB MVP).
4. **Biome** — Drop from docs/pipeline for v1.
5. **Canon** — Evaluation + `I-*` / `TB-*` + hygiene folder materialized after smoke.

## Next step

MVP pipeline landed (`yarn doctor:curated`). Next: first HITL hygiene campaign using the curated
report — not more tooling unless noise forces suppressions.

---

## Traceability (when materializing)

- Working plan: this file
- Pattern sources: `product-docs/global/java-doctor-curated-evaluation-2026-07-11.md`, `product-docs/global/hygiene/java-doctor/`, `ezkey-admin-ui/scripts/doctor-curated.mjs`
- Existing mobile assets: `ezkey_mobile/scripts/quality-pipeline.mjs`, Semgrep + Detekt packs
- Methodology: hygiene vs program; plan-incubation → canonical docs
