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
