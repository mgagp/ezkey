# Backlog Index

## Purpose

This index provides a lightweight list of active ideas and their current state.

## Active ideas

| ID | Title | Status | Priority | Components | Last reviewed |
|----|-------|--------|----------|------------|---------------|
| `I-2026-0001` | Per-enrollment local authentication policy for mobile respond | `incubating` | `P1` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-05-07` |
| `I-2026-0002` | Re-encryption batch UI: async button behavior | `incubating` | `P2` | `admin-ui`, `admin-api` | `2026-05-19` |
| `I-2026-0003` | High-availability Docker stack: parity review with `cleanstart.sh` | `incubating` | `P2` | `infra`, `admin-api`, `auth-api`, `integration-api` | `2026-05-19` |
| `I-2026-0004` | Admin API: configurable acceptance of API-key authentication | `promoted` | `P1` | `admin-api`, `sdk-java`, `docs` | `2026-05-25` |
| `I-2026-0005` | Checkpoint integrity breaks: declared remediation and reattachment | `incubating` | `P1` | `admin-api`, `audit` | `2026-05-17` |
| `I-2026-0006` | Nightly retroactive integrity validation batch | `incubating` | `P1` | `admin-api`, `audit`, `infra` | `2026-05-19` |
| `I-2026-0007` | Admin Dashboard: batch health and integrity widgets | `incubating` | `P1` | `admin-ui`, `admin-api` | `2026-05-19` |
| `I-2026-0008` | Rate-limit baseline analysis and generalization | `done` | `P2` | `admin-api`, `auth-api`, `integration-api` | `2026-05-24` |
| `I-2026-0010` | Phone-to-phone enrollment transfer ceremony | `ready` | `P2` | `mobile`, `auth-api`, `admin-api`, `admin-ui` | `2026-05-24` |
| `I-2026-0011` | Bootstrap clean-start: activation-code mode as default | `incubating` | `P1` | `admin-api`, `infra`, `bootstrap`, `docs` | `2026-05-19` |
| `I-2026-0012` | Hard-coded Ezkey System user sensitivity audit | `incubating` | `P2` | `admin-api`, `core`, `infra` | `2026-05-19` |
| `I-2026-0013` | Paginated admin screens: functional and operational pertinence review | `incubating` | `P2` | `admin-ui`, `admin-api` | `2026-05-19` |
| `I-2026-0014` | Paginated screens display strategy: foreign keys vs intelligent joins | `incubating` | `P2` | `admin-ui`, `admin-api`, `audit` | `2026-05-19` |
| `I-2026-0015` | Business limits on potentially large-volume SQL queries | `incubating` | `P2` | `admin-api`, `auth-api`, `integration-api`, `repositories` | `2026-05-24` |
| `I-2026-0017` | Profile elaboration template, skill, and per-client document workflow | `captured` | `P1` | `docs (product-docs)`, `methodology`, `skills` | `2026-05-08` |
| `I-2026-0018` | Profile generator skill: elaboration document to runtime configs | `captured` | `P2` | `docs (product-docs)`, `methodology`, `skills`, `infra` | `2026-05-08` |
| `I-2026-0020` | Integration ecosystem shell catalog & publish workflow | `captured` | `P2` | `docs`, GitHub ecosystem, `product-docs (traceability)` | `2026-05-11` |
| `I-2026-0021` | PostgreSQL application role and table permissions matrix | `incubating` | `P1` | `infra`, `core`, `admin-api`, `auth-api`, `integration-api`, `audit` | `2026-05-24` |
| `I-2026-0022` | Admin API scheduled jobs catalog (living document) | `incubating` | `P2` | `admin-api`, `docs` | `2026-05-24` |
| `I-2026-0023` | Email channel R1: optional operator-triggered delivery | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-05-24` |
| `I-2026-0024` | SMS channel R1: HTTP adapter SPI + optional operator send | `incubating` | `P2` | `admin-api`, `admin-ui`, `infra`, `docs` | `2026-05-24` |
| `I-2026-0025` | Auth API protocol capability versioning | `incubating` | `P2` | `auth-api`, `mobile`, `admin-api`, `docs` | `2026-05-24` |
| `I-2026-05-28-admin-ui-orval-upgrade` | Admin UI Orval upgrade (8.5 → 8.13 stepwise migration) | `ready` | `P2` | `admin-ui` | `2026-05-28` |

## Recently completed

| ID | Title | Closed date | Notes |
|----|-------|-------------|-------|
| `I-2026-0016` | Methodology hygiene follow-ups (Tier 2 from 2026-05-08 nomenclature pass) | `2026-05-24` | Closed Tier 2 items E–K: decision recording scopes, phase/component tags, superseded links, optional automation follow-up, corpus completeness audit checklist. Decision record `2026-05-24-artifact-tagging-canonization-conventions.md`. |
| `I-2026-0008` | Rate-limit baseline analysis and generalization | `2026-05-24` | Policy deliverable [`rate-limit-baseline-policy.md`](../rate-limit-baseline-policy.md): four families (device / integration / admin ops / login); honest no-single-baseline outcome; CONFIGURATION cross-links; no code unification R1. Closes Blitz D1 with registry downscope. |
| `I-2026-05-24-admin-ui-vite8-upgrade` | Admin UI Vite 8 upgrade | `2026-05-24` | Delivered via PR #154 / TB-2026-05-24: Vite 8, Vitest 4, Tailwind 4.2.2+, plugin-react v6; Rolldown demo DCE fix in `demo-mode.ts`. First Lane A toolchain chore with GitHub issue workflow. |
| `I-2026-0026` | OpenAPI exposure and API portal posture | `2026-05-22` | Delivered through `TB-2026-0003`: public API portal on `ezkey.org`, `ReDoc CE` pinning, `Integration API` spec publication path, and first prod-like raw-doc hardening on `EXP1`. |

## Parked

| ID | Title | Reason | Next review |
|----|-------|--------|-------------|
| `I-2026-0009` | Global controllers registry | **Dropped** 2026-05-24 — living registry downscoped; see [`methodology/decisions/2026-05-24-controllers-registry-downscope.md`](../methodology/decisions/2026-05-24-controllers-registry-downscope.md) | n/a |
