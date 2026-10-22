# Ezkey Jenkins lab

**Experimental.** Personal-lab R&D for a Jenkins authentication plugin that
uses the Ezkey **Java SDK** against the **Integration API**. Learn by building;
no production claim; no WebAuthn/FIDO2 equivalence. Keep Jenkins facts in this
folder; do not fold them into `docs/` or `docker/README.md`.

Tracking: [GitHub #972](https://github.com/mgagp/ezkey/issues/972).

- Status: [`STATUS.md`](STATUS.md)
- Tutorial: [`TUTORIAL.md`](TUTORIAL.md)
- Design / SDK decision / challenge matrix: [`DESIGN.md`](DESIGN.md)

## What is here

| Piece | Role |
|-------|------|
| `docker-compose.yml` + `scripts/up.sh` | Jenkins LTS (+ Integration API via stack network or `host.docker.internal`) |
| `ref/jobs/m0-ls/` | Freestyle `ls` smoke job |
| `ezkey-lab-hello/` | Hello-world build step |
| `ezkey-lab-auth/` | SecurityRealm + `/ezkey-lab-login/` MFA conversation |
| `scripts/configure-mock-auth.sh` | Install auth `.hpi` and apply realm |
| `scripts/m5-provision-lab-integration.sh` | Dedicated lab integration + enrollment + API key |
| `docker/Dockerfile.plugin-build` | JDK 21 Maven image + BuildKit `~/.m2` cache |
| `maven/ezkey-sdk-standalone.pom` | Packaging helper so the plugin can resolve `ezkey-sdk` |

## Prerequisites

- Docker Engine + Compose v2 (**only** — plugin builds use `maven:3.9-eclipse-temurin-21`)
- Host ports (defaults): `28080` (UI), `50000` (agent)
- For live MFA: Ezkey stack with Integration API + Demo Device

## Quick start

```bash
cd ezkey-jenkins
./scripts/up.sh
./scripts/build-auth-plugin.sh          # Docker / BuildKit — no local JDK
# Live keys (dedicated jenkins-lab integration — not system enrollment #1):
#   ./scripts/m5-provision-lab-integration.sh && source .ezkey/m5-integration.env
# Optional: EZKEY_JENKINS_FORCE_CHALLENGE=true|false (default false)
./scripts/configure-mock-auth.sh
./scripts/smoke.sh   # break-glass basic auth
```

- Break-glass: [http://localhost:28080/login](http://localhost:28080/login) — `admin` / `ezkey-lab-admin`
- Ezkey MFA: [http://localhost:28080/ezkey-lab-login/](http://localhost:28080/ezkey-lab-login/) —
  username → **Envoyer la demande Ezkey** → waiting → approved / denied / expired / cancelled

## Challenge matrix (see `DESIGN.md`)

| | Enrollment requires code | `forceChallenge` | Behaviour |
|---|--------------------------|------------------|-----------|
| **A** | no | false | No code shown |
| **B** | yes | false | Code displayed |
| **C** | — | — | Per-login opt-in not offered |
| **D** | — | true | Code forced; missing → deny |

Live A: `./scripts/m5-live-no-challenge.sh` (after m5 provision).

## Lab notes

- **Port 28080:** avoids Auth `8080` and Caddy Auth proxy `18080`.
- **Interaction model:** Auth API ↔ phone; Integration API ↔ SDK (this plugin).
- **Seed:** M5 creates integration `jenkins-lab` + enrollment for `labuser`.
- **Secret:** `hudson.util.Secret` (Credentials plugin deferred — `DESIGN.md`).
- **Temps:** scripts use `.ezkey/tmp` (`ezkey_lab_mktemp`) — no `TMPDIR`/`cygpath` workaround on Git Bash.
- **THIRD-PARTY.txt:** lab Docker build skips `aggregate-add-third-party` so the tracked root file is not rewritten.
- **Compose:** separate project; joins `ezkey_ezkey-network` as external for Integration DNS (`integration-api:7080`).

## Out of scope until later

Publishing, deploying, infrastructure; Credentials-plugin secret migration (deferred, not low-risk).
