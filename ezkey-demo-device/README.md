# Ezkey Demo Device

Spring Boot lab simulator of a mobile device for Auth API enrollment and MFA flows. It serves
integrated tests (agents, Playwright) and is the evaluator's phone on both ways to evaluate Ezkey
while the mobile app is not public: the clean-start stack (http://localhost:8083) and the community
instance `ezkey.online` (run it on its own, see [Running](#running)).

This module is the only source. The former standalone repository `mgagp/ezkey-demo-device` is
retired: its manual sync had drifted from the Auth API contract (#943).

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

| Mode | How | URL |
|------|-----|-----|
| Clean-start stack | started with the stack | http://localhost:8083/phone/ezkey |
| On its own, with the community instance | `docker compose -f ezkey-demo-device/docker-compose.yml up --build` from the repository root (Docker only, no JDK) | http://localhost:3080/phone/ezkey |
| Local JDK | `mvn test` then `mvn spring-boot:run` from this module | http://localhost:8083/phone/ezkey |

On its own, the Demo Device targets `https://auth-api.ezkey.online` by default. Set
`EZKEY_AUTH_API_URL` in the shell before `up` to target another Auth API. The first build compiles
the Java modules in Docker and takes a few minutes; later starts reuse the image. Enrollments stay
in the Docker volume `ezkey-demo-device_demo-device-data`.

Agent notes: [`AGENTS.md`](AGENTS.md).
