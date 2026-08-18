# Local stack — ports (direct vs Caddy)

This page frames the **two host-level ways** the same APIs are reachable after a typical `ezkey-tests/clean-start.sh` run: **direct published ports** (Spring Boot inside Docker) and **Caddy reverse proxy** ports on the host. Both are intentional; they do not replace each other.

For **what to test** on each URL (headers, Bruno, `curl`), see **[admin-ui-security-validation.md](admin-ui-security-validation.md)**.

## Direct vs Caddy (host)


| Role                         | Direct (container published ports) | Via Caddy on host (`docker-compose.with-proxy.yml`)       |
| ---------------------------- | ---------------------------------- | --------------------------------------------------------- |
| Auth API                     | `8080`                             | `18080` → forwards to `auth-api:8080`                     |
| Admin API                    | `9080`                             | `19080` → forwards to `admin-api:9080`                    |
| Integration API              | `7080`                             | `17080` → forwards to `integration-api:7080`              |
| Crypto API                   | `9090`                             | **No Caddy hop in the current compose file** — use `9090` |
| Integration API (management) | `7081`                             | **Not exposed through this Caddy file** — use `7081`      |


**Default for day-to-day manual testing (Bruno, curl, OpenAPI links):** use **direct** URLs (`8080`, `9080`, `7080`, …). Same semantics as the services; minimal moving parts.

**When to use Caddy ports (`19xxx` / `18xxx` / `17xxx`):** when you need to exercise the **reverse-proxy path**: baseline **response headers** on API JSON, `X-Forwarded-*`, `EZKEY_TRUSTED_PROXIES_CIDRS` / client IP behavior, or other **prod-like** behavior from the host.

**Clean start:** By default, `clean-start.sh` includes the Caddy overlay **in addition to** direct ports. You are not forced to use Caddy for every call. To run without the proxy stack: `./clean-start.sh --no-proxy`.

## Bruno

Versioned environments under `bruno/environments/` (see [`bruno/README.md`](../bruno/README.md)):


| Environment | Use |
|-------------|-----|
| `local` | Direct ports: `base_url` → `8080`, `base_url_admin_api` → `9080`, `base_url_integration_api` → `7080`, `base_url_crypto_api` → `9090`, `base_url_integration_api_mgmt` → `7081`. |
| `local-via-caddy-proxy` | Same variable names; Auth/Admin/Integration base URLs point at `18080` / `19080` / `17080`. Crypto and integration management stay on `9090` and `7081` (unchanged). |


Switch the active environment in Bruno (or `./scripts/bruno-health.sh --env …`) depending on whether you are testing the **direct** or **proxy** path.

## Demos and bootstrap (Docker network)

**Demo Device**, **Acme App Demo**, and similar containers should keep using **intra-compose** service names and **internal** ports (e.g. `auth-api:8080`, `admin-api:9080`). There is no `auth-api:18080` on the Docker network — `18080` is a **host** mapping for Caddy.

Pointing demos at `host.docker.internal:18080` by default would add fragility and rarely helps the nominal integration flow. **Default:** demos and bootstrap flows stay on **direct service URLs** inside Compose; use **Caddy host ports** from the **host** (Bruno on your machine, browser to localhost) when you explicitly want the proxy path.

## HA mode (`docker-compose.ha.yml`)

Separate compose project (`ezkey-ha`): **2×** Admin API + **2×** Auth API behind HAProxy. Not the
same as the daily Caddy overlay — do not mix HA and single-stack assumptions.

| Role | Host port | Notes |
|------|-----------|--------|
| Admin API (via HAProxy) | `9080` | Round-robin to `admin-api-1` / `admin-api-2` |
| Auth API (via HAProxy) | `8080` | Round-robin to `auth-api-1` / `auth-api-2` |
| HAProxy Admin stats | `9081` | `http://localhost:9081/stats` |
| HAProxy Auth stats | `8085` | `http://localhost:8085/stats` |

Entrypoints: `./docker/start-ha.sh`, `./ezkey-tests/clean-start.sh --ha`. Full runbook:
[`docker/README-HA.md`](../docker/README-HA.md). Parity follow-up: `I-2026-0003`.

## Related docs

- **[admin-ui-security.md](admin-ui-security.md)** — Admin UI token handling, Caddy headers, clean-start proxy default.
- **[admin-ui-security-validation.md](admin-ui-security-validation.md)** — How to validate headers and token behavior (checklist).
- **[../docker/caddy/Caddyfile](../docker/caddy/Caddyfile)** — API proxy blocks and comments for host port mapping.
- **[../docker/README-HA.md](../docker/README-HA.md)** — HA stack architecture, ShedLock checks, HAProxy stats.

