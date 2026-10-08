# Major upgrades — platform register

Punctual hygiene lane for **platform, runtime, and base-image line changes** that Dependabot
intentionally does not open as majors (Docker tags, Compose images, Gradle ecosystem — see
Dependabot major-ignore posture after #712). Majors must be **decided**, not suffered, and must
not be forgotten.

Inspired by SOC 2–style **traceable registers**: a versioned list of what we run, when we last
reviewed it, and what we decided. Proportionate — nothing in this lane blocks CI or merges.
Readable view of the register is the (upcoming) monthly review issue; there is no committed
dashboard and no generated index.

## What counts as a major upgrade

A **major upgrade** is:

1. a **support-line change** as defined by [endoflife.date](https://endoflife.date) (for example
   Node 24 → 26, PostgreSQL 18 → 19, HAProxy 2.9 → 3.4), **or**
2. a **semver major** when the component is not on endoflife.date (fallback for Android Gradle
   Plugin, TypeScript, Vite, and similar).

Patch and minor bumps that Dependabot already opens stay under the normal
[`dependabot-curated`](../dependabot/) pass. This lane is for **line** decisions.

## Scope — platforms, not libraries

The registry tracks **platforms, runtimes, and images** (JDK, Node, PostgreSQL, HAProxy, Caddy,
base images, Spring Boot line, React Native line, Gradle / AGP / Kotlin, TypeScript, Vite, …).

It does **not** track application libraries. Crypto and auth library majors on npm or Maven
(for example `js-sha256`, Boot-managed Spring Security, `nimbus-jose-jwt`) stay under the
`dependabot-curated` T4 rule: human review plus security-owner opinion. Do not add those to
`registry.yaml`.

Also out of scope here: Playwright test images, leftover `busybox:latest` debug images (see
`document-hygiene-curated`), and product decisions such as Android `minSdk`.

## Two paths by risk

| Path | When | What to do |
|------|------|------------|
| **Light path** | Line change that is still a **semver minor**, opened by Dependabot (example: Maven 3.9 → 3.10) | Update the registry entry (`current`, `sources`, `eol`, `last_review`) **in the same PR**. Put the test plan (family floor below + free part) in the **PR body**. |
| **Dedicated issue** | Semver **major**, **data migration** (PostgreSQL), **React Native** line, or any `security: true` entry | Open a GitHub issue with the test plan **before** the upgrade PR (labels per `github-issue-labels`). The security owner's **written opinion** goes in that issue; link it from the registry `notes` field. |

If a change would otherwise be light-path but touches a `security: true` line (example: Alpine
bootstrap-init, Spring Boot 4.1 → 4.2), open a **short** issue whose only job is to collect the
security reviewer's written opinion and point at the PR. The test plan stays in the PR body.

**Never** ship a major upgrade autonomously. Marc decides keep / plan / accept-risk.

## Target rule

The **target** for a line is the **latest LTS** that has been in LTS status for **at least
3 months**. For products without an LTS label, use the latest stable line released at least
3 months ago.

Declared minimum versions (for example Python `python_requires >= …`) are a **product** decision
and sit outside this automatic target rule — they still appear in the registry so they are not
forgotten.

## Signal rules (for the upcoming monthly check)

Default warning window: **6 months** before end of support (`warn_months`, overridable per entry;
React Native uses `3`).

| Signal | `keep` / `plan` | `follow:` / `accept-risk` |
|--------|-----------------|---------------------------|
| Support ended, or ends sooner than `warn_months` | warn | — (no EOL proximity flag unless the recorded `eol` **date** itself changes) |
| endoflife.date `eol` date differs from the value stored in the registry | warn | warn |
| `last_review` older than **100 days** | warn | warn |
| `accept_until` missing, expired, or more than 6 months after `last_review` | — | fail-closed signal when `security: true` |
| Declared `expect` string missing from a listed source file | drift | drift |

For `accept-risk` on a `security: true` entry, `accept_until` is **mandatory** and must be at most
**6 months** after `last_review`. The security owner sets that date.

## Roles

| Who | Role |
|-----|------|
| **Fred** | Prepares the **quarterly review** inside the full Monday `dependabot-curated` pass (January, April, July, October); keeps the registry honest; opens dedicated issues. |
| **Christophe** | Written opinion **mandatory** on every `security: true` line (recorded in the dedicated issue). Sets `accept_until` when accepting risk. |
| **Patrick** | Validates build / CI / mobile test plans. |
| **Marc** | Decides: keep, plan, or accept-risk. |

## Registry

Machine-readable source of truth:

[`registry.yaml`](registry.yaml)

Fields (see header comment in the file): `id`, `component`, `owner`, `eol_slug`, `current`,
`sources` (list of `{file, expect}`), `eol`, `target`, `decision`, `security`, optional
`warn_months` / `accept_until`, plus `issue`, `last_review`, `notes`.

## Test-plan floors (by family)

Every dedicated issue (or light-path PR body) starts from the floor for the family, then adds a
free part. Commands below are paths that exist in this repository.

### Base image / proxy

1. `./ezkey-tests/clean-start.sh` (add `--ha` when HAProxy is in scope).
2. Containers healthy; `bootstrap-init` exits without error.
3. Functional suite: `mvn test -pl ezkey-tests`.

HAProxy additionally: `./docker/manage-ha.sh status`, then **Test C: Failover** in
[`docker/README-HA.md`](../../../../docker/README-HA.md) (stop one instance; traffic fails over).

### JVM runtime

1. `./scripts/build.sh` (Spotless, Checkstyle, install, then `mvn test -pl '!ezkey-tests'`).
2. `./ezkey-tests/clean-start.sh`.
3. `mvn test -pl ezkey-tests`.

### Node runtime (Admin UI)

1. In `ezkey-admin-ui/`: `npm ci && npm run generate:api && npm run build && npm test`.
2. `./ezkey-admin-ui/scripts/run-ui-tests.sh` (Playwright).
3. Build the image from `ezkey-admin-ui/docker/Dockerfile`.

### Database

Rehearse on a **copy**, not production data.

1. `docker exec ezkey-postgres pg_dump -U postgres ezkey_db > backup.sql` (see
   [`docker/README.md`](../../../../docker/README.md) backup section).
2. Restore into the new major on a fresh volume.
3. `./docker/start.sh` (the `migration` container replays Flyway).
4. `mvn test -pl ezkey-tests`.

There is **no** `pg_upgrade` script in the repo; rehearsal is dump / restore.

### React Native line

1. In `ezkey_mobile/`: `yarn validate`.
2. `yarn android:bundle:release` (`bundleRelease` + 16 KB gate).
3. `ezkey_mobile/scripts/build-install-release-clean.sh`.
4. **Physical Pixel smoke by Justin**, recorded on a tracking issue (pattern: #627).

## Quarterly review

On the full Monday `dependabot-curated` pass in January, April, July, and October, Fred walks the
registry: refresh `eol` / `last_review`, open or update plan issues, and surface `accept-risk`
expiry to Christophe and Marc. Details stay in the Dependabot campaign note for that Monday.

## Upcoming — monthly endoflife.date check

A **non-blocking** monthly GitHub Actions workflow (compare `registry.yaml` to endoflife.date,
verify each `expect` still appears in its source files, update **one** standing issue) is
**planned** and is **not present yet**. It will not join `ci-gate`, will not open PRs, and will
not commit into the tree. See follow-up PR 3 in the major-upgrades rollout.

## Related

- Hygiene index: [`../README.md`](../README.md)
- Sibling triage lane: [`../dependabot/`](../dependabot/) (`dependabot-curated`)
- Agent rule: root [`AGENTS.md`](../../../../AGENTS.md) § Dependabot curated → *Major upgrades*
- Skill note: [`.cursor/skills/dependabot-curated/SKILL.md`](../../../../.cursor/skills/dependabot-curated/SKILL.md)
