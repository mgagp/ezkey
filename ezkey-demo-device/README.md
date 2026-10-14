# Ezkey Demo Device

Spring Boot lab simulator of a mobile device for Auth API enrollment and MFA flows. Used with the
local Docker clean-start stack (default http://localhost:8083) and optionally against experimental
Auth API hosts.

## Limitations (read this first)

- **Demo / lab tool only.** Do **not** expose Demo Device on the public internet. It stores device
  private keys on disk as JSON under `data/enrollments/` and is intentionally a teaching surface.
- **Not a production mobile client.** Production posture belongs in `ezkey_mobile`.
- **Enrollment proof tokens and bind traffic** are sent only to Auth API hosts on the allowlist
  (`ezkey.demo-device.allowed-auth-api-hosts`). By default that is the host of `ezkey.auth.api.url`
  plus `localhost` and `127.0.0.1`. A QR `authUrl` or bind override outside the allowlist is
  rejected with a constant message (the rejected URL is not echoed to the UI or logs). URLs with
  userinfo are rejected the same way. Docker’s `http://auth-api:8080` is the configured default
  base URL (config path); override validation still requires HTTPS outside loopback — see
  `CONFIGURATION.md`.
- UI pages show a limitations banner so operators see this in ~3 seconds.

Decision trace: CodeQL alerts #9 / #10 (SSRF / ReDoS on Auth API URL handling), code-scanning
triage 2026-10-13, approved by Marc 2026-10-13 19:40.

## Configuration

See [`CONFIGURATION.md`](CONFIGURATION.md) for `ezkey.auth.api.url` and
`ezkey.demo-device.allowed-auth-api-hosts`.

## Running

| Mode | URL |
|------|-----|
| Clean-start stack (monorepo) | http://localhost:8083 |
| Standalone Docker (Exp1) | see standalone mirror `mgagp/ezkey-demo-device` |
| Local JDK | `mvn test` then `mvn spring-boot:run` from this module |

Agent notes: [`AGENTS.md`](AGENTS.md).
