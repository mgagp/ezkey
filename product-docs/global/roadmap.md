# Roadmap — Ezkey

## Purpose

This document describes the major product steps for Ezkey. It captures the current intent of the roadmap — not a detailed change history. Feature-level detail lives in [`features-and-phases.md`](features-and-phases.md), which remains the historical filename for the features and milestones catalog. SOC 2-oriented operational discipline is canon in [`normative-posture.md`](normative-posture.md), not a separate certification program in [`../../docs/`](../../docs/).

## Reading Model

- **Milestones** group work under a single intent and progression step.
- **Features** inside a milestone describe observable capabilities. Each feature is catalogued in [`features-and-phases.md`](features-and-phases.md) with full traceability to specs and tests.
- This document is the current truth; it evolves and is never a historical log.

## Milestones Overview

| Milestone | Intent | Status | Primary outcomes |
| --- | --- | --- | --- |
| `P0-foundations` | Establish the core cryptographic MFA protocol end to end. | `implemented` | Enrollment and authentication flows, backend trust model, mobile reference app. |
| `P1-operability` | Make the product practical to operate and administer. | `in-progress` | Admin UI workflows, admin lifecycle, tenant management, auditability. |
| `P2-hardening` | Strengthen operational posture and security observability. | `in-progress` | Encryption key rotation, audit-chain integrity, rate limiting, RFC 9457 error model. |
| `P3-distribution` | Broaden integration and distribution paths. | `planned` | Integration API maturity, API key lifecycle, SDK/CLI, public static site. |
| `P4-compliance-readiness` | Operational discipline as a quality (SOC 2 as vocabulary, not certification). | `planned` | Provisioning and deprovisioning procedures, recovery-code lifecycle, audit artifacts. |

Milestone identifiers are stable; adding a milestone appends a new entry. Reordering requires explicit decision records in [`architecture-decisions.md`](architecture-decisions.md).

## Milestone Detail

### Milestone `P0-foundations` — Core Protocol

**Intent.** Prove that a backend-first cryptographic MFA protocol can be defined, implemented, and operated end to end.

**Status.** `implemented`.

**Key features.**

- `F-enrollment-bind-verify` — cryptographically linked enrollment flow.
- `F-auth-pending-respond` — cryptographically linked authentication flow.
- `F-mobile-reference-app` — native device participation in the protocol.
- `F-admin-api-core` — administrative surface for integrations and enrollments.

**Exit criteria.**

- Enrollment and authentication flow end to end with signed payloads.
- Self-hosted local stack runs cleanly.
- Mobile reference app supports bind, verify, pending, and respond.

### Milestone `P1-operability` — Day-To-Day Administration

**Intent.** Give operators the ability to manage integrations, enrollments, admins, and tenants with confidence and clear lifecycle rules.

**Status.** `in-progress`.

**Key features.**

- `F-admin-ui-shell` — authenticated Admin UI shell with role-aware navigation.
- `F-admin-ui-workflows` — integrations, enrollments, admins, API keys, tenants.
- `F-admin-lifecycle` — admin identity lifecycle with passwordless login and activation.
- `F-tenant-lifecycle` — active/inactive tenant governance with system-tenant protection.

**Exit criteria.**

- All primary operator workflows available in the Admin UI with audit coverage.
- Lifecycle rules are consistent across entities and described in [`lifecycle-model.md`](lifecycle-model.md).

### Milestone `P2-hardening` — Security and Observability

**Intent.** Improve operational posture, secret handling, and error surface.

**Status.** `in-progress`.

**Release-order compass (September 2026 target):**
[`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md).
Integrity cluster (`I-2026-0005`–`0007`) precedes Alerts UI polish.

**Key features.**

- `F-encryption-key-rotation` — primary key rotation and re-encryption batches.
- `F-audit-chain` — audit log integrity, lifecycle observability, and heartbeat supervision.
- `F-rfc9457-errors` — structured Problem Details error model across Admin API and Auth API.
- `F-rate-limiting` — protection against abuse on login, pending, and respond endpoints.

**Exit criteria.**

- Encryption key rotation is safe, observable, and reversible.
- Audit chain verification and incident declarations are exposed to operators.
- Error contract is uniform and clients can localize through stable problem types.

### Milestone `P3-distribution` — Integration and Distribution

**Intent.** Make Ezkey easy to adopt from integrating applications and distribute its surfaces cleanly.

**Status.** `planned`.

**Key features.**

- `F-integration-api-maturity` — stable Integration API for M2M workflows.
- `F-api-key-lifecycle` — full API key lifecycle and rotation discipline.
- `F-public-site` — public-facing static site and onboarding material.
- `F-sdk-and-cli-growth` — growth of SDK and CLI surfaces.

### Milestone `P4-compliance-readiness` — Operational Discipline

**Intent.** Raise operational discipline as a product quality: documented provisioning, recovery-code lifecycle, and stable audit artifacts. SOC 2 Trust Service Criteria may be used as mapping vocabulary. This milestone is not a Comply setup, mock audit, or Type I/II engagement. Current funded work remains the [September operable-release compass](operational-readiness-prioritization-2026-09.md), not P4-as-certification. See [`normative-posture.md`](normative-posture.md).

**Status.** `planned`.

**Key features.**

- `F-provisioning-procedures` — formal admin provisioning and deprovisioning procedure.
- `F-recovery-codes-lifecycle` — recovery code lifecycle alignment.
- `F-audit-artifacts` — stable audit artifacts for review and export.

## Long-Term Horizon

These themes shape decisions without being scheduled milestones yet:

- Ecosystem growth around SDKs and integration patterns.
- Mobile platform hardening (iOS parity with Android secure storage).
- Operational telemetry and monitoring presets.
- Broader third-party and OSS collaboration surface.

## Related Documents

- [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md) — **current release-order compass** (September 2026 target)
- [`product-intent.md`](product-intent.md)
- [`normative-posture.md`](normative-posture.md) — SOC 2-oriented discipline, not certification
- [`features-and-phases.md`](features-and-phases.md)
- [`architecture-overview.md`](architecture-overview.md)
- [`spec-test-traceability.md`](spec-test-traceability.md)
