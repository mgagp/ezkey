# Tracer Bullet Brief — `TB-2026-08-24-java-melody-collector` JavaMelody collector (opt-in)

## Metadata

- **ID:** `TB-2026-08-24-java-melody-collector`
- **Status:** `under-review`
- **Related idea:** [`I-2026-08-24-java-melody-collector`](ideas/I-2026-08-24-java-melody-collector.md)
- **Parent context:** `V-2026-0009`, `R-2026-0002`, grill D11 (2026-05-19), funded 2026-08-24
- **Lane:** `A`
- **Posture:** `single-pass`
- **Component tags:** `infra`, `admin-api`, `auth-api`, `integration-api`, `docs`
- **Created at:** `2026-08-24`
- **Updated at:** `2026-08-24`
- **Captured by:** Marc

## Objective

Add an opt-in JavaMelody collector to the local Docker stack and prove metric collection on
**baseline** clean-start and **HA** (`2×` admin / auth / integration) after functional-test
traffic, by browsing the collector dashboard.

## Execution choices (locked, not re-grilled)

| Topic | Choice |
| --- | --- |
| Enablement | Opt-in `--with-java-melody` (not default). Combinable with `--ha`. |
| Apps | admin-api, auth-api, integration-api. Crypto API excluded. |
| Starter | `javamelody-spring-boot4-starter` **2.8.0** (Spring Boot 4.1.x). |
| Reports | Management port `/actuator/monitoring` only. Business ports stay closed for `/monitoring`. |
| Collector | Official collector WAR 2.8.0 in a dedicated container. Host UI: `http://localhost:8088`. No Caddy. |
| Registration | Collector **pull** via `applications.properties` (explicit Docker DNS URLs). More reliable in HA than app-advertised auto-register. |
| HA naming | One application name per API; comma-separated replica URLs. |
| Default | `javamelody.enabled=false` in API config so IDE / unit tests / docker without the flag stay dark. |

## Boundaries in scope

- Parent BOM pin + starter dependency on the three boot APIs.
- Compose overlays `docker-compose.javamelody.yml` and `docker-compose.ha.javamelody.yml`.
- Flags: `clean-start.sh`, `docker/start.sh`, `docker/start-ha.sh`.
- Operator docs: `docker/README.md`, `docker/README-HA.md`, `docs/LOCAL_STACK_PORTS.md`, per-API `CONFIGURATION.md` pointer.

## Out of scope

- Production APM, Prometheus/Grafana/OTel, Caddy for the collector, Crypto API, authenticated
  `/monitoring` beyond local Docker exposure.

## Validation (required)

1. `./scripts/build.sh`
2. `./ezkey-tests/clean-start.sh --with-java-melody` then `mvn test -pl ezkey-tests` for volume.
3. Browser: collector shows admin-api, auth-api, integration-api with HTTP/memory after traffic.
4. `./ezkey-tests/clean-start.sh --ha --with-java-melody` then enough traffic to see **two nodes**
   per application on the collector.

## Validation results (2026-08-24)

- **Build:** `./scripts/build.sh` succeeded.
- **Baseline:** collector at `http://localhost:8088` listed `admin-api`, `auth-api`,
  `integration-api` (green). After `mvn test -pl ezkey-tests`, HTTP stats included bind/verify/
  pending/respond (auth-api), auth-attempts POST (integration-api), and Admin API operator paths.
  Management-port reports `GET /actuator/monitoring` returned 200; business-port `/monitoring`
  stayed closed.
- **HA:** collector scrapes both replica URLs per API (`usedMemory` graphs = 2; System information
  shows two `Host:` lines). Functional tests (`AdminTokenCreationTest` and related) generated
  further HTTP volume through HAProxy.
- **Fix found in validation:** switching baseline → HA left `ezkey-javamelody-collector` on host
  port 8088. `clean-start.sh` / `start.sh` / `start-ha.sh` now tear down the sibling collector.

## Links

- Idea: `I-2026-08-24-java-melody-collector`
- Vision: `V-2026-0009`
- Retrofit: `R-2026-0002`
