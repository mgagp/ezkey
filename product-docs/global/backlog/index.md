# Backlog Index

## Purpose

This index provides a lightweight list of active ideas and their current state.

## Current prioritization anchor (read first)

**“Where are we?” / “What should be next?”** for the September 2026 operable-release target:

→ [`../operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)

**Summary (2026-07-05):** Wave B integrity cluster R1 **closed** (GitHub #269). Wave C alerts list polish **done**
([`TB-2026-07-03-admin-ui-alerts-list-polish.md`](backlog/TB-2026-07-03-admin-ui-alerts-list-polish.md)).
Dashboard widget signal model **done** — [`I-2026-0030`](ideas/I-2026-0030-admin-dashboard-widget-signal-model-review.md)
(enrollment invalid/revoked split + signal-model doc; closed on `I-*` without retroactive `TB-*`).
Audit chain checkpoints matrix row **done** — [`TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md`](backlog/TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md) (Wave C closed).
Wave B closeout ML:

## Active ideas

| ID | Title | Status | Priority | Components | Last reviewed |
|----|-------|--------|----------|------------|---------------|
| `I-2026-0001` | Per-enrollment local authentication policy for mobile respond | `incubating` | `P1` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-05-07` |
| `I-2026-0003` | High-availability Docker stack: parity review with `cleanstart.sh` | `incubating` | `P2` | `infra`, `admin-api`, `auth-api`, `integration-api` | `2026-05-19` |
| `I-2026-0004` | Admin API: configurable acceptance of API-key authentication | `promoted` | `P1` | `admin-api`, `sdk-java`, `docs` | `2026-05-25` |
| `I-2026-0010` | Phone-to-phone enrollment transfer ceremony | `ready` | `P2` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-05-24` |
| `I-2026-0011` | Bootstrap clean-start: activation-code mode as default | `incubating` | `P1` | `admin-api`, `infra`, `bootstrap`, `docs` | `2026-05-19` |
| `I-2026-0012` | Hard-coded Ezkey System user sensitivity audit | `incubating` | `P2` | `admin-api`, `core`, `infra` | `2026-05-19` |
| `I-2026-0013` | Paginated admin screens: functional and operational pertinence review | `incubating` | `P2` | `admin-ui`, `admin-api` | `2026-06-20` |
| `I-2026-0014` | Paginated screens display strategy: foreign keys vs intelligent joins | `incubating` | `P2` | `admin-ui`, `admin-api`, `audit` | `2026-06-20` |
| `I-2026-0015` | Business limits on potentially large-volume SQL queries | `incubating` | `P2` | `admin-api`, `auth-api`, `integration-api`, `repositories` | `2026-05-24` |
| `I-2026-0017` | Profile elaboration template, skill, and per-client document workflow | `captured` | `P1` | `docs (product-docs)`, `methodology`, `skills` | `2026-05-08` |
| `I-2026-0018` | Profile generator skill: elaboration document to runtime configs | `captured` | `P2` | `docs (product-docs)`, `methodology`, `skills`, `infra` | `2026-05-08` |
| `I-2026-0020` | Integration ecosystem shell catalog & publish workflow | `captured` | `P2` | `docs`, GitHub ecosystem, `product-docs (traceability)` | `2026-05-11` |
| `I-2026-0021` | PostgreSQL application role and table permissions matrix | `incubating` | `P1` | `infra`, `core`, `admin-api`, `auth-api`, `integration-api`, `audit` | `2026-05-24` |
| `I-2026-0022` | Admin API scheduled jobs catalog (living document) | `incubating` | `P2` | `admin-api`, `docs` | `2026-05-24` |
| `I-2026-0023` | Email channel R1: optional operator-triggered delivery | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-05-24` |
| `I-2026-0024` | SMS channel R1: HTTP adapter SPI + optional operator send | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-05-24` |
| `I-2026-0025` | Auth API protocol capability versioning | `incubating` | `P2` | `auth-api`, `mobile`, `admin-api`, `docs` | `2026-05-24` |
| `I-2026-05-31-mobile-android-stack-followups` | Mobile Android stack follow-ups (post-#177) | `incubating` | `P2` | `mobile`, `android`, `ezkey-tests` | `2026-05-31` |
| `I-2026-06-28-audit-archive-export-spi` | Audit archive export SPI: sealed-batch detachment + vendor-neutral immutable retention | `incubating` | `P3` | `core`, `admin-api`, `admin-ui`, `audit`, `infra`, `docs`, peripheral | `2026-06-28` |
| `I-2026-0029` | Re-encryption: indexed encryption key id columns (replace LIKE scans) | `ready` | `P2` | `core`, `admin-api`, `infra`, `docs` | `2026-06-28` |
| `I-2026-07-05-distilled-admin-platform-starter` | Distilled admin platform starter (Spring Boot + operator UI) | `parked` | `P3` | `admin-api`, `admin-ui`, `docs`, ecosystem GitHub, `sdk-java` | `2026-07-05` |
| `I-2026-07-05-enrollment-integration-key-cycling` | Enrollment integration key cycling via auth-exchange hooks | `incubating` | `P3` | `auth-api`, `core`, `mobile`, `docs`, `crypto` | `2026-07-05` |
| `I-2026-0032` | Proof token hash-only storage (tiered hardening) | `incubating` | `P1` | `core`, `auth-api`, `admin-api`, `docs`, `crypto` | `2026-07-06` |
| `I-2026-07-09` | `encryption.required` read-path parity (at-rest fields) | `ready` | `P2` | `core`, `core-security`, `admin-api`, `auth-api`, `docs` | `2026-07-09` |
| `I-2026-07-10` | Admin recovery codes: exhausted-set vs initial-issuance label UX | `captured` | `P3` | `admin-ui`, `admin-api`, `docs` | `2026-07-09` |

## Active tracer bullets (ready / in progress)

| ID | Title | Status | Related idea |
|----|-------|--------|--------------|
| `TB-2026-07-06` | Device proof token hash-only storage (Tier 0) | `active` | `I-2026-0032` (#296) |

## Recently completed

| ID | Title | Closed date | Notes |
|----|-------|-------------|-------|
| `I-2026-0030` | Admin Dashboard: widget signal model review and realignment | `2026-07-05` | PR #291. Enrollment `invalid`/`revoked` badges; `dashboard-widget-signal-model.md`. Closed on `I-*` (no `TB-*`). |
| `I-2026-0007` | Admin Dashboard: batch health and integrity widgets (Wave B B3) | `2026-07-03` | PR #289 / TB `TB-2026-07-03-dashboard-batch-health-widgets`. Program #269 closed same day. |
| `I-2026-0005` | Checkpoint integrity breaks: remediation + entry conciliation (Wave B B2/B2.6) | `2026-07-03` | PR #287 (B2.6) + earlier B2 chain reconcile. TB `TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence`. |
| `I-2026-0006` | Nightly retroactive integrity validation batch (Wave B B1) | `2026-06-29` | PR #270 / TB `TB-2026-06-28-nightly-integrity-validation-batch`. Detective layer + job registry + `AUDIT_INTEGRITY_RUPTURE`. |
| `I-2026-0002` | Re-encryption batch UI: async button behavior | `2026-06-28` | PR #265 / issue #264. Async manual triggers (202 Accepted); TB `TB-2026-06-27-admin-ui-encryption-reencryption-async`; matrix encryption keys + batches → `implemented`. Origin: Blitz D1. |
| `I-2026-0028` | Admin UI operator experience — post–Tier A follow-up | `2026-06-27` | Program closed #236 → P4 + detail FK parity (#261, #263). ML `ML-2026-06-27-admin-ui-operator-experience-program-closeout`. P3 encryption slice spun out → `I-2026-0002` (done 2026-06-28). |
| `I-2026-06-05-admin-enrollment-vs-admin-login-ux-clarity` | Admin enrollment vs admin login UX clarity | `2026-06-06` | PR #192 / issue #191 closed. Lane D first cut: dual-state admin detail, recovery-code gating tooltip, activation/recovery copy (EN/FR). Hygiene #182 first slice co-merged; #182 closed 2026-06-18 (React Doctor campaign #232/#234). |
| `I-2026-06-18-demo-device-qr-auth-url-parity` | Demo Device QR authUrl routing parity | `2026-06-18` | PR #222 / issue #223; dual-repo delivery; `TB-*` + `ML-2026-06-18-demo-device-qr-session`. Post-close Jackson/standalone hygiene in consolidation ML. |
| `I-2026-05-29-mobile-stack-modernization` | Mobile stack modernization (#177) | `2026-05-31` | RN 0.85.3, VC5+Nitro, AS3, CI `validate:ci`, ESLint 9, post-merge dep bumps, canonical Android build scripts. Maestro enrollment deferred to `TB-2026-0002`. |
| `I-2026-05-28-admin-ui-orval-upgrade` | Admin UI Orval upgrade (8.5 → 8.13) | `2026-05-29` | PR #175; stepwise ladder; Orval 8.13 with verb-aware config fix at 8.10/8.11. Post-program 8.18 hygiene: PR #234. |
| `I-2026-0016` | Methodology hygiene follow-ups (Tier 2 from 2026-05-08 nomenclature pass) | `2026-05-24` | Closed Tier 2 items E–K: decision recording scopes, phase/component tags, superseded links, optional automation follow-up, corpus completeness audit checklist. Decision record `2026-05-24-artifact-tagging-canonization-conventions.md`. |
| `I-2026-0008` | Rate-limit baseline analysis and generalization | `2026-05-24` | Policy deliverable [`rate-limit-baseline-policy.md`](../rate-limit-baseline-policy.md): four families (device / integration / admin ops / login); honest no-single-baseline outcome; CONFIGURATION cross-links; no code unification R1. Closes Blitz D1 with registry downscope. |
| `I-2026-05-24-admin-ui-vite8-upgrade` | Admin UI Vite 8 upgrade | `2026-05-24` | Delivered via PR #154 / TB-2026-05-24: Vite 8, Vitest 4, Tailwind 4.2.2+, plugin-react v6; Rolldown demo DCE fix in `demo-mode.ts`. First Lane A toolchain chore with GitHub issue workflow. |
| `I-2026-0026` | OpenAPI exposure and API portal posture | `2026-05-22` | Delivered through `TB-2026-0003`: public API portal on `ezkey.org`, `ReDoc CE` pinning, `Integration API` spec publication path, and first prod-like raw-doc hardening on `EXP1`. |

## Parked

| ID | Title | Reason | Next review |
|----|-------|--------|-------------|
| `I-2026-0009` | Global controllers registry | **Dropped** 2026-05-24 — living registry downscoped; see [`methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../methodology/decisions/2026-05-24-controllers-registry-downscope.md) | n/a |
| `I-2026-0031` | SDK dogfood: first-party consumption and honest narrative | **Parked** 2026-06-30 — pre-analysis complete (layers A/B, no Admin UI → Integration SDK); deferred for September operable-release focus. Insight: [`grill-sessions/2026-06-30-sdk-dogfood-grill-me.md`](grill-sessions/2026-06-30-sdk-dogfood-grill-me.md). `I-2026-0004` unchanged. | post–Sept 2026 milestone or distribution trigger |
| `I-2026-07-05-distilled-admin-platform-starter` | Distilled admin platform starter (`ezkey-app-starter`) | **Parked** 2026-07-05 — grill complete (D1–D10); external greenfield starter, not lite platform. Insight: [`grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md`](grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md). | post–Sept 2026 milestone or distribution trigger |
