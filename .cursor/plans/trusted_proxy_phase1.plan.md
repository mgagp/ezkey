---
name: Trusted Proxy Phase 1
overview: Phase 1 of trusted proxy support — property-based CIDR list, shared ClientIpResolver in core, wiring in auth-api / admin-api / m2m-api for rate limiting and audit, plus test strategy and optional Docker reverse-proxy for realistic E2E testing.
todos:
  - id: phase1-resolver
    content: "Add ClientIpResolver in ezkey-core (request + trustedProxyCidrs → client IP; empty list = remoteAddr only; reuse ipaddress lib for CIDR)"
    status: pending
  - id: phase1-property
    content: "Add ezkey.trusted-proxies property and binding in auth-api, admin-api, m2m-api"
    status: pending
  - id: phase1-wire-auth
    content: "Wire resolver in auth-api RateLimitFilter and AuditHelper; ClientContext.from(request, trustedProxies) in core"
    status: pending
  - id: phase1-wire-admin-m2m
    content: "Wire resolver in admin-api AdminRateLimitFilter and ApiKeyAuthenticationFilter; m2m-api ApiKeyAuthenticationFilter"
    status: pending
  - id: phase1-tests-unit
    content: "Unit tests: ClientIpResolver (empty list, in-CIDR + XFF, not-in-CIDR, CF-Connecting-IP); RateLimitFilter/AdminRateLimitFilter with trusted list and forged X-Forwarded-For"
    status: pending
  - id: phase1-tests-func
    content: "Integration/functional tests: auth-api rate limit key with mocked remoteAddr + headers; optional ezkey-tests IP spoofing does not bypass rate limit"
    status: pending
  - id: phase1-docker-option
    content: "Optional: docker-compose.with-proxy.yml (Caddy) + alternative ports 19080/18080/17080 + start.sh --with-proxy and EZKEY_TRUSTED_PROXIES for E2E"
    status: pending
isProject: false
---

# Trusted Proxy — Phase 1 (Implementation Plan)

## Goal

Implement configurable trusted proxy CIDR list so that client IP extraction (for rate limiting and audit) only trusts `X-Forwarded-For` / `X-Real-IP` when the direct connection comes from a configured proxy. Phase 1 is property-only (no DB, no Admin UI): one property `ezkey.trusted-proxies` and a shared resolver used across all APIs.

---

## Phase 1 — Backend (summary)

- **ezkey-core:** `ClientIpResolver.resolve(request, trustedProxyCidrs)`. Rule: if list null/empty → use only `request.getRemoteAddr()`; if non-empty → use headers only when `remoteAddr` is contained in any CIDR (reuse `com.github.seancfoley.ipaddress` as in ApiKeyService). Priority: CF-Connecting-IP, then X-Forwarded-For (first IP), then X-Real-IP, then remoteAddr.
- **Configuration:** `ezkey.trusted-proxies` (list of CIDR strings) in each API module (auth, admin, m2m). Optional startup validation (log warning, ignore invalid entries).
- **Wire resolver everywhere client IP is derived:** RateLimitFilter and AuditHelper (auth-api), ClientContext (core callers pass list), AdminRateLimitFilter and ApiKeyAuthenticationFilter (admin-api), ApiKeyAuthenticationFilter (m2m-api).

---

## Phase 1 — Test Strategy

### Unit tests

**ClientIpResolver (ezkey-core)**

- Empty or null trusted list → always return `request.getRemoteAddr()`, regardless of headers.
- `remoteAddr` contained in one of the CIDRs + `X-Forwarded-For` set → return first IP from X-Forwarded-For.
- `remoteAddr` contained in one of the CIDRs + `CF-Connecting-IP` set → return CF-Connecting-IP.
- `remoteAddr` not in any CIDR + `X-Forwarded-For` set → return `remoteAddr` (headers ignored; spoofing has no effect).
- Invalid CIDR in list: either validate at load and ignore invalid entries (with log), or document that list is pre-validated; add at least one test that "IP in CIDR" works with the existing ipaddress library.

**RateLimitFilter (ezkey-auth-api)**

- With trusted list empty: request with `X-Forwarded-For: 1.2.3.4` → bucket key must be `remoteAddr`, not 1.2.3.4 (no trust to headers).
- With trusted list containing `remoteAddr`: same request → bucket key must be the client IP from the header (e.g. 1.2.3.4 or first XFF).
- Reuse style of existing tests: [RateLimitFilterPendingTest](ezkey-auth-api/src/test/java/org/ezkey/auth/config/RateLimitFilterPendingTest.java), [RateLimitFilterRespondTest](ezkey-auth-api/src/test/java/org/ezkey/auth/config/RateLimitFilterRespondTest.java) — `MockHttpServletRequest`, `setRemoteAddr(...)`, `addHeader("X-Forwarded-For", ...)`, then assert 200 vs 429 and that the same bucket is used when spoofing without trusted proxy.

**AdminRateLimitFilter (ezkey-admin-api)**

- Same logic: trusted list empty → header ignored, key = remoteAddr; trusted list contains remoteAddr → key from header. Mock requests and assert behaviour.

### Integration / functional tests

