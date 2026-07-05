# Vision Note — `V-2026-07-05-distilled-admin-platform-starter` Distilled admin platform starter for greenfield Spring Boot projects

## Metadata

- **ID:** `V-2026-07-05-distilled-admin-platform-starter`
- **Status:** `parked`
- **Lane:** `A`
- **Created at:** `2026-07-05`
- **Updated at:** `2026-07-05`
- **Parked at:** `2026-07-05`
- **Captured by:** Marc

## Intent

Offer a **distilled, clone-ready admin platform starter** — backend (Spring Boot + Spring Security)
and operator UI (modern React stack) — derived from Ezkey's own Admin API and Admin UI patterns, as
a separate ecosystem repository (`ezkey-app-starter`). The starter goes **beyond SDK jumpstart**: it
gives greenfield Java projects a credible full-stack foundation with session/token security, RBAC,
persistence scaffolding, and documented API conventions, with **Ezkey MFA as the default
authentication path** while remaining honest that adopters may add or substitute other login methods.

The starter is a **generic consumer application template**, not a lite Ezkey platform.

## Motivation

Ezkey already **eats its own dog food** at layer A (cryptographic product dogfood): operators
authenticate through Ezkey MFA, not passwords. External feedback still surfaces a credible
counter-argument for small personal projects: *"Passkeys are built in; I still had to build token
tables and security plumbing anyway — how much does Ezkey really save?"*

That objection is partly fair for trivial scopes, but it underplays the **integrated cost** of doing
login, API protection, token lifecycle, database schema, RBAC, and operator UI well on a modern
Java stack. Ezkey has invested heavily in those cross-cutting layers inside Admin API and Admin UI.

Distilling that investment into a **community-facing starter** would:

- give back reusable structure without requiring adopters to run the full Ezkey platform as their app,
- demonstrate Ezkey-backed auth in a **greenfield application shape** (not only as platform admin),
- complement SDK-only onboarding (`ezkey-sdk-typescript`, Java Integration SDK) with an
  **application boilerplate** scoped to the Admin UI + Admin API **pattern family**,
- provide legitimate **layer-B SDK dogfood** via Java Integration SDK in the starter backend.

## Potential impact

- **Product milestones:** `P3-distribution`; post–September 2026 operable-release (`R1`) or
  low-intensity side project — not on the current operability compass.
- **Components:** `admin-api`, `admin-ui`, `docs`, ecosystem GitHub (outside monorepo), `sdk-java`,
  `ezkey-demo-app-acme` (contrast reference).
- **User segments:**
  - Backend/fullstack developers starting **Java Spring Boot + Spring Security** projects.
  - Community builders (e.g. sports-club registration) who want modern security defaults.
  - Evaluators comparing Ezkey to "passkeys + DIY token tables."

## Signals and constraints

- **Reuse hypothesis (revised by grill):** ~**50–65%** of infrastructure patterns reusable once
  platform domain is stripped — validate with distillation matrix on resume.
- **Auth architecture (settled):** Starter backend uses **Java Integration SDK** against a running
  Ezkey instance; issues **local opaque session tokens**; UI calls starter API only (Orval-style).
- **Existing references:** Demo ACME (Thymeleaf M2M demo) and Demo Device do **not** replace this starter.
- **Related but distinct:** [`I-2026-0031`](../backlog/ideas/I-2026-0031-sdk-dogfood-first-party-consumption.md)
  (parked) — first-party narrative inside monorepo; this vision — external adopter starter.
- **Non-goal for R1:** Do not delay September 2026 structural hardening.

## Promotion criteria

Resume and promote when **all** are true:

1. Post–R1 calendar or explicit distribution milestone (`F-sdk-and-cli-growth`) allocates capacity.
2. Distillation boundary matrix exists (generic vs platform-specific).
3. Ecosystem repo `ezkey-app-starter` shell and maintenance model drafted.
4. `I-2026-0004` Integration API alignment sufficient for starter auth wiring.

## Parked (2026-07-05)

Pre-analysis and grill complete. No execution until resume trigger. See grill session for settled
decisions D1–D10.

**Resume when:** post–September 2026 operable-release gate; distribution milestone; or adopter demand
for full-stack starter beyond SDKs and Demo ACME.

## Related documents

- [`I-2026-07-05-distilled-admin-platform-starter`](../backlog/ideas/I-2026-07-05-distilled-admin-platform-starter.md)
- [`2026-07-05-distilled-admin-platform-starter-grill-me.md`](../backlog/grill-sessions/2026-07-05-distilled-admin-platform-starter-grill-me.md)
- [`V-2026-06-30-sdk-dogfood-integrity-posture`](V-2026-06-30-sdk-dogfood-integrity-posture.md)
- [`I-2026-0031`](../backlog/ideas/I-2026-0031-sdk-dogfood-first-party-consumption.md)
- [`I-2026-0004`](../backlog/ideas/I-2026-0004-admin-api-key-acceptance-flag.md)
- [`I-2026-0020`](../backlog/ideas/I-2026-0020-integration-ecosystem-shell-catalog.md)
- [`docs/ECOSYSTEM_REPOSITORIES.md`](../../../docs/ECOSYSTEM_REPOSITORIES.md)
- [`operational-readiness-prioritization-2026-09.md`](../operational-readiness-prioritization-2026-09.md)
- [`features-and-phases.md`](../features-and-phases.md) — `F-sdk-and-cli-growth`
