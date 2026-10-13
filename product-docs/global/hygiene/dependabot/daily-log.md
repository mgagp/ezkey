# Dependabot curated — weekday daily log

Append-only residual for **weekday light** `dependabot-curated` passes.

**Rules** (see [`README.md`](README.md) § Cadence):

- Append a `## YYYY-MM-DD` section **only when** the pass merged, held, or deferred something.
- **Silent when the actionable queue is empty** — do not write an empty-day entry.
- Keep each section short (lots touched, Orval O-tier if any, concurrency check, validation one-liner).
- Monday full passes still write a dedicated `YYYY-MM-DD-pass-N.md` from [`TEMPLATE.md`](TEMPLATE.md);
  they may summarize this file’s entries for the week.

---

<!-- Newest sections at the bottom. Example:

## 2026-10-07

- Pass: weekday light; concurrency: clear
- Merged: #NNN (T1 maven-patch), #NNN (Admin Orval O0)
- Validation: lint + tsc + vitest green; Playwright smoke n/a
- Residual: none

-->

## 2026-10-07

- Pass: weekday light (first under #678 / `e235d4cb`); concurrency: clear (owning pass; #672 crypto agent out of scope)
- GitHub write: `GH_TOKEN as mgagp`
- Merged (squash):
  - Java: #661 checkstyle 14.1→14.3 (T2) `7bc70945`; #664 logback 1.6.4→1.6.5 (T1) `eab3d20d`; #665 bucket4j 8.20→8.21 (T3) `dd3ae49b`
  - Admin UI: #663 eslint 10.12 (T2) `1f163d3f`; #666 vite group (T3) `b4be8507`; #684 lucide 1.33→1.52 (T3, superseded #667) `18b864a6`; #668 tanstack 5.104.1 (T3) `c349adde`; #685 `@types/node` (T2) `6ad989cd`; #683 globals (T1) `7f2115d4`; #686 zod 4.6.5 (T1) `31d34b95`; #682 tailwind-merge 3.7 (T3) `0e35c657`; #687 react-hook-form 7.89 (T3) `08dc491d`
  - SDK JS: #662 `@types/node` 26.6.4 (T1) `af21ec39`
  - Mobile: #671 safe-area-context 5.10.1 (T1) `38052e53`; #670 Orval 8.36→8.39 (**O0** bit-identical after `yarn generate:api` on Node 22.22.2; Dependabot PR had pin-only diff) `7c2ebef6`
- Held (morning light):
  - #669 navigation, #688 react-i18next, #689 mobile tanstack, #691 gesture-handler — **infra-blocked** (Android foojay JDK 21 download HTTP 400; `js-validate` green; PAT cannot re-run Actions)
  - #690 mobile ESLint 10 — ecosystem gate (+ foojay red)
  - #653 Jest 30 — standing hold; #672 js-sha256 — other agent; deferred `#498`/`#337`/`#347`
- Validation: `./scripts/build.sh` OK; Admin UI lint + `tsc -b` + vitest 129/129; Playwright 5/5; mobile `yarn validate` / jest 307/307; Orval exact pin `8.39.0` + AGENTS.md sync
- Residual: phone smoke for #670/#671 tracked on #627 (gates next Play AAB)

### Afternoon unblock (Marc-approved; after Android toolchain fix)

- Concurrency: clear (owning pass; prior Dependabot batch idle)
- Toolchain fix: #677 stop Gradle Foojay JDK 21 daemon download (keep JDK 17) — squash-merged `a6bab801` (CI green: `js-validate`, `android-jvm-unit-tests`, `android-16kb-alignment`, Confirm JDK 17 on PATH)
- Unblocked mobile (rebase → CI green → squash; #672 recreate):
  - #669 mobile-navigation group (T3) `e22fe7f6`
  - #688 react-i18next 17.0.14→17.0.15 (T1) `717d0745`
  - #689 `@tanstack/react-query` 5.103.2→5.104.1 (T1) `e31a88de`
  - #691 react-native-gesture-handler 3.0.2→3.3.0 (T2) `1434078a`
  - #672 js-sha256 0.11.1→1.0.0 (crypto major; Marc + Christophe approved) `939ac283` — `localEnrollmentIdentity` + `sha256HexUtf8` characterization PASS in `js-validate` (42 suites)
- Still held: #690 ESLint 10 (ecosystem gate), #653 Jest 30, deferred `*` PRs
- Residual: phone smoke for #669/#688/#689/#691/#672 tracked on #627 (same pattern as #670/#671; gates next Play AAB)

## 2026-10-09

- Pass: weekday light (autonomous); concurrency: clear (no Dependabot/hygiene merges in prior ~30 min; no other in-flight `dependabot-curated` agent)
- GitHub write: `GH_TOKEN as mgagp`
- Merged (squash):
  - Mobile T1: #707 joi 17.13.3→17.13.8 `6b8e25fb`; #708 compression 1.8.1→1.8.2 `dce9bac6`; #709 source-map-js 1.2.1→1.2.2 `887ba14a`
  - Mobile Orval: #706 8.39.0→8.40.0 (**O0** bit-identical after `yarn generate:api` on Node 24; Dependabot PR pin+lock only) `5fe36cbb`
  - Admin Orval: #704 8.39.0→8.40.0 (**O0** bit-identical `src/generated/` base vs bump on Node 24) `497b00fb`
  - Admin UI Vite: #723 `@vitejs/plugin-react` 6.1.1→6.1.2 (T3) `8b3d662a`
  - Maven: #716 javamelody-spring-boot4-starter 2.8.0→2.9.0 (T3) `a3b0df66`
- Held (out of light scope):
  - #713 adm-zip 0.5→0.6 (0.x minor → Monday full)
  - #714 express 4→5, #715 marked 14→18 (T4 majors, product-docs/site)
  - #705 jsdom 26→30 Admin UI (T4 major)
  - #717 Android Gradle group, #718 Docker images group (Marc 2026-10-08: HITL / full passes only; revisit after #702)
  - #690 mobile ESLint 10, #653 Jest 30 (ecosystem gates)
  - deferred:later-train `#498`/`#337`/`#347` (skip)
- Infra note (non-blocking): pre-rebase `#704` had stale `submit-maven` FAILURE (Automatic Dependency Submission; missing `org.ezkey:checkstyle-config` — run `37783553186`); `ci-gate` green; cleared after `@dependabot rebase`
- Validation: `./scripts/build.sh` OK; clean-start OK (Admin `9081` UP, audit HMAC READY); `mvn test -pl ezkey-tests -P smoke-tests` 12/12; Admin vitest 148/148; Playwright 5/5; mobile `yarn validate` + jest 307/307; Orval exact pin `8.40.0` + AGENTS.md sync (Admin UI + mobile)
- Residual: phone smoke for #706/#707/#708/#709 tracked on #627 (gates next Play AAB)

## 2026-10-12

- Pass: targeted #718 split (Marc-approved; not a full dependabot-curated lot); concurrency: clear for this slice (Monday campaign #760 already merged; no concurrent Dependabot merges on docker)
- GitHub write: `GH_TOKEN as mgagp`
- Hygiene PR (replaces #718):
  - `docker/bootstrap-init`: Alpine **3.19→3.24** (accepted)
  - `docker/Dockerfile`: **keep** `maven:3.9-eclipse-temurin-25` (refuse temurin-26; JDK 25 LTS)
  - `ezkey-admin-ui/docker/ui-browser-tests.Dockerfile`: Playwright **v1.60.0-noble→v1.63.0-noble** (matches lockfile `@playwright/test` 1.63.0; not Dependabot's v1.64.0)
  - `.github/dependabot.yml`: ignore `maven` versions `*-eclipse-temurin-26|27|28*` and `*-temurin-26|27|28*` (comment: revisit at JDK 29 LTS)
- Replacement PR **#762** open for review (Marc paused dependency merges for functional tests) — **do not merge yet**; #718 stays open until squash-merge
- Local validation on #762: `./scripts/build.sh` OK; bootstrap-init Alpine 3.24.2 (curl/jq, UID 100/GID 101); clean-start OK (bind+verify+demo-device seed); Playwright **5/5** in `v1.63.0-noble`
- Residual: Dependabot may reopen Playwright 1.64 image bump when npm moves; #717 untouched

### #762 Dependabot config regression (same day)

- Cause: #762 merged invalid docker `ignore.versions` globs (`*-eclipse-temurin-26*`, …). GitHub check `.github/dependabot.yml` fails on every PR/main with: `The property '#/updates/8/ignore/1/versions' includes invalid version requirements for a docker ignore condition`. Docker ignores require Bundler syntax, not shell globs.
- Fix (option a, #764): Bundler ranges `">= 3.9.26, < 3.9.29"` / `3.10.*` / `4.0.*` for dependency `maven` (covers JDK 26–28 on current + next Maven lines; exact `3.9.26|27|28` left a gap on `3.10-eclipse-temurin-26`). Mapping: tag `WORDS_WITH_BUILD` → `3.9.N` (dependabot-core `tag.rb` / `version.rb`). Manual extend per new Maven minor; revisit at JDK 29 LTS; fallback `@dependabot ignore this version`. Rejected: (b) blanket ignore; (c) curated-refusal only as sole mechanism.
- Dedicated PR **#764** (do **not** merge until Marc go; do not touch #730).

## 2026-10-13

- Pass: weekday light (autonomous; afternoon, Marc-approved after the morning pass backed off); concurrency: clear at ~14:30 ET (latest `main` merge #796 docs ~32 min earlier, not Dependabot/hygiene; no other in-flight `dependabot-curated` / Orval agent)
- GitHub write: GitHub MCP connector as `mgagp` (box `gh` not logged in; no `GH_TOKEN`)
- Merge bar (#787): `ci-gate` green + `mergeable_state` clean (or local `git merge-tree` clean vs moving `main`), squash pinned to head SHA; CodeQL check `neutral` with 0 annotations on every PR ("5 configurations not found" — default setup does not analyze Dependabot refs), so no PR-introduced alerts; 0 review comments / threads
- Merged (squash):
  - Maven: #774 spotless-maven-plugin 3.10.3→3.10.4 (T1) `d210cccc`; #772 postgresql 42.7.13→42.7.14 (T1) `5c78c773`; #765 tomcat 11.0.26→11.0.27 (T1) `406b6659`; #766 rest-assured 6.0.1→6.1.0 (T2) `7791dbb7`
  - Actions: #770 actions/setup-node 7.0.0→7.1.0 (T2) `3bff17b5`
  - Mobile: #775 mobile-tooling (`@babel/core` 8.0.6→8.0.7, `@babel/runtime` 8.0.5→8.0.7) (T1) `7912f2bc`
  - Admin UI: #768 vite 8.3.3→8.3.4 (T1) `48e46948`; #776 lucide-react 1.52→1.54 (T3) `30853af6`
  - Playwright lot (merged together; image matches lockfile): #771 `@playwright/test` 1.63.0→1.64.0 (T2) `c3259ad5` + #769 `mcr.microsoft.com/playwright` v1.63.0-noble→v1.64.0-noble (T2) `ec717ab1`
  - Admin Orval: #773 8.40.0→8.41.0 (**O0** — `src/generated/` bit-identical, 227 files, base vs bump with `main`'s spec on Node 24.21.0; lint + `tsc -b` + vitest 157/157 green on bump) `dc5ffce0`
  - Mobile Orval: #777 8.40.0→8.41.0 (**O0** — 8.40.0 reproduces committed `app/services/api/generated` byte-for-byte; 8.41.0 output identical, 20 files; Dependabot PR pin+lock only) `28a9ed59`
- Held:
  - #778 react-native-screens 4.28→4.29 (T3) — CI red: `mobile / js-validate` fails only on stale `app/data/thirdPartyLicenses.json` (jest 307/307 green). Fix: `yarn license:app-data` on the Dependabot branch (or hygiene branch), then CI → merge next pass
- Skipped (leave-alone): #717 (HITL until #702), #779 mobile ESLint 10 and #653 Jest 30 (ecosystem gates), #767 textual (pip); deferred:later-train `#498`/`#337`/`#347`
- Pin sync: Orval exact pin `8.41.0` kept in both `package.json`; AGENTS.md pin lines (Admin UI + mobile) and Playwright Dockerfile comment synced in this docs PR
- Validation: per-PR Dependabot CI green (backend / admin-ui / mobile jobs as applicable); local Orval codegen diff + Admin lint/tsc/vitest on Node 24. **Deferred** to next session on Rootbeer/Mac (box has no JDK/Docker): `./scripts/build.sh`, clean-start + functional smoke (T2/T3 Maven: tomcat, postgresql, rest-assured), Playwright 5/5 in `v1.64.0-noble` (Vite + lucide + Playwright lot)
- Residual: phone smoke for #775/#777 tracked on #627 (gates next Play AAB). Nothing deployed (no ezkey.online, no methodology.ezkey.org)
