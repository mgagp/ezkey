# Major-upgrades quarterly review — 2026-10-19

## Metadata

- **Date:** 2026-10-19
- **Quarter:** October 2026 (Jan / Apr / Jul / Oct cadence)
- **Approved by:** Marc (2026-10-19)
- **Operator / author:** Fred (cloud agent)
- **Scope:** Walk [`registry.yaml`](registry.yaml) against [endoflife.date](https://endoflife.date)
  for every registered platform / runtime / base image the repo actually uses. **No version
  bumps** in this PR — register honesty + dispositions only.
- **Locks from Marc:** JDK stays **25 LTS**; Docker / Android Gradle remain **HITL**; any T4 /
  semver-major / dedicated-issue path needs Marc's go before code moves.
- **Related:** short Oct walk in dependabot `2026-10-12-pass-1` (no majors driven); Alpine line
  already landed via `#762` (split of `#718`).

## Method

1. Drift-check every `sources[].expect` against the tree on `main`.
2. Query endoflife.date API for each `eol_slug` (and `temurin` / `maven` where the register was
   stale or incomplete).
3. Refresh `eol`, `last_review` (2026-10-19), and decision notes in `registry.yaml`.
4. Surface items with support ended or ending within **6 months** (default `warn_months`), plus
   RN's 3-month window.
5. Propose disposition; **do not** open upgrade PRs or bump pins.

## Drift check (sources vs register)

| Result | Detail |
|--------|--------|
| Fixed this review | `alpine-bootstrap-init` still listed `alpine:3.19` while tree is `alpine:3.24` after `#762` |
| OK | All other `expect` substrings present (37 matches after Alpine correction) |
| Slug fix | `java-jdk` / `jdk-javamelody` / `jdk-android-ci` `eol_slug`: `openjdk` → `temurin` (API 404) |
| Slug add | `maven-build-image` now has endoflife.date product `maven` (was `null`) |

## endoflife.date snapshot (2026-10-19)

| id | current | EOL / support (API) | target | prior decision | Proposed disposition |
|----|---------|---------------------|--------|----------------|----------------------|
| haproxy | 2.9 | **2025-03-21** (overdue) | 3.4 LTS (EOL 2031-04-01) | plan | **plan** — open dedicated issue when Marc funds; security opinion required |
| alpine-bootstrap-init | **3.24** (was 3.19) | 2028-06-01 | 3.24 | plan | **keep** — line bump done in `#762` |
| python-cli-min | 3.12 | 2028-10-31 | 3.13 (floor raise 2027-04-02) | keep | **keep** — floor policy `window-42m` unchanged; CI `[3.12, 3.14]` |
| kotlin-android | 2.2 | **2025-12-16** (overdue; latest line 2.4) | follow:react-native | follow | **follow** — no solo bump; Android Gradle HITL |
| eslint-mobile | 9 | **2026-08-06** (overdue; 10 current) | 10 when RN presets follow | accept-risk | **accept-risk** — tooling only; `security: false` |
| react-native | 0.87 | API `eol: false` (supported); prior estimate 2027-02-01 withdrawn | 0.88 / 0.89 | plan | **plan** — dedicated issue + Pixel smoke `#627` before line move; Marc go |
| node | 24 | EOL 2028-04-30; **Active LTS → Maintenance 2026-10-20** | 26 (LTS 2026-10-28 → eligible ≥2027-01-28) | keep | **keep** — stay on 24 LTS; revisit Node 26 after 3-month LTS age |
| java-jdk | 25 Temurin LTS | 2031-09-30 | 25 | keep | **keep** — Marc lock; never non-LTS 27 |
| jdk-javamelody | 21 | 2029-12-31 | 25 optional | keep | **keep** |
| jdk-android-ci | 17 | 2027-10-31 | follow:react-native | follow | **follow** — outside 6-month warn; move with RN toolchain |
| spring-boot | 4.1 | 2027-07-31 | 4.2 when ≥3 months old | keep | **keep** — patches via Java BOM pulse; line = light path + security opinion |
| postgresql | 18 | 2030-11-14 | 18 | keep | **keep** — any major = dedicated issue + dump/restore |
| caddy | 2 | no cycle EOL on 2.x (latest 2.11.7) | 2 | keep | **keep** |
| rocky-linux-pam | 10 | 2035-05-31 | 10 | keep | **keep** |
| maven-build-image | 3.9 | API `eol: false`; **3.10** released 2026-09-27 | 3.9 (3.10 eligible ~2026-12-27) | keep | **keep** — observe 3.10 until ≥3 months; then light path |
| react | 19 | supported (`eol: false`) | 19 | keep | **keep** |
| gradle | 9 | supported | follow:react-native | follow | **follow** — Android Gradle HITL (`#717` open) |
| android-gradle-plugin | 9 | not on EOL (semver) | follow:react-native | follow | **follow** — HITL with `#717` / RN |
| typescript | 6 | n/a | 7 | keep | **keep** — already `deferred:later-train` |
| vite | 8 | n/a | 8 | keep | **keep** |
| github-actions-runner | 24.04 | 2029-05-31 | 24.04 | keep | **keep** (Ubuntu 26.04 LTS exists; runners stay on 24.04 until deliberate move) |
| express-methodology-site | 5 | no cycle EOL | 5 | keep | **keep** |
| conscrypt-android | 2.7 | n/a (#712 ignore) | 2.7 | keep | **keep** — 16 KB gate (`#659`) |
| androidx-biometric | 1.1 | n/a (#712 ignore) | latest stable (à confirmer) | keep | **keep** — no line pressure this quarter; security opinion if major appears |

### Observed outside register (not added)

- **Android `compileSdk` / `targetSdk` 36** (`ezkey_mobile/android/build.gradle`) — product /
  platform API level, out of scope per lane README (`minSdk` / API product decisions).
- **Playwright test images** — explicitly out of scope.
- **Admin UI ESLint 10** — already on 10; register tracks **mobile** ESLint 9 only.

## Items within 6 months of EOL (or already past)

Review date **2026-10-19**; default window ends **~2027-04-19**. RN uses `warn_months: 3`.

| id | EOL | Status | Proposed disposition (Marc go before code) |
|----|-----|--------|--------------------------------------------|
| **haproxy** | 2025-03-21 | **Overdue** (~19 months) | Dedicated issue → **3.4 LTS**; security:true; HA test floor (`clean-start --ha` + failover). **T4 / heavy path — Marc go.** |
| **eslint-mobile** | 2026-08-06 | **Overdue** | Keep **accept-risk** until RN ESLint presets allow 10; no runtime exposure. |
| **kotlin-android** | 2025-12-16 | **Overdue** (line policy) | Stay **follow:react-native**; do not solo-bump. Surface with Android Gradle HITL. |
| **node** (Active LTS) | Active ends **2026-10-20**; Maintenance EOL 2028-04-30 | Transition tomorrow | **Informational** — registry `eol` stays 2028-04-30; no bump. |

**Not in the 6-month EOL window (current lines):** Temurin 25, PostgreSQL 18, Spring Boot 4.1,
Alpine 3.24, Rocky 10, Ubuntu 24.04 runners, Python 3.12, Caddy 2, React 19, Gradle 9, RN 0.87
(API still supported / `eol: false`).

**Near-term non-EOL signals (for January quarterly):**

- Maven **3.10** eligible under target rule ~**2026-12-27** (light path).
- Node **26** LTS age gate ~**2027-01-28**.
- Python floor raise to **3.13** planned **2027-04-02** (distro guard).
- JDK 17 Android CI EOL **2027-10-31** (outside this window; follow RN).

## Decisions recorded this review

| Entry | Decision after review | Notes |
|-------|----------------------|-------|
| alpine-bootstrap-init | **keep** @ 3.24 | Closed the `#762` drift; security line current |
| java-* eol_slug | **temurin** | Register matches API product id |
| maven-build-image | **keep** @ 3.9; slug `maven` | Watch 3.10 for Jan light path |
| haproxy | **plan** (unchanged) | Still the loudest overdue item — needs Marc-funded issue |
| react-native | **plan** (unchanged) | Cleared stale 2027-02-01 estimate; API `eol: false` |
| java-jdk | **keep** @ 25 | Marc lock affirmed |
| eslint-mobile | **accept-risk** (unchanged) | |
| All others | **keep** / **follow:** as before | `last_review` → 2026-10-19 |

## Explicit non-actions

- No Docker image tag bumps (HAProxy, Maven, Postgres, Caddy, Temurin, Rocky).
- No Android Gradle / Kotlin / AGP / RN line bumps (`#717` remains Marc HITL).
- No Spring Boot 4.2, Node 26, TypeScript 7, or ESLint 10 mobile moves.
- No new GitHub issues opened this pass (HAProxy / RN issues wait for Marc go + funding).

## Follow-ups for Marc

1. **Fund HAProxy 2.9 → 3.4** dedicated issue (security opinion from Christophe) — highest EOL debt.
2. **Android Gradle HITL** on open Dependabot `#717` (and any AGP/Kotlin coupling) when ready.
3. Confirm RN line plan timing (T1 2027) vs `#627` Pixel smoke residual.
4. Optional Jan 2027 light path: Maven build image **3.9 → 3.10** after 3-month age gate.

## Validation

- Docs / register only — no `./scripts/build.sh` required for product code.
- Drift re-check after registry edit: all `expect` strings match sources.
- endoflife.date queried live on 2026-10-19 (API JSON per slug).
