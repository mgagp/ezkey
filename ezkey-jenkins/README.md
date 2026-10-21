# Ezkey Jenkins lab

**Experimental.** Personal-lab R&D for a Jenkins authentication plugin that will
use the Ezkey SDK. Same spirit as `ezkey-pam/`: learn by building; no production
claim; no WebAuthn/FIDO2 equivalence. Keep Jenkins facts in this folder; do not
fold them into `docs/` or `docker/README.md`.

Tracking: [GitHub #972](https://github.com/mgagp/ezkey/issues/972).

- Status: [`STATUS.md`](STATUS.md)
- Tutorial: [`TUTORIAL.md`](TUTORIAL.md)
- Design / prior-art / challenge rules: [`DESIGN.md`](DESIGN.md)

## What is here

| Piece | Role |
|-------|------|
| `docker-compose.yml` + `scripts/up.sh` | M0: stock Jenkins LTS |
| `ref/jobs/m0-ls/` | M0: freestyle `ls` smoke job |
| `ezkey-lab-hello/` | M1: hello-world build step |
| `ezkey-lab-auth/` | M2: mock `SecurityRealm` (break-glass + auto-accept) |
| `scripts/configure-mock-auth.sh` | M2: install auth `.hpi` and apply realm |
| `maven-settings.xml` | Jenkins Maven repos for plugin builds |

## Prerequisites

- Docker Engine + Compose v2
- Host ports (defaults): `28080` (UI), `50000` (agent)
- Plugin builds: **JDK 21** and **Maven 3.9+**

## Quick start (M0 → M2)

```bash
cd ezkey-jenkins
./scripts/up.sh
./scripts/build-plugin.sh          # M1 hello .hpi (optional if already installed)
./scripts/install-plugin.sh
./scripts/build-auth-plugin.sh     # M2 auth .hpi
./scripts/configure-mock-auth.sh   # replaces unsecured realm; enables mock + break-glass
./scripts/smoke.sh                 # uses break-glass basic auth
```

- Login: [http://localhost:28080/login](http://localhost:28080/login)
- Break-glass: `admin` / `ezkey-lab-admin` (override with `EZKEY_JENKINS_ADMIN_*`)
- Mock user: any other username + any non-empty password (after configure)
- Security: [http://localhost:28080/manage/configureSecurity/](http://localhost:28080/manage/configureSecurity/)

If the named volume still has the old M0 unsecured init applied, `configure-mock-auth.sh`
is the source of truth (or `./scripts/down.sh --volumes` for a clean home).

## Lab notes

- **Port 28080:** avoids Auth `8080` and Caddy Auth proxy `18080`.
- **M2 secret:** `hudson.util.Secret` on the realm (never logged). Credentials plugin
  deferred — see `DESIGN.md`.
- **No live Ezkey HTTP** until M4. Challenge display / fail-closed / never-log rules
  for M3/M4 are recorded in `DESIGN.md` (PAM lesson).
- **Independent compose:** not on `ezkey-network`; no PAM coupling.

## Out of scope until later

Real Ezkey round-trip, SDK decision (M3), publishing, deploying, infrastructure.
