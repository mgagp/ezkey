# Local stack — ports (direct vs Caddy)

This page frames the **two host-level ways** the same APIs are reachable after a typical `ezkey-tests/clean-start.sh` run: **direct published ports** (Spring Boot inside Docker) and **Caddy reverse proxy** ports on the host. Both are intentional; they do not replace each other.

For **what to test** on each URL (headers, Postman, `curl`), see **[admin-ui-security-validation.md](admin-ui-security-validation.md)**.

## Direct vs Caddy (host)


| Role                         | Direct (container published ports) | Via Caddy on host (`docker-compose.with-proxy.yml`)       |
| ---------------------------- | ---------------------------------- | --------------------------------------------------------- |
| Auth API                     | `8080`                             | `18080` → forwards to `auth-api:8080`                     |
| Admin API                    | `9080`                             | `19080` → forwards to `admin-api:9080`                    |
| Integration API              | `7080`                             | `17080` → forwards to `integration-api:7080`              |
| Crypto API                   | `9090`                             | **No Caddy hop in the current compose file** — use `9090` |
| Integration API (management) | `7081`                             | **Not exposed through this Caddy file** — use `7081`      |


**Default for day-to-day manual testing (Postman, curl, OpenAPI links):** use **direct** URLs (`8080`, `9080`, `7080`, …). Same semantics as the services; minimal moving parts.

**When to use Caddy ports (`19xxx` / `18xxx` / `17xxx`):** when you need to exercise the **reverse-proxy path**: baseline **response headers** on API JSON, `X-Forwarded-*`, `EZKEY_TRUSTED_PROXIES_CIDRS` / client IP behavior, or other **prod-like** behavior from the host.

**Clean start:** By default, `clean-start.sh` includes the Caddy overlay **in addition to** direct ports. You are not forced to use Caddy for every call. To run without the proxy stack: `./clean-start.sh --no-proxy`.

## Postman

Versioned environments under `postman/environments/`:


| Environment | Use |
|-------------|-----|
| `local` | Direct ports: `base_url` → `8080`, `base_url_admin_api` → `9080`, `base_url_integration_api` → `7080`, `base_url_crypto_api` → `9090`, `base_url_integration_api_mgmt` → `7081`. |
| `local (via Caddy proxy)` | Same variable names; Auth/Admin/Integration base URLs point at `18080` / `19080` / `17080`. Crypto and integration management stay on `9090` and `7081` (unchanged). |


Switch the active environment in Postman depending on whether you are testing the **direct** or **proxy** path.

## Demos and bootstrap (Docker network)

**Demo Device**, **Acme App Demo**, and similar containers should keep using **intra-compose** service names and **internal** ports (e.g. `auth-api:8080`, `admin-api:9080`). There is no `auth-api:18080` on the Docker network — `18080` is a **host** mapping for Caddy.

Pointing demos at `host.docker.internal:18080` by default would add fragility and rarely helps the nominal integration flow. **Default:** demos and bootstrap flows stay on **direct service URLs** inside Compose; use **Caddy host ports** from the **host** (Postman on your machine, browser to localhost) when you explicitly want the proxy path.

## Related docs

- **[admin-ui-security.md](admin-ui-security.md)** — Admin UI token handling, Caddy headers, clean-start proxy default.
- **[admin-ui-security-validation.md](admin-ui-security-validation.md)** — How to validate headers and token behavior (checklist).
- **[../docker/caddy/Caddyfile](../docker/caddy/Caddyfile)** — API proxy blocks and comments for host port mapping.

