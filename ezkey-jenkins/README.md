# Ezkey Jenkins lab

**Experimental.** Personal-lab R&D for a Jenkins authentication plugin that will
use the Ezkey SDK. Same spirit as `ezkey-pam/`: learn by building; no production
claim; no WebAuthn/FIDO2 equivalence. Keep Jenkins facts in this folder; do not
fold them into `docs/` or `docker/README.md`.

Tracking: [GitHub #972](https://github.com/mgagp/ezkey/issues/972).

Current milestone status: [`STATUS.md`](STATUS.md).

## M0 gate (this folder today)

Stock **Jenkins LTS** in Docker, one freestyle job `m0-ls` that runs `ls` and
must be **SUCCESS**. No Ezkey plugin yet (that is M1+).

## Prerequisites

- Docker Engine + Compose v2
- Free host ports (defaults): `28080` (UI), `50000` (agent; unused in M0)

## Run

```bash
cd ezkey-jenkins
./scripts/up.sh
./scripts/smoke.sh
```

Open the UI: [http://localhost:28080/](http://localhost:28080/)  
Job: [http://localhost:28080/job/m0-ls/](http://localhost:28080/job/m0-ls/)

Stop (keeps the named volume):

```bash
./scripts/down.sh
```

Reset Jenkins home:

```bash
./scripts/down.sh --volumes
```

Override ports if needed:

```bash
export EZKEY_JENKINS_HTTP_PORT=28081
./scripts/up.sh
EZKEY_JENKINS_HTTP_PORT=28081 ./scripts/smoke.sh
```

## Lab notes (M0 decisions)

- **Image:** `jenkins/jenkins:lts-jdk21` — current LTS line; JDK 21 matches the
  ecosystem Jenkins documents for this LTS.
- **Port 28080:** avoids direct Auth `8080` and Caddy Auth proxy `18080` on a
  typical clean-start host. Not the community host; never commit real IPs.
- **Unsecured realm:** init groovy disables authentication so Marc can open the
  UI and smoke scripts can trigger builds without a wizard. Replace before M2
  auth work (break-glass admin path required then).
- **Seeded job:** `ref/jobs/m0-ls/config.xml` copied via Jenkins Docker `ref/`
  on first start — no Configuration-as-Code plugin for M0 (keep the surface
  stock).
- **Independent compose:** not on `ezkey-network`; no PAM or main-stack
  coupling.

## Out of scope for M0

Plugin archetype, `.hpi`, SDK wiring, community/ngrok auth, publishing,
deploying, infrastructure creation.
