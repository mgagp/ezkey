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
- **Collector pull (Docker).** The collector scrapes `/actuator/monitoring` from explicit Docker DNS URLs in `applications.properties`. App-advertised auto-register is not the Docker source of truth (HA hostnames are known and stable).
- **Stable application names** (`admin-api`, `auth-api`, `integration-api`) — not derived from hostname or context, so the same names persist across environments.
- **HA:** one application name per API with **two node URLs** (replicas). Do not invent six collector applications.
- **Versions:** `javamelody-spring-boot4-starter` **2.8.0** and collector WAR **2.8.0** (spike settled 2026-08-24 against Spring Boot 4.1.x).
- **Validation topologies (funded 2026-08-24):** baseline `clean-start.sh --with-java-melody` **and** `clean-start.sh --ha --with-java-melody`.

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
| `docker/README.md` + `docs/LOCAL_STACK_PORTS.md` | Collector URL, `--with-java-melody`, crypto-api exclusion, HA node URLs | **in this slice** |
| Per-module `CONFIGURATION.md` (`admin-api`, `auth-api`, `integration-api`) | Note the management endpoint and the JavaMelody dependency | **in this slice** |
| `product-docs/global/architecture-decisions.md` | Optional ADR if opt-in vs management-port exposure needs formal record | gap (low priority) |

## Confidence and residual gaps

- **Confidence high** on the technical decisions in the plan.
- **Residual gaps:**
  - ~~Default-vs-opt-in for clean start~~ **settled:** opt-in (`--with-java-melody` style).
  - ~~Exact Java Melody collector WAR version compatibility~~ **settled 2026-08-24:** starter + collector WAR **2.8.0**.
  - Long-term observability strategy beyond Java Melody (Prometheus / Grafana / OpenTelemetry) is intentionally out of scope here; should be revisited if installation profiles diverge significantly.

## Grill outcome (2026-05-19)

- **`V-2026-0009` grilled** — opt-in default; DX/troubleshooting scope; no Caddy R1; exclude crypto-api. Session `blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`.

## Next action

- Execute funded slice `I-2026-08-24-java-melody-collector` / `TB-2026-08-24-java-melody-collector`.

## Links

- Source plan-prompt: deleted 2026-08 (git history); this retrofit holds the extracted decisions.
- Derived vision: `V-2026-0009`
- Implementation: `I-2026-08-24-java-melody-collector`, `TB-2026-08-24-java-melody-collector`
- Adjacency: deployment profile context `V-2026-0002` (clean-start is one such profile)
- Methodology: [`legacy-retrofit-workflow.md`](../../methodology/legacy-retrofit-workflow.md)
