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

It does **not** track ordinary application libraries. Crypto and auth library majors on npm or
Maven (for example `js-sha256`, Boot-managed Spring Security, `nimbus-jose-jwt`) stay under the
`dependabot-curated` T4 rule: human review plus security-owner opinion. Do not add those to
`registry.yaml`.

**Exception:** libraries whose **majors Dependabot ignores** because they sit in the Gradle
ecosystem ignore set (#712) — today **Conscrypt** and **AndroidX Biometric** — stay in this
register so the line is not forgotten.

Also out of scope here: Playwright test images, leftover `busybox:latest` debug images (see
`document-hygiene-curated`), and product decisions such as Android `minSdk`.

## Two paths by risk

| Path | When | What to do |
|------|------|------------|
| **Light path** | Line change that is still a **semver minor** (example: Maven 3.9 → 3.10, Spring Boot 4.1 → 4.2) | Update the registry entry (`current`, `sources`, `eol`, `last_review`) **in the same PR**. Put the test plan (family floor below + free part) in the **PR body**. |
| **Dedicated issue (heavy)** | Semver **major**, **data migration** (PostgreSQL), or **React Native** line | Open a GitHub issue with the test plan **before** the upgrade PR (labels per `github-issue-labels`). When `security: true`, Christophe's written opinion goes in that issue; link it from `notes`. |

**Light path + `security: true`** (example: Alpine bootstrap-init, Spring Boot 4.1 → 4.2): open a
**short opinion-only** issue linked to the **existing** PR (Dependabot or human). Collect
Christophe's written opinion there. The test plan stays in the PR body — do **not** invent a
pre-PR issue-with-test-plan when the PR already exists.

**Line change Dependabot did not open** (example: Kotlin pin, Rocky `ARG ROCKY_IMAGE`): if it is
a semver major or otherwise heavy-path, use the dedicated-issue path; otherwise use the light
path via a normal PR that updates the registry (same as Dependabot light path).

**Never** ship a major upgrade autonomously. Marc decides keep / plan / accept-risk.

## Target rule

The **target** for a line is the **latest LTS** that has been in LTS status for **at least
3 months**. For products without an LTS label, use the latest stable line released at least
3 months ago.

Declared minimum versions (for example Python `python_requires >= …`) are a **product** decision
and sit outside this automatic target rule — they still appear in the registry so they are not
forgotten.

`target` may also be `follow:<id>` (for example `follow:react-native`) when the component must
move with another register entry rather than under the LTS-age rule alone.

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
| **Christophe** | Written opinion **mandatory** on every `security: true` line (recorded in the dedicated or opinion-only issue). Sets `accept_until` when accepting risk. |
| **Patrick** | Validates build / CI / mobile test plans. |
| **Marc** | Decides: keep, plan, or accept-risk. |

## Registry

Machine-readable source of truth:

[`registry.yaml`](registry.yaml)

### Field reference

| Field | Rule |
|-------|------|
| `id`, `component`, `owner` | Required. |
| `eol_slug` | endoflife.date product id, or `null` for manual follow. |
| `current` | The **support line** (endoflife.date cycle, or semver major/minor line when not on EOL) — homogeneous across entries (e.g. `24`, `4.1`, `19`, `9`), **not** a patch pin. |
| `sources` | List of `{file, expect}`. `expect` is a **line-level** substring (`grep -F`); do **not** pin patch digits so routine Dependabot patches do not false-fail drift. |
| `eol` | Support end date last recorded (or estimate noted in `notes`). |
| `target` | Desired line per the target rule above, **or** `follow:<id>` when the entry tracks another register id. |
| `decision` | `keep` · `plan` · `follow:<id>` · `accept-risk`. |
| `security` | Boolean; required. |
| `warn_months` | Optional; default **6** (React Native uses **3**). |
| `accept_until` | Required when `accept-risk` **and** `security: true`; at most 6 months after `last_review`. |
| `floor_policy` | Optional. Declared-minimum policy id (CLI Python: `window-42m`). |
| `ci_matrix` | Optional. List of runtime versions exercised in CI for this entry. |
| `issue`, `last_review`, `notes` | `notes` links Christophe's opinion when present. |

## Python

The Ezkey CLI supports CPython lines released less than **42 months** ago, and always at least
the **two latest** released lines. Raise the floor only when a version ≥ the new floor is
installable as an official package on the latest Ubuntu LTS, Debian stable, and current RHEL.
Compute candidates from [endoflife.date/python](https://endoflife.date/python) release dates.
Floor today: **>=3.12** (next raise to 3.13 on 2027-04-02, then 3.14 on 2028-04-07). CI matrix =
`[floor, latest line released ≥ 3 months]` → **3.12** and **3.14**. Rationale: mainstream defaults
are already ≥3.12; `click` 8.5 / `requests` 2.34 already need ≥3.10 so the old `>=3.8` was false;
a 3-year SPEC 0 window would force 3.13 and exclude Ubuntu 24.04 / RHEL 10 defaults. The monthly
check (future PR) will flag: computed floor > `current`, CI latest lagging, and
`python_requires` / CI floor drift vs the registry.

## Test-plan floors (by family)

Every dedicated issue (or light-path PR body) starts from the floor for the family, then adds a
free part. Commands below are paths that exist in this repository.

### Base image / proxy

1. `./ezkey-tests/clean-start.sh` (add `--ha` when HAProxy is in scope).
2. Containers healthy; `bootstrap-init` exits without error.
3. Functional suite: `mvn test -pl ezkey-tests`.

HAProxy additionally: `./docker/manage-ha.sh status`, then **Test C: Failover** in
[`docker/README-HA.md`](../../../../docker/README-HA.md) (stop one instance; traffic fails over).

**PAM / Rocky:** when the Rocky/PAM image line moves, run
`./ezkey-pam/scripts/provision.sh` then `./ezkey-pam/scripts/up.sh` (`up.sh` requires
provisioning first; both scripts exist) and record the result in the PR/issue. **Lightsail
compose** (`experimental-hybrid/lightsail/docker-compose.yml`) is in the registry `sources` for
drift only; a full Lightsail deploy rehearsal is **out of scope** for the local floor unless the
operator asks for it.

### JVM runtime

1. `./scripts/build.sh` (Spotless, Checkstyle, install, then `mvn test -pl '!ezkey-tests'`).
2. `./ezkey-tests/clean-start.sh`.
3. `mvn test -pl ezkey-tests`.

### Node runtime (Admin UI)

1. `./ezkey-tests/clean-start.sh` (API stack the UI talks to).
2. `./ezkey-admin-ui/scripts/run-ui-tests-docker.sh` — builds the Admin UI image and runs
   Playwright against it on port **3090**.

### Database

Rehearse on a **copy**, not production data. There is **no** `pg_upgrade` script in the repo;
rehearsal is dump / restore.

1. **Before any fresh-volume step:** dump the database **and** protect secrets.
   - `docker exec ezkey-postgres pg_dump -U postgres ezkey_db > backup.sql` (see
     [`docker/README.md`](../../../../docker/README.md) backup section).
   - Keep the existing `ezkey_encryption-secrets` volume **or** back up the master key
     (`docker/README.md` § encryption backup ~540–548).
   - **Warning:** never run `docker compose down -v` (or equivalent volume wipe) before secrets
     are backed up — it deletes `ezkey_encryption-secrets`, and restored ciphertext becomes
     unreadable.
2. Start **postgres alone** on the **new** image tag with a **fresh** data volume; restore with
   `psql` (for example `docker exec -i … psql -U postgres ezkey_db < backup.sql`). Restore the
   master key into `ezkey_encryption-secrets` first if that volume was not retained.
3. `./docker/start.sh` — Flyway should then be a **no-op** on an already-migrated dump (do not
   expect a full replay).
4. `./scripts/db/verify-grants.sh --docker`.
5. `mvn test -pl ezkey-tests`.

The **`db-grants`** service uses the same Postgres image tag
(`docker/docker-compose.yml` ~64) and **must move together** with `postgres` on every line bump.

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

When implemented, the drift check must verify **all** occurrences of an `expect` in a source
file (examples today: HAProxy ×3 in `docker-compose.ha.yml`, Postgres ×2 per compose including
`db-grants`, Temurin `25` ×8 across `docker/Dockerfile` stages). The standing issue stays
idempotent via a hidden body marker `<!-- major-upgrades-review -->` plus an author filter
(`app/github-actions`), so each run edits one issue rather than opening duplicates.

## Related

- Hygiene index: [`../README.md`](../README.md)
- Sibling triage lane: [`../dependabot/`](../dependabot/) (`dependabot-curated`)
- Agent rule: root [`AGENTS.md`](../../../../AGENTS.md) § Dependabot curated → *Major upgrades*
- Skill note: [`.cursor/skills/dependabot-curated/SKILL.md`](../../../../.cursor/skills/dependabot-curated/SKILL.md)
