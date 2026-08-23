# Dependabot curated campaign — 2026-08-23 google-java-format

## Metadata

- **Date:** 2026-08-23
- **Operator:** Marc (+ agent; operator asked to execute the nested-pin recommendation now)
- **Skill:** `dependabot-curated` § *Java BOM pulse* (nested pin only)
- **Open Dependabot PRs at start:** not listed; not a weekly sweep
- **Highest accepted tier:** T2 tooling

## Lots overview

1. **Already deferred — skip HITL:** TypeScript 7 train unchanged; Tink / ShedLock / ipaddress
   handoff left for a later session.
2. **Lot A (T2 nested formatter pin):** `google-java-format` `1.35.0` → `1.36.1`, lifted to
   parent property `google-java-format.version`.

## Decisions

| Lot | PRs | Tier | Decision | Notes |
|-----|-----|------|----------|-------|
| A | none (manual) | T2 | apply | Dependabot does not see the Spotless XML pin |

## Rationale (short)

### Lot A — google-java-format 1.36.1

Pulse check (GitHub releases + Maven Central POM): latest is **1.36.1** (2026-07-30), not 1.35.0.
`1.36.0` improved Markdown Javadoc; `1.36.1` fixed indented / blank-line list formatting. Ezkey
runs Spotless with `formatJavadoc=true`, so those notes are a real formatter reason, not calendar
churn. Spotless itself stayed on `3.9.0`. JDK 25 already worked on 1.35.0.

Also lifted the literal into `${google-java-format.version}` so the weekly pulse has a first-class
pin. No Dependabot group added (still not a GAV Dependabot reliably updates).

## Validation evidence

| Step | Ran? | Result |
|------|------|--------|
| Dependabot PR CI (per merged PR) | n/a | No Dependabot PR |
| Java BOM pulse (`spring-boot.version` vs current same-minor) | n/a | Nested-pin slice only; Boot left to the 4.1.1 hygiene branch |
| SEC-019 overrides reviewed after Boot bump | n/a | No Boot bump |
| Nested pin: `google-java-format` | yes | **1.36.1** applied; property `google-java-format.version` |
| `./scripts/build.sh` | yes | Exit 0. Spotless rewrapped one Javadoc in `EzkeyClient.java`. Unit tests green (demo-device 28/28; reactor SUCCESS). |
| Clean-start stack | n/a | Formatter / property only |
| Functional tests | n/a | No runtime dep |
| Elective functional | n/a | |
| Playwright Admin UI | n/a | No UI surface |
| Exact pin preserved (no accidental `^`) | n/a | Maven property |
| Documented pins synced (`AGENTS.md`, …) | yes | Skill + dependabot README + AGENTS HITL point at the property |
| Workspace install (`npm ls` / Yarn clean) | n/a | |
| Codegen after Orval / OpenAPI generator bump | n/a | |
| Exploratory human | n/a | |

## Holds and deferrals

- Tink / ShedLock / ipaddress parent-pin lift: [`handoff-centralize-core-pins.md`](handoff-centralize-core-pins.md)
- Docker Compose image tags: operator-owned, out of this pulse

## Out of scope this pass

- Weekly Dependabot PR triage
- Spring Boot BOM pulse
- Docker images
