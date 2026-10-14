# Ezkey Demo Device — Configuration

Lab-only phone simulator (port 8083). Properties bind from JAR `application.properties` /
`application-docker.properties` and environment variables (Spring relaxed binding).

## Auth API target (`ezkey.auth.api.url`)

| Property | Default (local) | Default (Docker) | Obligation | Description |
|----------|-----------------|------------------|------------|-------------|
| `ezkey.auth.api.url` | `http://localhost:8080` | `http://auth-api:8080` (`EZKEY_AUTH_API_URL`) | recommandé | Base URL used when the enrollment QR has no `authUrl`, and as the seed host for the default allowlist. |

## Host allowlist (`ezkey.demo-device.*`)

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.demo-device.allowed-auth-api-hosts` | _(empty)_ | optionnel | Comma-separated **hostnames** (no scheme/port) allowed as Auth API targets for QR `authUrl` and bind overrides. When empty, the effective allowlist is the host of `ezkey.auth.api.url` plus `localhost` and `127.0.0.1`. When set, **only** the listed hosts are allowed. |

Env override example: `EZKEY_DEMO_DEVICE_ALLOWED_AUTH_API_HOSTS=localhost,127.0.0.1,auth-api`.

### Semantics

- Manual bind (no QR URL) always calls `ezkey.auth.api.url` (not re-checked as an override).
- Non-blank QR / form `authApiBaseUrl` must pass HTTPS (or HTTP on loopback) **and** the host
  allowlist. Rejection uses a constant message; the submitted URL is not echoed.
- URLs with userinfo (`user@host` / `user:pass@host`) are rejected as invalid (constant message;
  normalization rebuilds from scheme + host + port + path only — never `URI.getAuthority()`).
- Trailing slashes on the path are stripped in linear time (no regex).

### Docker `http://auth-api:8080` vs HTTP-outside-loopback

Clean-start sets Demo Device `ezkey.auth.api.url` to `http://auth-api:8080`. That hostname is
seeded into the default allowlist, but **override** validation still rejects plain HTTP outside
loopback (`localhost` / `127.0.0.1` / `10.0.2.2`). That is intentional:

- The compose default is used on the **config path** (QR without `authUrl`, or manual ID + token).
- Enrollment QR `authUrl` comes from public `EZKEY_QR_AUTH_BASE_URL` (HTTPS or host-reachable),
  not from the intra-compose `auth-api` service name.
- A QR that embeds `http://auth-api:8080` would fail override validation; use HTTPS on an
  allowlisted host, or omit `authUrl` so the demo device uses its configured base URL.

See [`README.md`](README.md) § Limitations. Index: [`docs/configuration/README.md`](../docs/configuration/README.md).
