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
| [ADR-0006](#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519) | Dual signing algorithms: EC P-256 device, Ed25519 integration | accepted | 2025-07-20 |
| [ADR-0007](#adr-0007-proof-token-storage-hash-only-where-protocol-allows) | Proof token storage: hash-only where protocol allows | accepted | 2026-07-06 |

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

## ADR-0006 — Dual signing algorithms: EC P-256 device, Ed25519 integration

### Metadata

- **ID:** ADR-0006.
- **Date:** 2025-07-20.
- **Status:** accepted.
- **Scope:** global.
- **Owners:** Platform architecture.

### Context

Early Ezkey enrollment used RSA-2048, then Ed25519 for both integration and device keys. Mobile clients initially signed with Ed25519 in software, which did not map cleanly to platform keystore best practices (Android Keystore / StrongBox, iOS Keychain). Device signing was moved to **EC P-256 (secp256r1) with ECDSA-SHA256** for hardware-backed key isolation.

Integration keys are **server-held** (encrypted at rest on the backend). They sign bind, pending, and respond-result payloads that the mobile **verifies** but does not generate. Uniformly adopting EC P-256 for integration would add ECDSA wire complexity (DER signatures, low-S normalization) without keystore benefit.

### Decision

The protocol uses **two signing contexts**:

- **Device** (per enrollment, platform keystore): **EC P-256 / ECDSA-SHA256** — SPKI public key, DER signatures (standard Base64 on the wire).
- **Integration** (per enrollment, server-held): **Ed25519** — raw 32-byte public key and raw 64-byte signatures (**Base64URL without padding** on the wire). Phase 1 requires `integrationKeyAlgorithm: "ed25519"`.

Canonical wire formats and payload rules live in [`../../docs/CRYPTO.md`](../../docs/CRYPTO.md), [`../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`](../../docs/ENROLLMENT_SIGNATURE_PAYLOAD.md), and [`../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`](../../docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md).

### Alternatives Considered

- **Ed25519 for both device and integration.** Rejected for device — poor fit with native mobile keystore signing paths at the time of the decision; device keys must stay non-exportable in platform secure storage.
- **EC P-256 for both device and integration.** Rejected for integration — no keystore gain on the server; loses compact Ed25519 wire format and reintroduces ECDSA interoperability concerns on every integration-signed JSON field.
- **Single algorithm everywhere for narrative simplicity.** Rejected — conflates two trust boundaries (device participant vs integrating backend) and optimizes the wrong layer.

### Consequences

- **Positive.** Device keys align with platform secure storage; integration signatures stay compact, deterministic, and JDK-native; mobile verifies integration Ed25519 without holding integration private keys.
- **Negative.** Operators and integrators must understand two algorithm columns in protocol docs; SDKs and tests must implement both paths.
- **Neutral.** Both families are pre-quantum elliptic-curve schemes; post-quantum migration would be a separate, protocol-wide effort.

### Impact

- All components involved in enrollment and authentication.
- [`F-enrollment-bind-verify`](features-and-phases.md#f-enrollment-bind-verify), [`F-auth-pending-respond`](features-and-phases.md#f-auth-pending-respond).
- Component: [mobile ADR-MOB-0002](../components/mobile/design-decisions.md#adr-mob-0002-ec-p256-keys-on-native-keystore), [mobile ADR-MOB-0003](../components/mobile/design-decisions.md#adr-mob-0003-fail-closed-on-signature-checks).

### Related Decisions

- Refines [ADR-0001](#adr-0001-backend-first-cryptographic-protocol) with concrete algorithm choices.
- Device keystore detail: [ADR-MOB-0002](../components/mobile/design-decisions.md#adr-mob-0002-ec-p256-keys-on-native-keystore).
- Fail-closed integration algorithm check: [ADR-MOB-0003](../components/mobile/design-decisions.md#adr-mob-0003-fail-closed-on-signature-checks).

## ADR-0007 — Proof token storage: hash-only where protocol allows

### Metadata

- **ID:** ADR-0007.
- **Date:** 2026-07-06.
- **Status:** accepted (Tier 0 execution via `TB-2026-07-06-device-proof-token-hash-only`).
- **Scope:** global (persistence + protocol).
- **Owners:** Platform architecture / security.

### Context

Proof tokens on `ezkey_enrollment` and `ezkey_auth_attempt` are stored as **Tink-encrypted ciphertext
plus SHA-256 hash**. Lookups and uniqueness checks already use hashes. A 2026 security protocol
audit questioned whether recoverable storage is necessary for all token types, especially after
encryption was found disabled in one environment. Admin bearer sessions already use **hash-only**
storage (`bearer_token_hash`), matching the pattern: the server verifies `hash(incoming)` without
ever storing recoverable secrets.

The session-token analogy applies only when the server **never re-issues** the secret and does not
need plaintext to rebuild canonical signature strings. Auth attempt **pending** must return
`authAttemptProofToken` to the device (pull model); **respond** verifies `{proofToken}|{accepted}`
using the stored server-side token because the device sends only a signature, not the plaintext token.

### Decision

Adopt a **tiered storage policy**:

| Column | Storage | Rationale |
| --- | --- | --- |
| `device_proof_token` | **Hash only** (`device_proof_token_hash`) | Client-generated; server never reads stored plaintext; replay via hash uniqueness only |
| `enrollment_proof_token` | **Encrypted + hash** (unchanged for now) | Admin re-read (QR, GET), verify/respond integration signing still decrypt from DB; Tier 1 optional program |
| `auth_attempt_proof_token` | **Encrypted + hash** (unchanged) | Server must deliver token on pending and rebuild respond payloads without client echo |

Execute Tier 0 immediately pre-production. Defer Tier 1 (enrollment show-once + verify request field)
and Tier 2 (auth attempt protocol v2) to separate backlog slices under `I-2026-0032`.

Use **SHA-256 hex** (via `SensitiveDataHasher`) for proof-token hashes — same as bearer tokens —
not bcrypt, because lookup is by the token value itself.

### Alternatives Considered

- **Hash-only for all proof tokens now.** Rejected — breaks pending delivery and respond verification
  for `auth_attempt_proof_token`; breaks Admin QR/GET for enrollment without product/protocol changes.
- **Keep dual storage for device proof token.** Rejected for Tier 0 — no read path exists; SEC-007
  already recommends hash-only; zero client impact.
- **Deterministic token derivation instead of storage.** Rejected for auth attempt tokens — server
  secret compromise exposes all attempts; random per-attempt tokens remain preferred.

### Consequences

- **Positive.** Tier 0 removes one encryptable column from breach and re-encryption scope; aligns
  device proof token with bearer-token security posture; documents explicit limits of hash-only pattern.
- **Negative.** Asymmetric storage rules require operator/docs clarity; `I-2026-0029` must drop
  `device_proof_token` from indexed key-id targets.
- **Neutral.** Enrollment and auth attempt proof tokens remain in Tink rotation until Tier 1/2.

### Impact

- **Affected components:** `core`, re-encryption subsystem, `auth-api` (behavior unchanged).
- **Affected features:** [`F-auth-pending-respond`](features-and-phases.md#f-auth-pending-respond).
- **Backlog:** [`I-2026-0032`](backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md),
  [`TB-2026-07-06`](backlog/TB-2026-07-06-device-proof-token-hash-only.md).

### Validation

- Tier 0: see [`TSP-2026-07-06-device-proof-token-hash-only.md`](backlog/test-plans/TSP-2026-07-06-device-proof-token-hash-only.md).
- Manual: clean-start → pending claim → respond; DB hash present, no `device_proof_token` column.

### Related Decisions

- Admin bearer hash-only analysis: [`.cursor/plans/bearer_token_hash_storage_analysis.plan.md`](../../.cursor/plans/bearer_token_hash_storage_analysis.plan.md).
- Dual signing payloads: [ADR-0006](#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519).
- Incubation source: [`.cursor/plans/proof_token_hash-only_storage.plan.md`](../../.cursor/plans/proof_token_hash-only_storage.plan.md).
