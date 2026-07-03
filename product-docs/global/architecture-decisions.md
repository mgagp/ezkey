# Architecture Decisions

## Purpose

This document is the canonical log of architecture and design decisions at the **global** scope for Ezkey. Component-scoped decisions live in each component's `design-decisions.md`.

Each decision is recorded with enough context to be understood years later: why it was taken, what was considered, and what consequences it has. Superseded decisions stay in place with a pointer to the newer ADR; they are not deleted.

## How to Add an ADR

1. Copy [`../templates/architecture-decision.template.md`](../templates/architecture-decision.template.md).
2. Assign the next sequential identifier (`ADR-XXXX`). Identifiers are stable and never reused.
3. Append the instantiated ADR as a new `##` section in this document.
4. Cross-reference it from the affected component packs and from [`features-and-phases.md`](features-and-phases.md) when relevant.

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| [ADR-0001](#adr-0001-backend-first-cryptographic-protocol) | Backend-first cryptographic protocol identity | accepted | 2025-06-01 |
| [ADR-0002](#adr-0002-user-initiated-mobile-polling) | User-initiated mobile polling | accepted | 2025-06-01 |
| [ADR-0003](#adr-0003-rfc9457-as-external-error-contract) | RFC 9457 Problem Details as external error contract | accepted | 2025-09-01 |
| [ADR-0004](#adr-0004-lifecycle-without-persistent-cascade) | Lifecycle without persistent downstream cascade | accepted | 2026-01-15 |
| [ADR-0005](#adr-0005-audit-log-hmac-badge-detail-session-sync) | Audit log HMAC badge — detail verify seeds list session | accepted | 2026-07-03 |

## ADR-0001 — Backend-first cryptographic protocol

### Metadata

- **ID:** ADR-0001.
- **Date:** 2025-06-01.
- **Status:** accepted.
- **Scope:** global.
- **Owners:** Platform architecture.

### Context

Several established MFA models are deeply coupled to browser-centric standards (FIDO2, WebAuthn, passkeys) with their own ecosystems, assumptions, and integration overhead. Ezkey is aimed at teams that want explicit backend control over authentication state and cryptographic verification, without inheriting ecosystem complexity they do not need.

### Decision

Ezkey defines and documents its own cryptographic MFA protocol, with the backend as the authoritative source of state and verification, and a mobile app participating as a secure device. No claim of passkey or WebAuthn equivalence is made.

### Alternatives Considered

- **Build as a WebAuthn-compatible layer.** Rejected — introduces browser-centric assumptions and ecosystem coupling that work against the product thesis.
- **Adopt an existing open-source MFA stack.** Rejected — none offered the backend-first, explicitly self-hostable, cryptographically transparent posture required.

### Consequences

- **Positive.** Clear trust model, simpler to document and reason about, stays inspectable and self-hostable.
- **Negative.** No out-of-the-box interoperability with browser-based passkey ecosystems.
- **Neutral.** Mobile secure storage may still be leveraged as a building block.

### Impact

- All components.
- [`F-enrollment-bind-verify`](features-and-phases.md#f-enrollment-bind-verify), [`F-auth-pending-respond`](features-and-phases.md#f-auth-pending-respond).

### Related Decisions

- Complements [ADR-0002](#adr-0002-user-initiated-mobile-polling).

## ADR-0002 — User-initiated mobile polling

### Metadata

- **ID:** ADR-0002.
- **Date:** 2025-06-01.
- **Status:** accepted.
- **Scope:** global.
- **Owners:** Mobile + Auth API.

### Context

Push-based MFA flows are vulnerable to push fatigue attacks and introduce complexity in mobile background behavior. The product thesis is to keep the mobile app simple and the protocol explicit.

### Decision

The mobile application polls for pending authentication attempts only on explicit user actions. No background polling is performed. The `pending` endpoint enforces rate limits and requires a signed request body.

### Alternatives Considered

- **Automatic periodic polling.** Rejected — creates push-fatigue equivalents, battery cost, and unnecessary complexity.
- **Server push through platform push services.** Rejected — re-introduces dependency on opaque third-party platform services.

### Consequences

- **Positive.** Prevents push fatigue, simplifies the mobile architecture, keeps the protocol explicit.
- **Negative.** Users must explicitly trigger the check; this is an acceptable trade-off for the product's audience.

### Impact

- [components/mobile](../components/mobile/README.md).
- [`F-auth-pending-respond`](features-and-phases.md#f-auth-pending-respond).

## ADR-0003 — RFC 9457 as external error contract

### Metadata

- **ID:** ADR-0003.
- **Date:** 2025-09-01.
- **Status:** accepted.
- **Scope:** global.
- **Owners:** Admin API + Auth API.

### Context

Error contracts were drifting across Admin API and Auth API, mixing free-text messages with ad hoc JSON payloads. Clients (Admin UI, Mobile) had to translate and localize without a stable branching key.

### Decision

All 4xx and 5xx responses from Admin API and Auth API use **RFC 9457 Problem Details** (`application/problem+json`) with `type`, `title`, `status`, `detail`, and optional extensions `path`, `parameters`. Clients branch on `type` and HTTP status; dynamic localization uses `parameters`.

### Alternatives Considered

- **Keep free-form JSON error payloads.** Rejected — no stable branching surface for clients.
- **Adopt a custom error envelope.** Rejected — reinvents a standard.

### Consequences

- **Positive.** Uniform error model, stable client branching, clean localization story.
- **Negative.** Migration work to convert legacy error paths.
- **Neutral.** A few endpoints still return business-level outcomes in a 200 body (e.g. `respond` with `FAILED`).

### Impact

- [components/admin-api](../components/admin-api/README.md), [components/admin-ui](../components/admin-ui/README.md), [components/mobile](../components/mobile/README.md).
- [`F-rfc9457-errors`](features-and-phases.md#f-rfc9457-errors).

## ADR-0004 — Lifecycle without persistent downstream cascade

### Metadata

- **ID:** ADR-0004.
- **Date:** 2026-01-15.
- **Status:** accepted.
- **Scope:** global.
- **Owners:** Platform architecture.

### Context

The product handles hierarchical entities (tenants, integrations, enrollments, API keys, admins) with operator-facing actions that can be reversible or irreversible. A naive cascade model (deactivating a tenant flips child entities to inactive in storage) creates reconciliation problems on reactivation and breaks audit truthfulness.

### Decision

Lifecycle state changes on a parent entity do **not** cascade into child entity storage. Instead, the system evaluates an **eligibility chain** at runtime that blocks operations when any ancestor is non-operational. Reactivating a parent immediately restores all locally healthy descendants without reconciliation.

### Alternatives Considered

- **Cascade state changes on deactivation.** Rejected — creates reconciliation cost on reactivation and muddies the audit story.
- **Mixed cascade with rollback tables.** Rejected — unnecessary complexity, poor operator clarity.

### Consequences

- **Positive.** Clean reversibility, truthful audit, no reconciliation debt.
- **Negative.** Operators must understand that local state and effective operational state can differ; UI must surface that clearly.

### Impact

- All components involved in lifecycle operations.
- [`lifecycle-model.md`](lifecycle-model.md).
- [`F-tenant-lifecycle`](features-and-phases.md#f-tenant-lifecycle), [`F-admin-lifecycle`](features-and-phases.md#f-admin-lifecycle).

### Related Decisions

- Further detail in the legacy [`../../docs/LIFECYCLE_GOVERNANCE.md`](../../docs/LIFECYCLE_GOVERNANCE.md).

## ADR-0005 — Audit log HMAC badge — detail verify seeds list session

### Metadata

- **ID:** ADR-0005.
- **Date:** 2026-07-03.
- **Status:** accepted.
- **Scope:** Admin UI — audit log integrity investigation.
- **Owners:** Integrity cluster (B2.5 / B2.6).

### Context

After entry-integrity conciliation, the investigation session is cleared on reconcile success (honest
reset). The paginated audit list then shows a neutral gray **Signed** badge until a full-range Verify
pass repopulates the session. Operators who open **Detail** see a live single-entry integrity check,
but closing Detail left the list unchanged — implying the row was never verified or that conciliation
had no effect. In a PME posture Ezkey must make security decisions obvious without requiring a
second full-range action for every row.

### Decision

1. **Detail dialog** uses the shared `EntryHmacBadge` states (`violation`, `violationExplained`,
   `violationRetamper`, `verified`) driven by `GET …/audit-logs/{id}/integrity-check` including
   `conciliationStatus` — not a binary intact / violation label.
2. **On successful single-entry verify**, merge the result into `IntegrityInvestigationSession`
   (`mergeSingleEntryVerificationIntoSession`) so the list column updates immediately when Detail
   closes (React state + `sessionStorage`).
3. **Reconcile still clears** the full session; detail-seeded sessions are **manual** scope and do
   not replace panel Verify for range-wide investigation.
4. **No false Explained badge** without a verify response: gray **Signed** remains the default when
   neither session nor detail check has run.

### Alternatives Considered

- **Require panel Verify only after reconcile.** Rejected — high friction; contradicts operator
  mental model after viewing Detail.
- **Persist conciliation posture in list API DTOs.** Rejected for R1 — verify-time lookup keeps
  crypto truth on the integrity endpoints; session cache is the UI bridge.

### Consequences

- **Positive.** List and Detail stay aligned; conciliated rows show amber **Explained** after one
  Detail open; security posture is visible without memorizing session-cache rules.
- **Negative.** Session may contain entry-scoped data without a formal range window until the next
  panel Verify; acceptable for operator UX.
- **Neutral.** Logout and reconcile success still clear the session.

### Impact

- `ezkey-admin-ui`: `integrity-investigation-session.ts`, `audit-logs.tsx`, `entry-hmac-badge.tsx`.
- [`TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md`](backlog/TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md) D5 UI.

### Related Decisions

- Extends B2.5 investigation session cache; refines post-reconcile UX from B2.6 D5.

