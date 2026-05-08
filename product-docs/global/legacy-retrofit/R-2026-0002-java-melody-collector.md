# Retrofit Slice — `R-2026-0002` Java Melody collector for Ezkey

## Metadata

- **ID:** `R-2026-0002`
- **Status:** `captured`
- **Source type:** `plan`
- **Capture date:** `2026-05-08`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md` — active plan-prompt with phases, decisions, file map, and verification criteria.

## Trigger

User dictation (blitz `_blitz-2026-05-08-2.md`, item D11) requested observability via Java Melody. Lookup confirmed an existing detailed plan-prompt that already maps the integration end to end. The retrofit canonizes that plan content into product-docs.

## Extracted decisions and invariants

- **Java Melody is the chosen lightweight tool.** Not Prometheus, not Grafana, not a full APM stack at this stage.
- **In scope:** `admin-api`, `auth-api`, `integration-api`. **Excluded:** `crypto-api`.
- **Use the official Spring Boot 4 starter** (`javamelody-spring-boot4-starter`) for the monitored APIs.
- **Run a dedicated collector container.** Standalone official WAR (`javamelody-collector-server.war`) in its own Docker service, with a persistent volume for the collector state.
- **Expose monitoring through the management port (`/actuator/monitoring`), not on the business port.** This avoids opening monitoring endpoints on application traffic interfaces.
- **No Caddy proxy for the collector at this stage.** Direct local port exposure only.
- **Auto-registration on startup.** Each monitored API registers and deregisters itself with the collector via the Java Melody node API at lifecycle hooks.
- **Stable application names** (`admin-api`, `auth-api`, `integration-api`) — not derived from hostname or context, so the same names persist across environments.

## Patterns

- **Simplicity (`Design Principle #1`):** a lightweight off-the-shelf tool with low integration cost.
- **Stay within the chosen stack (`#7`):** Java Melody is well established in the Spring Boot ecosystem; no exotic dependency.
- **Operator-first (`#5`):** zero-config DX in default clean start (per the plan), so the operator sees something useful immediately.

## Open question to settle (divergence between plan and user verbatim)

- **Plan position:** **enabled by default** in clean start (zero-config DX).
- **User verbatim:** **opt-in** (`--with-java-melody` style) to keep clean start light.
- **Recommendation for grilling:** keep the plan's "enabled by default in `clean-start`" posture for developer environments, but add a documented Docker compose override so production-leaning installations can disable it cleanly. This balances DX and right-sizing.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/global/vision/product-orientation-notes.md` (`V-2026-0009`) | Captures the lightweight observability posture | **integrated** in this slice |
| `docs/DEVELOPMENT.md` (or operator-facing observability doc) | Document the collector URL, auto-registration, and `crypto-api` exclusion | gap (pending follow-up at implementation time) |
| Per-module component docs (`admin-api`, `auth-api`, `integration-api`) | Note the management endpoint extension and the Java Melody dependency | gap (pending follow-up at implementation time) |
| `product-docs/global/architecture-decisions.md` | Optional ADR if the default-vs-opt-in question proves controversial | gap (pending decision) |

## Confidence and residual gaps

- **Confidence high** on the technical decisions in the plan.
- **Residual gaps:**
  - Default-vs-opt-in for clean start (see above).
  - Exact Java Melody collector WAR version compatibility (the plan flags this as a Phase 1 spike).
  - Long-term observability strategy beyond Java Melody (Prometheus / Grafana / OpenTelemetry) is intentionally out of scope here; should be revisited if installation profiles diverge significantly.

## Principle candidates

None new. The retrofit reinforces `#1` (simplicity), `#7` (stay within the stack), `#5` (operator-first).

## Next action

- Settle the default-vs-opt-in question via grilling on `V-2026-0009`.
- Run the Phase 1 compatibility spike from the plan (Java Melody artifact versions) before any code change.
- Once settled, propose an `I-*` for the implementation following the plan's phases.

## Links

- Source plan-prompt: `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md`
- Derived vision: `V-2026-0009`
- Adjacency: deployment profile context `V-2026-0002` (clean-start is one such profile)
- Methodology: [`legacy-retrofit-workflow.md`](../../methodology/legacy-retrofit-workflow.md)
