# Backlog Idea — `I-2026-07-05-distilled-admin-platform-starter` Distilled admin platform starter (Spring Boot + operator UI)

## Metadata

- **ID:** `I-2026-07-05-distilled-admin-platform-starter`
- **Status:** `parked`
- **Vision:** [`V-2026-07-05-distilled-admin-platform-starter`](../../vision/V-2026-07-05-distilled-admin-platform-starter.md)
- **Priority:** `P3`
- **Created at:** `2026-07-05`
- **Updated at:** `2026-07-05`
- **Last reviewed at:** `2026-07-05`
- **Parked at:** `2026-07-05`
- **Progression markers:** `P3-distribution`
- **Component tags:** `admin-api`, `admin-ui`, `docs`, ecosystem GitHub (outside monorepo), `sdk-java`
- **Lane:** `A`
- **Captured by:** Marc

## Intent

Distill Ezkey **Admin API** and **Admin UI** cross-cutting patterns into a **standalone ecosystem
starter** (`ezkey-app-starter`) that developers clone as the base for new **Java Spring Boot +
Spring Security** applications with a modern operator UI. The starter showcases **Ezkey MFA as the
default login path** (via Java Integration SDK on the backend + local opaque session tokens) while
packaging generic scaffolding: secured REST APIs, token persistence, RBAC hooks, Flyway baseline,
and spec-first UI conventions.

The starter is a **consumer application template**, not a lite Ezkey platform.

## Problem and value

- **Problem:** Evaluators dismiss Ezkey with: passkeys are easy, and builders still need token
  tables, API security, and admin UI plumbing. SDKs jumpstart Integration API calls; Demo ACME shows
  Thymeleaf M2M only. Full-stack admin patterns live trapped in the platform monorepo.
- **Expected value:** Community give-back; end-to-end clone-and-extend narrative; honest Ezkey MFA
  showcase in greenfield shape; credible modern Java + React stack without overselling.

## Scope

- **In scope (on resume):**
  - Distillation boundary matrix (generic vs platform-specific).
  - Ecosystem repo **`ezkey-app-starter`** (one-time fork; manual security cherry-pick policy).
  - Starter backend: Spring Security, opaque tokens, simplified RBAC (`ROLE_ADMIN`, optional
    `ROLE_USER`), DTO/service/OpenAPI patterns, Java Integration SDK for Ezkey MFA login.
  - Starter UI: Vite + Orval + Tailwind shell; auth gate; login/wait UX; no platform screens.
  - Optional sports-club domain stub (deferred to v0.1 vs v0.2 decision).
  - Docker compose or docs for **running Ezkey sidecar** alongside starter.
- **Out of scope (default):**
  - Full Ezkey platform domain (tenants, enrollments, keys, integrity, audit chain).
  - In-process `ezkey-core` coupling.
  - Global Admin / Tenant Admin platform semantics.
  - Monorepo Admin UI/API rewrite for extraction.
  - September 2026 operable-release work.

## Key assumptions

- ~**50–65%** infrastructure reuse once platform domain stripped (grill-revised from 60–75%).
- Target: Java Spring Boot + Spring Security greenfield admin apps.
- Separate ecosystem repo with its own release cadence.
- Running Ezkey instance required for MFA path; mock-auth dev-only with disclaimer.

## Risks and exceptions

- Distillation drift without cherry-pick policy.
- Confusion with platform admin or Demo ACME — mitigated by README positioning.
- Overclaim vs trivial passkey-only apps — honest segment targeting.
- Maintenance cost of public repo.

## Settled decisions (grill 2026-07-05)

See [`2026-07-05-distilled-admin-platform-starter-grill-me.md`](../grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md) — D1–D10 confirmed.

| ID | Summary |
| -- | ------- |
| D1 | One-time distillation fork, not live monorepo sync |
| D2 | Manual cherry-pick for security fixes |
| D3 | Single-tenant RBAC (`ROLE_ADMIN` + optional `ROLE_USER`) |
| D4 | Match UI toolchain; strip platform screens |
| D5 | Repo name **`ezkey-app-starter`** |
| D6 | Running Ezkey required for MFA; mock-auth dev-only |
| D7 | Reuse hypothesis **50–65%** |
| D8 | No monorepo rewrite for extraction |
| D9 | Park until post–R1 or distribution trigger |
| D10 | Backend Java SDK + local opaque tokens; UI → starter API only |

## Promotion notes

Move toward `ready` / `TB-*` when (after resume):

- Distillation matrix exists.
- First vertical slice defined: clone → Ezkey config → login → one CRUD screen.
- Smoke test plan: backend boot, MFA login, 401 without token.

## Parked (2026-07-05)

**Decision:** Pre-analysis and grill complete. No `TB-*`, no ecosystem repo, no distillation matrix
until resume trigger. September 2026 operability compass unchanged.

**What remains valid (do not re-litigate without new signal):**

- Starter ≠ lite platform; ≠ Demo ACME duplicate.
- Auth = external Ezkey via Integration SDK + local session tokens.
- Reuse ~50–65%, not 75%.

**Resume triggers (any one):**

- Post–September 2026 operable-release gate + maintainer capacity.
- Explicit `F-sdk-and-cli-growth` distribution push.
- Repeated adopter demand for full-stack starter beyond SDKs.

**Next review:** post–R1 milestone or on resume trigger.

## Priority posture

- **Not on** [`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md).
- **`I-2026-0004`** alignment benefits starter auth wiring when both resume.

## Links

- Vision: [`V-2026-07-05-distilled-admin-platform-starter`](../../vision/V-2026-07-05-distilled-admin-platform-starter.md)
- Grill: [`2026-07-05-distilled-admin-platform-starter-grill-me.md`](../grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md)
- Related (parked): [`I-2026-0031`](I-2026-0031-sdk-dogfood-first-party-consumption.md)
- Prerequisite: [`I-2026-0004`](I-2026-0004-admin-api-key-acceptance-flag.md)
- Ecosystem pattern: [`I-2026-0020`](I-2026-0020-integration-ecosystem-shell-catalog.md)
- Contrast: `ezkey-demo-app-acme`, Demo Device
- Program: [`F-sdk-and-cli-growth`](../../features-and-phases.md)
