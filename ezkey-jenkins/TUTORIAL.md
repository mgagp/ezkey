# Tutorial — hello-world Jenkins plugin (M1)

Short, reproducible path from empty plugin to installed `.hpi` in the M0
Docker Jenkins. Lab only (#972). No Ezkey auth yet.

Official canon (verify versions when you re-run):

- [Prepare](https://www.jenkins.io/doc/developer/tutorial/prepare/)
- [Create a plugin](https://www.jenkins.io/doc/developer/tutorial/create/)
- [Build and run](https://www.jenkins.io/doc/developer/tutorial/run/)

## Conventions confirmed (2026-10)

| Topic | Finding |
|-------|---------|
| Language | **Java** (not Groovy) for the plugin implementation. Jelly/HTML for UI snippets. |
| Build | **Maven** with parent `org.jenkins-ci.plugins:plugin` and `<packaging>hpi</packaging>`. |
| Scaffold | Official archetype `io.jenkins.archetypes:hello-world-plugin` (lab used **1.37**). |
| Local run | `mvn hpi:run` starts a throwaway controller (default `http://localhost:8080/jenkins/`, home under `work/`). Optional; this lab installs into the M0 container instead. |
| Package | Maven HPI plugin produces **`*.hpi`** (Jenkins Plugin Interface / “Hudson Plugin”). |
| JDK | Tutorial recommends **JDK 21**. The M0 image is `jenkins/jenkins:lts-jdk21`. Do not build the plugin with the repo’s JDK 25 by default. |
| Maven | Need **Maven 3.9+**. Distro Maven 3.8.x fails with `Unknown packaging: hpi`. |
| Settings | Lab file `maven-settings.xml` adds `repo.jenkins-ci.org` + `org.jenkins-ci.tools` plugin group. |

## Prerequisites

- Docker (M0 stack already documented in `README.md`)
- JDK 21 (`JAVA_HOME`)
- Maven 3.9+ on `PATH`, or `EZKEY_JENKINS_MVN=/path/to/mvn`

## 1. Start M0 Jenkins

```bash
cd ezkey-jenkins
./scripts/up.sh
./scripts/smoke.sh   # optional: m0-ls still green
```

UI: http://localhost:28080/

## 2. (Reference) How this plugin was generated

Already present as `ezkey-lab-hello/`. To regenerate elsewhere:

```bash
export JAVA_HOME=…/jdk-21
mvn -s maven-settings.xml -U archetype:generate \
  -DarchetypeGroupId=io.jenkins.archetypes \
  -DarchetypeArtifactId=hello-world-plugin \
  -DarchetypeVersion=1.37 \
  -DartifactId=ezkey-lab-hello \
  -Dpackage=org.ezkey.jenkins.hello \
  -DinteractiveMode=false
```

The archetype may pin `groupId` to `io.jenkins.plugins`; that is fine for a lab hello-world.

## 3. Build the `.hpi`

```bash
./scripts/build-plugin.sh
# → ezkey-lab-hello/target/ezkey-lab-hello.hpi
```

## 4. Optional: `hpi:run` (local throwaway Jenkins)

```bash
cd ezkey-lab-hello
mvn -s ../maven-settings.xml hpi:run
# open http://localhost:8080/jenkins/  (Ctrl-C to stop)
```

Prefer the M0 container for the #972 gate so Marc has one stable UI.

## 5. Install into the M0 container

```bash
./scripts/install-plugin.sh
```

This copies the `.hpi` into `/var/jenkins_home/plugins/` and restarts the
container. Confirm under **Manage Jenkins → Plugins → Installed** (search
`ezkey-lab-hello`), or:

http://localhost:28080/manage/pluginManager/installed

## 6. Demo job that uses the build step

```bash
./scripts/demo-hello-job.sh
```

Creates freestyle job `m1-hello` with build step **Ezkey lab: Say hello world**,
runs it, expects console line `Hello, Ezkey!`.

http://localhost:28080/job/m1-hello/

## What “working” means for M1

1. `.hpi` builds cleanly.
2. Plugin appears as installed in the M0 UI.
3. A job can add **Ezkey lab: Say hello world** and the build prints a greeting.

## Lab fix on top of the archetype

The stock `doCheckName` used `value.matches(".*[éáàç].*")`. CodeQL flagged that as
a high ReDoS risk on user input. The lab replaces it with a simple character scan
(same UX warning). Keep that change if you regenerate from the archetype.

## 7. M2 — mock SecurityRealm + break-glass

```bash
./scripts/build-auth-plugin.sh
./scripts/configure-mock-auth.sh
```

Effects:

1. Unsecured realm is replaced (login required).
2. Security realm = **Ezkey Lab Auth (experimental)** with Integration API base URL,
   key, and secret (`hudson.util.Secret`, concealed in UI, never logged).
3. Break-glass `admin` / `ezkey-lab-admin` always works without Ezkey (`/login`).
4. Ezkey MFA users use [`/ezkey-lab-login/`](http://localhost:28080/ezkey-lab-login/) —
   type username + `ok` to confirm **before** create; then match the challenge on
   Demo Device and Approve or Deny.

Prior-art and Integration API ↔ SDK model: [`DESIGN.md`](DESIGN.md).

Smoke uses break-glass basic auth (`./scripts/smoke.sh`).

## 8. M3 — SDK fit (design)

Recorded in [`DESIGN.md`](DESIGN.md): **Java SDK adequate as-is** for Jenkins as an
Integration API consumer. Auth API / device path is out of scope for this plugin.
Credentials-plugin secret migration deferred (not cheap).

## 9. M4 — live Integration round-trip

1. Ezkey stack up (Integration API `:7080`) + Demo Device.
2. Provision a dedicated lab integration (M5 script below) and `source .ezkey/m5-integration.env`.
3. `./scripts/build-auth-plugin.sh && ./scripts/configure-mock-auth.sh`
4. Open `/ezkey-lab-login/` → confirm `ok` → note challenge → Approve (or Deny) on Demo Device.
5. Never log the challenge or Integration secret.

## 10. M5 — dedicated seed + clear login states

1. From a clean-start Ezkey stack + Demo Device + Jenkins lab up:
   ```bash
   cd ezkey-jenkins
   ./scripts/m5-provision-lab-integration.sh   # creates jenkins-lab integration + labuser enrollment
   source .ezkey/m5-integration.env
   ./scripts/build-auth-plugin.sh && ./scripts/configure-mock-auth.sh
   ./scripts/m4-live-demo.sh approve           # or deny
   ```
2. Admin login during provision uses system enrollment `#1` (`admin.docker`) only for a token —
   Jenkins MFA uses the new enrollment id written to the env file (not enrollment `#1`).
3. UI states on `/ezkey-lab-login/`: **confirm** → **challenge** + **waiting** → **approved** /
   **denied** / **expired** / **cancelled** (`data-ezkey-lab-state` on each page).
4. Credentials-plugin migration remains deferred (not low-risk for this lab — see `DESIGN.md`).

## Out of scope (later milestones)

Credentials-plugin migration (deferred), community/ngrok endpoints, publishing to the
Jenkins update center.