- **Auth-api (MockMvc or TestRestTemplate):** Filter chain with RateLimitFilter and a small trusted list (e.g. CIDR containing 127.0.0.1). Send requests with `X-Forwarded-For: 2.3.4.5`; verify rate limit applies per resolved client IP (either 127.0.0.1 or 2.3.4.5 depending on list). Verify that when connection is not from trusted proxy, changing only the header does not change the bucket.
- **ezkey-tests (optional):** One test "IP spoofing does not bypass rate limit": call Admin or Auth API directly (no proxy) with forged `X-Forwarded-For`; assert that rate limiting still applies (e.g. same bucket as without header, or 429 after expected count). Uses [DockerStackConfig](ezkey-tests/src/test/java/org/ezkey/tests/config/DockerStackConfig.java) default URLs (9080, 8080).

### E2E with Docker proxy (optional)

- **Condition:** Stack started with override that adds Caddy (e.g. `docker-compose.with-proxy.yml`) and `EZKEY_TRUSTED_PROXIES` set to the proxy’s network (e.g. Docker subnet or Caddy container IP).
- **Scenarios:**
  - Requests to **proxy ports** (e.g. 19080, 18080): proxy sets X-Forwarded-For (host IP). Verify rate limiting and audit use that client IP.
  - Requests **directly** to 9080/8080 with forged X-Forwarded-For: rate limit must ignore header (direct connection not in trusted list) and use remoteAddr.
- **Implementation:** Either extend DockerStackConfig / env (e.g. `EZKEY_TEST_VIA_PROXY=true`) to point to proxy ports when running "trusted proxy E2E", or a tagged test (e.g. `@Tag("trusted-proxy-e2e")`) run only when stack is started with proxy override and appropriate env. Not required for Phase 1 delivery; unit + integration tests are sufficient to validate the logic.

---

## Phase 1 — Docker Option (Reverse Proxy for Realistic Testing)

### Recommendation

- **Do not add the reverse proxy to the base stack.** Keep default "clean start" (e.g. `./docker/start.sh` with no flags) unchanged: same ports (9080, 8080, 7080) and no proxy.
- **Provide an optional path** for realistic "host → proxy → APIs" testing via a **compose override** and **alternative ports**.

### Design

- **Override file:** e.g. `docker/docker-compose.with-proxy.yml` (or similar name).
- **Reverse proxy:** **Caddy** (Apache 2.0, single binary, simple Caddyfile; already used in the project for admin-ui). Avoids Nginx (F5); lighter and simpler than Apache HTTPD for this use case.
- **Ports:**
  - Base stack unchanged: Admin 9080, Auth 8080, M2M 7080 (direct to containers).
  - Override adds Caddy and exposes **alternative** host ports, e.g. Admin 19080 → Caddy → admin-api:9080, Auth 18080 → Caddy → auth-api:8080, M2M 17080 → Caddy → m2m-api:7080. Caddy sets `X-Forwarded-For` (and optionally `X-Real-IP`) to the client IP (host, in dev).
- **Activation:** Script flag, e.g. `./docker/start.sh --with-proxy`, which:
  - Applies the override: `-f docker-compose.yml -f docker-compose.with-proxy.yml`.
  - Passes `EZKEY_TRUSTED_PROXIES` to admin-api, auth-api, m2m-api with the proxy’s network (e.g. Docker subnet such as `172.20.0.0/16` or the Caddy service IP range) so that only this proxy is trusted.
- **Result:** Default start has no proxy; with `--with-proxy`, the stack also exposes 19080, 18080, 17080 via Caddy, and the apps trust that proxy for forwarded headers. Functional tests can then target either direct ports (default) or proxy ports (when E2E trusted-proxy tests are desired).

### Caddyfile (minimal)

- Listen on host-mapped ports (e.g. 19080, 18080, 17080 inside the container).
- For each: `reverse_proxy admin-api:9080` (or auth-api:8080, m2m-api:7080) with `header_up X-Forwarded-For {remote_host}` (or equivalent) so the backend receives the real client IP. Caddy’s default behaviour for forwarded headers can be used; document the exact header set in docker README.

### Documentation

- In `docker/README.md` (or equivalent): short section "Optional: Testing behind a reverse proxy" describing `--with-proxy`, the alternative ports, and that `EZKEY_TRUSTED_PROXIES` must match the proxy’s network so that rate limiting and audit use the real client IP when calling via 19080/18080/17080.

---

## Implementation Order

1. Core: `ClientIpResolver` + (if needed) `ClientContext.from(request, trustedProxies)` using resolver.
2. Property and binding in auth-api, admin-api, m2m-api.
3. Wire resolver in all filters and audit helpers (auth, admin, m2m).
4. Unit tests (ClientIpResolver, RateLimitFilter, AdminRateLimitFilter).
5. Integration tests (auth-api rate limit + trusted list; optional ezkey-tests spoofing test).
6. Optional: `docker-compose.with-proxy.yml`, Caddy service, `start.sh --with-proxy`, env `EZKEY_TRUSTED_PROXIES`, and short doc.

Phase 1 is complete when the resolver is in place, all APIs use it for client IP, unit and integration tests pass, and (if implemented) the Docker proxy option works for manual or E2E testing.
