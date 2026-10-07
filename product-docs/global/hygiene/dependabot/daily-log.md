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
- Held:
  - #669 navigation, #688 react-i18next, #689 mobile tanstack, #691 gesture-handler — **infra-blocked** (Android foojay JDK 21 download HTTP 400; `js-validate` green; PAT cannot re-run Actions)
  - #690 mobile ESLint 10 — ecosystem gate (+ foojay red)
  - #653 Jest 30 — standing hold; #672 js-sha256 — other agent; deferred `#498`/`#337`/`#347`
- Validation: `./scripts/build.sh` OK; Admin UI lint + `tsc -b` + vitest 129/129; Playwright 5/5; mobile `yarn validate` / jest 307/307; Orval exact pin `8.39.0` + AGENTS.md sync
- Residual: phone smoke for #670/#671 tracked on #627 (gates next Play AAB)
