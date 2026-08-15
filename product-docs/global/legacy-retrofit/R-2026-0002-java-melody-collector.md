# Retrofit Slice — `R-2026-0002` Java Melody collector for Ezkey

## Metadata

- **ID:** `R-2026-0002`
- **Status:** `mapped`
- **Source type:** `plan`
- **Capture date:** `2026-05-08`
- **Owner:** product + AI collaboration
- **Confidence:** `high`

## Source batch

- Plan-prompt `.github/prompts/plan-javaMelodyCollectorForEzkey.prompt.md` (deleted 2026-08 corpus-ablation). Decisions extracted below.

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
- **Operator-first (`#5`):** deliberate opt-in keeps clean-start light; document how to enable for local troubleshooting.

## Open question to settle (divergence between plan and user verbatim)

- **Settled (grill D11, 2026-05-19):** **Opt-in** via clean-start / compose (`--with-java-melody` style). Not enabled by default.

## Mapping to canonical destinations

| Canonical destination | Mapping action | Status |
|-----------------------|----------------|--------|
| `product-docs/global/vision/product-orientation-notes.md` (`V-2026-0009`) | Captures the lightweight observability posture | **integrated** in this slice |
| `docs/DEVELOPMENT.md` (or operator-facing observability doc) | Document the collector URL, auto-registration, and `crypto-api` exclusion | gap (pending follow-up at implementation time) |
| Per-module component docs (`admin-api`, `auth-api`, `integration-api`) | Note the management endpoint extension and the Java Melody dependency | gap (pending follow-up at implementation time) |
| `product-docs/global/architecture-decisions.md` | Optional ADR if opt-in vs management-port exposure needs formal record | gap (low priority) |

## Confidence and residual gaps

- **Confidence high** on the technical decisions in the plan.
- **Residual gaps:**
  - ~~Default-vs-opt-in for clean start~~ **settled:** opt-in (`--with-java-melody` style).
  - Exact Java Melody collector WAR version compatibility (the plan flags this as a Phase 1 spike).
  - Long-term observability strategy beyond Java Melody (Prometheus / Grafana / OpenTelemetry) is intentionally out of scope here; should be revisited if installation profiles diverge significantly.

## Grill outcome (2026-05-19)

- **`V-2026-0009` grilled** — opt-in default; DX/troubleshooting scope; no Caddy R1; exclude crypto-api. Session `blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`.

## Next action

- Run Phase 1 compatibility spike (Java Melody artifact versions) before code change.
- Propose an `I-*` for implementation when funded.

## Links

- Source plan-prompt: deleted 2026-08 (git history); this retrofit holds the extracted decisions.
- Derived vision: `V-2026-0009`
- Adjacency: deployment profile context `V-2026-0002` (clean-start is one such profile)
- Methodology: [`legacy-retrofit-workflow.md`](../../methodology/legacy-retrofit-workflow.md)
