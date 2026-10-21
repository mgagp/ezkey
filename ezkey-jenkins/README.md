# Ezkey Jenkins lab

**Experimental.** Personal-lab R&D for a Jenkins authentication plugin that will
use the Ezkey SDK. Same spirit as `ezkey-pam/`: learn by building; no production
claim; no WebAuthn/FIDO2 equivalence. Keep Jenkins facts in this folder; do not
fold them into `docs/` or `docker/README.md`.

Tracking: [GitHub #972](https://github.com/mgagp/ezkey/issues/972).

Current milestone status: [`STATUS.md`](STATUS.md).

Plugin tutorial (M1): [`TUTORIAL.md`](TUTORIAL.md).

## What is here

| Piece | Role |
|-------|------|
| `docker-compose.yml` + `scripts/up.sh` | M0: stock Jenkins LTS |
| `ref/jobs/m0-ls/` | M0: freestyle `ls` smoke job |
| `ezkey-lab-hello/` | M1: official hello-world archetype plugin (Java + Maven → `.hpi`) |
| `scripts/build-plugin.sh` / `install-plugin.sh` / `demo-hello-job.sh` | M1: build, install into M0, demo job |
| `maven-settings.xml` | Jenkins repo + HPI plugin group for Maven |

## Prerequisites

- Docker Engine + Compose v2
- Free host ports (defaults): `28080` (UI), `50000` (agent; unused)
- For M1 plugin builds: **JDK 21** and **Maven 3.9+** (see `TUTORIAL.md`)

## Quick start (M0 + M1)

```bash
cd ezkey-jenkins
./scripts/up.sh
./scripts/smoke.sh
./scripts/build-plugin.sh
./scripts/install-plugin.sh
./scripts/demo-hello-job.sh
```

- Home: [http://localhost:28080/](http://localhost:28080/)
- Installed plugins: [http://localhost:28080/manage/pluginManager/installed](http://localhost:28080/manage/pluginManager/installed)
- M0 job: [http://localhost:28080/job/m0-ls/](http://localhost:28080/job/m0-ls/)
- M1 job: [http://localhost:28080/job/m1-hello/](http://localhost:28080/job/m1-hello/)

Stop (keeps the named volume):

```bash
./scripts/down.sh
```

Reset Jenkins home:

```bash
./scripts/down.sh --volumes
```

## Lab notes

- **Image:** `jenkins/jenkins:lts-jdk21`.
- **Port 28080:** avoids direct Auth `8080` and Caddy Auth proxy `18080`.
- **Unsecured realm:** lab-only; replace before M2 auth work.
- **Plugin packaging:** Java + Maven archetype → `.hpi` (not Groovy). `mvn hpi:run` works for throwaway local Jenkins; gate path installs into the M0 container.
- **Independent compose:** not on `ezkey-network`; no PAM or main-stack coupling.

## Out of scope (until later milestones)

Ezkey SDK / auth realm, credentials storage, community or ngrok round-trips,
publishing to the update center, deploying, infrastructure creation.
