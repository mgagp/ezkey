# Backlog Idea — `I-2026-08-24-java-melody-collector` JavaMelody collector (opt-in Docker)

## Metadata

- **ID:** `I-2026-08-24-java-melody-collector`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-08-24`
- **Updated at:** `2026-08-24`
- **Last reviewed at:** `2026-08-24`
- **Component tags:** `infra`, `admin-api`, `auth-api`, `integration-api`, `docs`
- **Captured by:** Marc
- **Parent vision:** [`V-2026-0009`](../../vision/V-2026-0009-java-melody-observability.md)
- **Retrofit:** [`R-2026-0002`](../../legacy-retrofit/R-2026-0002-java-melody-collector.md)
- **Tracer bullet:** [`TB-2026-08-24-java-melody-collector`](../TB-2026-08-24-java-melody-collector.md)

## Intent

Ship an **opt-in** JavaMelody collector in the local Docker stack so operators can inspect HTTP,
memory, and CPU patterns on Admin API, Auth API, and Integration API — in **baseline clean-start**
and in **HA** (`2×` each API). This is a DX / troubleshooting tool, not a production APM.

## Problem and value

- **Problem:** local performance feedback today is Actuator health/metrics plus optional JMX. There
  is no aggregated request/memory dashboard across the three boot APIs, and HA replicas are
  especially hard to compare.
- **Expected value:** one collector UI (`http://localhost:8088`) that shows the three applications
  (and both replicas in HA) after ordinary functional-test traffic.

## Grilling / funding decisions (2026-08-24)

Settled earlier in grill D11 (2026-05-19), **funded** for implementation 2026-08-24:

- Opt-in via `./ezkey-tests/clean-start.sh --with-java-melody` (combinable with `--ha`).
- In scope: admin-api, auth-api, integration-api. **Exclude** crypto-api.
- Management-port reports (`/actuator/monitoring`). No Caddy for the collector.
- Validate **both** topologies: default stack and HA.
- HA: one collector application name per API, with **two node URLs** (replicas), not six app names.

## Scope

- **In scope:** Spring Boot 4 starter on the three APIs (disabled by default); collector container;
  compose overlays; clean-start / start / start-ha flags; operator docs; live proof on both stacks.
- **Out of scope:** Prometheus / Grafana / OpenTelemetry; Caddy front for the collector; Crypto API;
  production APM hardening (auth on `/monitoring` beyond local-only exposure).

## Links

- Vision: `V-2026-0009`
- Retrofit: `R-2026-0002`
- TB: `TB-2026-08-24-java-melody-collector`
- Grill: [`../grill-sessions/blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md`](../grill-sessions/blitz-2026-05-08-2-D2-D11-retrofit-grill-me.md)
