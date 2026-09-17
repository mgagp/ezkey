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
| [ADR-0008](#adr-0008-tink-keyset-sync-concurrent-read-path) | Tink keyset sync: concurrent read path for at-rest encryption | accepted | 2026-07-09 |
| [ADR-0009](#adr-0009-detective-integrity-windows-align-to-checkpoint-grid) | Detective integrity windows align to checkpoint grid | accepted | 2026-07-10 |
| [ADR-0010](#adr-0010-rate-limiting-scoped-by-actor-identity-not-by-ip) | Rate limiting scoped by actor identity, not by IP, across device and M2M surfaces | accepted | 2026-05-08 |
| [ADR-0011](#adr-0011-tink-native-database-keyset-envelope) | Tink-native database keyset envelope | accepted | 2026-08-02 |
| [ADR-0012](#adr-0012-admin-owned-keyset-materialization) | Admin-owned keyset materialization | accepted | 2026-08-28 |
| [ADR-0013](#adr-0013-admin-role-admin-umbrella-keep-vs-split) | Admin `ROLE_ADMIN` umbrella — keep vs split | accepted | 2026-09-16 |

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

- Tier 0: **executed** 2026-07-07 (PR `#297` / issue `#296`); see
  [`TSP-2026-07-06-device-proof-token-hash-only.md`](backlog/test-plans/TSP-2026-07-06-device-proof-token-hash-only.md)
  and TB closeout on [`TB-2026-07-06-device-proof-token-hash-only.md`](backlog/TB-2026-07-06-device-proof-token-hash-only.md).
- Manual: clean-start → pending claim → respond; DB hash present, no `device_proof_token` column.

### Related Decisions

- Admin bearer hash-only precedent: shipped `bearer_token_hash` (Flyway V7), `AdminToken` lookup by
  SHA-256; Crypto API `POST /api/v1/crypto/hash-token` — see § Context above.
- Dual signing payloads: [ADR-0006](#adr-0006-dual-signing-algorithms-device-ec-p256-integration-ed25519).
- Incubation materialized (deleted Cursor plan): [`I-2026-0032`](backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md),
  [`TB-2026-07-06`](backlog/TB-2026-07-06-device-proof-token-hash-only.md).
- Concurrent Tink keyset access: [ADR-0008](#adr-0008-tink-keyset-sync-concurrent-read-path).
- Detective integrity windows: [ADR-0009](#adr-0009-detective-integrity-windows-align-to-checkpoint-grid).

## ADR-0008 — Tink keyset sync: concurrent read path for at-rest encryption

### Metadata

- **ID:** ADR-0008.
- **Date:** 2026-07-09.
- **Status:** accepted.
- **Scope:** global (core encryption / `TinkKeyManager`).
- **Owners:** Platform architecture / security.

### Context

Every at-rest encrypt/decrypt path goes through `TinkKeyManager.getAeadPrimitive()` (via
`EncryptionEntityListener` → `EncryptionService`). In `DATABASE` / `HYBRID` storage modes the
manager also periodically checks whether the keyset version changed (rotation or multi-instance
sync) and may reload the in-memory keyset.

A 2026 security challenge (findings F-07-B / F-10-B, backlog **SEC-009**) observed that
`getAeadPrimitive()` was **method-level `synchronized`**, and that the throttled DB version check
ran **inside** that exclusive lock. Under concurrent entity access — especially during background
re-encryption — all encrypt/decrypt threads serialized on one mutex, including while waiting on a
DB round-trip. This is an **Insecure Design / concurrency** issue (OWASP A04): keyset sync was
coupled to the hot path, degrading latency under load without changing cryptographic correctness.

### Decision

Decouple keyset synchronization from the encrypt/decrypt hot path:

1. Protect `keysetHandle` with a **`ReentrantReadWriteLock`**: concurrent **read** locks for
   `getAeadPrimitive()` / read-only keyset queries; exclusive **write** locks for reload, rotation,
   and keyset mutation.
2. Schedule throttled keyset version checks (default interval 5 s) on a **daemon background
   thread**, outside the read lock. Acquire the write lock only when a newer version is confirmed
   (double-check under write lock before reload).
3. Keep the existing **`CHECKING_DATABASE` ThreadLocal** guard to prevent JPA listener recursion
   during version checks.
4. Allow re-entrant use when the calling thread already holds the write lock (rotation that
   triggers entity listeners must not deadlock).

Cryptographic formats, storage modes (`FILE` / `DATABASE` / `HYBRID`), and
`ezkey.encryption.required` fail-fast (SEC-002) are unchanged.

### Alternatives Considered

- **Keep method-level `synchronized`.** Rejected — serializes all encrypt/decrypt on periodic DB
  checks; root cause of SEC-009.
- **Synchronous version check under the read lock.** Rejected — still blocks concurrent readers
  during DB I/O; only slightly better than a full mutex.
- **External scheduler / separate sync component outside `TinkKeyManager`.** Rejected for this
  slice — adds accidental complexity; the manager already owns keyset lifecycle and the ThreadLocal
  recursion guard.

### Consequences

- **Positive.** Encrypt/decrypt threads proceed concurrently; DB version checks no longer sit on
  the hot-path critical section; reload briefly pauses readers only when the keyset actually
  changes.
- **Negative.** Slightly more locking surface to reason about (read vs write, async check,
  re-entrance); operators must not assume "every `getAeadPrimitive()` call synchronously refreshes
  the keyset."
- **Neutral.** Throttle interval and storage-mode semantics remain as configured; no ciphertext or
  Flyway change.

### Impact

- **Affected components:** `ezkey-core-security` (`TinkKeyManager`), all APIs that encrypt at rest.
- **Affected features:** [`F-encryption-key-rotation`](features-and-phases.md#f-encryption-key-rotation).
- **Security audit:** SEC-009 / F-07-B / F-10-B in
  [`../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md`](../../docs/SECURITY_CHALLENGE_REPORT_2026-06.md);
  GitHub issue `#316`.
- **Config pointer:** [`../../ezkey-core/CONFIGURATION.md`](../../ezkey-core/CONFIGURATION.md)
  (Encryption at Rest).

### Validation

- Unit: `TinkKeyManagerConcurrencyTest` (concurrent readers with slow `findVersion`; DB version
  bump reload; concurrent readers during `addKeyWithoutPromotion`).
- Elective E2E spot-check: `ReencryptionFullTriggerConcurrentActivityElectiveTest` (re-encryption
  under enrollment churn) via `ezkey-tests` profile `elective-tests`.

### Related Decisions

- Proof-token storage tiers (related Tink surface): [ADR-0007](#adr-0007-proof-token-storage-hash-only-where-protocol-allows).
- Read-path fail-closed when `encryption.required=true` (orthogonal follow-up):
  [`I-2026-07-09`](backlog/ideas/I-2026-07-09-encryption-required-read-path-parity.md).
- Detective integrity windows: [ADR-0009](#adr-0009-detective-integrity-windows-align-to-checkpoint-grid).

## ADR-0009 — Detective integrity windows align to checkpoint grid

### Metadata

- **ID:** ADR-0009.
- **Date:** 2026-07-10.
- **Status:** accepted.
- **Scope:** global (audit integrity / detective layer).
- **Owners:** Platform architecture / integrity cluster.

### Context

Wave B shipped a two-layer integrity model: rolling **attach** (5-minute checkpoints) and nightly
**detect** (retroactive HMAC + chain verification). On EXP1, the nightly batch raised daily CRITICAL
`AUDIT_INTEGRITY_RUPTURE` alerts with zero entry and zero chain crypto violations. Investigation
showed the detective window used wall-clock `now()` (sub-second precision) while checkpoints sit on
exact grid boundaries, so `findByWindowRange` excluded the first aligned checkpoint and reported a
leading undeclared gap. The alert payload and Admin UI summarized only crypto violation counts,
producing a CRITICAL "0 + 0" signal that operators correctly treated as unfounded.

The original design was right to treat undeclared gaps as integrity failures for forensic honesty.
It under-specified that **scheduled detective windows must share the checkpoint time base**, and
that **operator-facing rupture alerts must distinguish coverage findings from crypto violations**.

### Decision

1. **Grid alignment:** Scheduled retroactive integrity validation computes
   `[windowStart, windowEnd)` from a `windowEnd` rounded down to the configured checkpoint window
   boundary (reuse `AuditChainScheduler.roundDownToWindow` / same `windowMinutes`). Only completed
   grid windows are in scope for the default nightly pass.
2. **Alert honesty:** `AUDIT_INTEGRITY_RUPTURE` payloads and list summaries must not imply "no
   findings" when `chainStatus` is `UNDECLARED_GAP_DETECTED` (or equivalent). Undeclared-gap signal
   is first-class in operator-facing text, separate from entry/chain crypto counts.
3. **Taxonomy guard:** Do not raise manipulation-family CRITICAL alerts solely for **boundary
   artifacts** of the scheduled scan (misaligned `now()`, or "last sealed window not yet persisted"
   at the exact cron instant). Interior undeclared gaps and crypto failures remain alert-eligible.
4. **Scheduler contract:** Document that chain attach (`1 */5 …`) and nightly detect (`0 0 2 …`)
   are coupled; prefer validating sealed windows and/or a small cron margin after the hour.

### Alternatives Considered

- **Config-only cron shift (e.g. 02:10).** Rejected as sole fix — reduces race odds but leaves
  sub-second exclusion and "0 + 0" CRITICAL UX intact.
- **Never alert on undeclared gaps.** Rejected — weakens detective honesty for real interior gaps.
- **New alert type for boundary gaps.** Deferred — alignment + payload honesty is enough for R1;
  a separate type can be revisited if operators still need a distinct queue lane.

### Consequences

- **Positive.** Healthy instances stay quiet; CRITICAL integrity alerts regain trust; future
  integrity jobs inherit an explicit time-base rule.
- **Negative.** Slightly less "up to the millisecond" coverage at the trailing edge of the nightly
  window (by design — that edge is not yet a sealed checkpoint).
- **Neutral.** Historical false-positive OPEN alerts on EXP1 may need manual resolve.

### Impact

- **Affected components:** `core` (schedulers, verification orchestration), `admin-api`,
  `admin-ui` (alert summary), docs.
- **Affected features:** [`F-audit-chain`](features-and-phases.md#f-audit-chain).
- **Backlog:** [`I-2026-07-10-nightly-integrity-boundary-false-positives`](backlog/ideas/I-2026-07-10-nightly-integrity-boundary-false-positives.md),
  method log [`ML-2026-07-09`](backlog/method-logs/ML-2026-07-09-exp1-nightly-integrity-boundary-false-positives.md).
- **Design pack:** [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md) pitfall note.

### Validation

- Unit: sub-second window end does not produce leading undeclared gap on a contiguous checkpoint grid.
- Regression: real interior gap and digest mismatch still raise/touch `AUDIT_INTEGRITY_RUPTURE`.
- EXP1: after deploy, next nightly run leaves no new zero-violation rupture alert.
- **Execution:** PR `#318` / issue `#315` merged and closed 2026-07-11; backlog idea
  `I-2026-07-10-nightly-integrity-boundary-false-positives` marked `done` (canon synced 2026-08-02).

### Related Decisions

- Integrity cluster design: [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md).
- Vision: [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md).
- Parent delivery: `I-2026-0006` / nightly batch B1.
- Tink keyset concurrent read path: [ADR-0008](#adr-0008-tink-keyset-sync-concurrent-read-path).

## ADR-0010 — Rate limiting scoped by actor identity, not by IP

### Metadata

- **ID:** ADR-0010.
- **Date:** 2026-05-08.
- **Status:** accepted.
- **Scope:** global (Auth API, Admin API, Integration API rate-limit surfaces).
- **Owners:** Platform architecture.

### Context

Ezkey exposes rate-limited surfaces to three distinct kinds of caller: a mobile device polling and
responding to its own auth attempts, an integration (M2M) using an API key that is shared across many
end users, and an admin session performing sensitive mutations or logging in. A single client IP is
not a safe key for two of these three: an integration's API key is used by a backend server on behalf
of many different end users, so every one of those users' auth-attempt creations shares the same
egress IP — an IP-keyed bucket would throttle unrelated users together, or (behind shared NAT/load
balancers) throttle unrelated integrations together. Grill session
[`blitz-2026-05-08-2-D1`](backlog/grill-sessions/blitz-2026-05-08-2-D1-rate-limit-registry-grill-me.md)
and backlog analysis [`I-2026-0008`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) also
found no single numeric baseline that fits device polling, integration throughput, and admin
mutations at once — forcing one band would misrepresent product semantics.

### Decision

Rate limiting is organized into four policy families, each keyed by the identity that actually
identifies the abusive actor for that surface, not by client IP alone:

| Family | Audience | Key dimension |
| --- | --- | --- |
| **D — Device auth** | Mobile / device | `enrollment-id`, `auth-attempt-id`, or `client-ip` for anonymous pre-enrollment calls |
| **I — Integration throughput** | API key (M2M) | Per API key id |
| **A — Admin sensitive ops** | Admin session | Per admin id or recovery token |
| **L — Admin login** | Public admin auth | Client IP (no authenticated identity exists yet at this surface) |

No single global numeric baseline is defined. Each new public or high-abuse endpoint is assigned one
of these four families and its own `CONFIGURATION.md`-documented property prefix instead of a shared
constant. The full inventory of current surfaces, defaults, and per-profile overrides is maintained
in [`rate-limit-baseline-policy.md`](rate-limit-baseline-policy.md) — that document is the living
inventory; this ADR records why IP is deliberately not the default key and why no single baseline was
adopted.

### Alternatives Considered

- **One global numeric baseline (e.g. N requests / window per IP) applied everywhere.** Rejected —
  misrepresents product semantics; would either be too loose for `respond` (critical, 1/5 min) or too
  strict for legitimate integration throughput.
- **Key every surface by client IP for simplicity.** Rejected — breaks for API key (M2M) traffic,
  where many end users share one integration's egress path; also fragile behind shared NAT.
- **Unify `RateLimitService` implementations across Admin API and Integration API into one shared
  module.** Deferred, not rejected — the known duplication (both non-distributed, Caffeine-backed) is
  documented, but no measurable simplification was found to justify the refactor at R1 (`#1`
  pragmatism — no refactor for refactor's sake).

### Consequences

- **Positive.** Each surface's abuse-resistance key matches who can actually abuse it; integration
  traffic scales per key, not per shared IP; admin login retains IP-based brute-force resistance where
  no authenticated identity exists yet.
- **Negative.** Four families plus per-surface property prefixes are more surface area than one
  constant; operators must consult `rate-limit-baseline-policy.md` or module `CONFIGURATION.md` rather
  than a single number.
- **Neutral.** Rate-limit buckets remain per-instance (Caffeine, via Bucket4j); not distributed. Noted
  as acceptable at current scale; Redis-backed distribution is called out in Integration service
  Javadoc as a future option if scale warrants it.

### Impact

- **Affected components:** `auth-api` (`RateLimitFilter`), `admin-api` (`AdminRateLimitFilter`,
  `AdminOperationsRateLimitService`, API-key auth-attempt paths), `integration-api`
  (`RateLimitService`).
- **Affected features:** enrollment bind/verify, auth-attempt pending/respond, API key create/wait,
  admin login, admin sensitive operations (API key create/revoke/update, enrollment reset).
- **Backlog:** [`I-2026-0008`](backlog/ideas/I-2026-0008-rate-limit-baseline-analysis.md) (closed).

### Validation

- Inventory-level validation only (no code unification): the mapping of surface → family → key
  strategy is kept current in [`rate-limit-baseline-policy.md`](rate-limit-baseline-policy.md) and
  cross-checked against each module's `CONFIGURATION.md` and filter/service implementation.
- Historical incident: an unrelated, never-implemented `ezkey.admin.rate-limit.api-key.*` property
  family was found disabled/inconsistent across profiles during a later documentation-triage pass —
  see [`../../ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md`](../../ezkey-admin-api/API_KEY_RATE_LIMIT_NOTE.md).
  That incident does not change this decision; it is a config-naming collision on top of the policy
  described here.

### Related Decisions

- None yet at component scope; component packs for Auth API and Integration API do not exist yet
  (see [`../components/README.md`](../components/README.md)).

## ADR-0011 — Tink-native database keyset envelope

### Metadata

- **ID:** ADR-0011.
- **Date:** 2026-08-02.
- **Status:** accepted.
- **Scope:** global (core encryption / `TinkKeyManager` / `ezkey_keyset_blob`).
- **Owners:** Platform architecture / security.

### Context

Ezkey uses Google Tink for at-rest encryption. A file-based 256-bit master key creates the master
AEAD; the active Tink keyset produces the data-encryption AEAD used by encrypted entity fields.
After the 2026-07 Tink deprecation migration, file keysets used Tink's encrypted-keyset JSON
envelope through `TinkJsonProtoKeysetFormat`, but the database synchronization blob still used an
Ezkey-specific wrapper: serialize the cleartext keyset JSON, encrypt those bytes with the master
AEAD, and store the resulting opaque ciphertext in `ezkey_keyset_blob.keyset_data`.

That historical wrapper was cryptographically sound, but it created two persisted keyset envelope
models and made the database representation less aligned with Tink's public keyset-format facade.
Its extra opacity also risked being over-valued as a security property. The relevant security line is
that key material is secret and must remain encrypted; keyset metadata such as key IDs, primary-key
status, and output-prefix information is operational metadata and is not treated as secret in Ezkey's
threat model.

### Decision

Store `ezkey_keyset_blob.keyset_data` as Tink's encrypted-keyset JSON envelope directly, using the
existing master AEAD, empty associated data, and `RegistryConfiguration.get()` overloads. Keep the
database column as `BYTEA`, keep the file keyset format unchanged, and do not change data ciphertexts,
algorithms, master-key source, rotation ownership, or `DATABASE` / `HYBRID` reload semantics.

This is a clean-start pre-production cutover. Existing production deployments do not need a legacy
dual-read migration because there are no production installations to preserve at this point.

### Alternatives Considered

- **Keep the outer Ezkey wrapper.** Rejected for the current release line. It remained viable crypto,
  but it preserved custom envelope semantics after Tink provided a public encrypted-keyset format
  facade and left future readers to reconcile two keyset persistence models.
- **Add a second custom encryption layer around the Tink envelope.** Rejected. Hiding non-secret
  metadata would mostly move the trust problem to another wrapping key and risk security theater.
- **Add dual-read legacy compatibility.** Rejected for this pre-production cutover. Clean-start is
  acceptable, while dual-read logic would add format ambiguity and migration surface before any
  production installation exists.
- **Use contextual associated data for the DB blob.** Deferred. Empty associated data preserves the
  prior Tink file semantics and avoids backup / restore / environment-cloning brittleness. Contextual
  AD can be revisited only if a concrete binding requirement appears.

### Consequences

- **Positive.** File and database keyset persistence now use the same Tink encrypted-keyset concept;
  keyset serialization no longer carries an Ezkey-specific outer envelope; future Tink evolution is
  easier to follow; cold readers can inspect the database blob shape without inferring custom crypto
  semantics.
- **Negative.** Tink envelope metadata such as key IDs and primary-key status is visible to a reader
  with database access. Ezkey treats that as non-secret operational metadata, not as key material.
- **Neutral.** The database schema remains unchanged; application ciphertexts and re-encryption
  posture are unchanged; old outer-encrypted DB blobs are not a supported current representation.

### Impact

- **Affected components:** `ezkey-core-security` (`TinkKeyManager`), `ezkey-core`
  (`KeysetBlob`), Admin API / Auth API / Integration API in `DATABASE` or `HYBRID` storage modes.
- **Affected features:** [`F-encryption-key-rotation`](features-and-phases.md#f-encryption-key-rotation).
- **Backlog:** [`I-2026-07-26-tink-native-keyset-blob-envelope`](backlog/ideas/I-2026-07-26-tink-native-keyset-blob-envelope.md).
- **Configuration pointer:** [`../../ezkey-core/CONFIGURATION.md`](../../ezkey-core/CONFIGURATION.md)
  (Encryption at Rest / Keyset Storage).

### Validation

- Unit: `TinkKeyManagerConcurrencyTest` characterizes the Tink-native DB blob shape, visible
  envelope metadata, Tink parse round-trip, tamper rejection, legacy DB blob clean-start posture,
  and DB reload after rotation.
- Build: `mvn spotless:apply`, `mvn checkstyle:check`, `mvn clean`, and
  `mvn install -DskipTests` passed from the repository root.
- Operational smoke: `ezkey-tests/clean-start.sh --no-proxy` produced a healthy Docker stack;
  PostgreSQL inspection confirmed `encryptedKeyset` and `keysetInfo` in `ezkey_keyset_blob`; live
  smoke tests passed with `mvn test -pl ezkey-tests -P smoke-tests`.

### Related Decisions

- Tink keyset concurrent read / reload path: [ADR-0008](#adr-0008-tink-keyset-sync-concurrent-read-path).
- Proof-token storage tiers on related encrypted data surfaces: [ADR-0007](#adr-0007-proof-token-storage-hash-only-where-protocol-allows).
- Admin-owned keyset materialization: [ADR-0012](#adr-0012-admin-owned-keyset-materialization).

## ADR-0012 — Admin-owned keyset materialization

### Metadata

- **ID:** ADR-0012.
- **Date:** 2026-08-28.
- **Status:** accepted.
- **Scope:** global (core encryption / `TinkKeyManager` / `KeyRotationService` / DB roles).
- **Owners:** Platform architecture / security.

### Context

`TB-2026-07-16` split runtime PostgreSQL roles. `ezkey_encryption_key` is Admin-write /
peripheral-SELECT. Shared `KeyRotationService.initializeKeysetSync()` still attempted
`STARTUP_SYNC` inserts on Auth and Integration. `TinkKeyManager` could upsert `ezkey_keyset_blob`
from any boot API. After TX-002 moved empty-table sync onto a transactional
`ApplicationReadyEvent` listener, a denied INSERT left the transaction rollback-only and Spring
failed Auth startup with `UnexpectedRollbackException`. The July 17 observation was the same
denied INSERT with Auth still up. The crash is therefore ownership plus a transactional boundary,
not a missing grant.

### Decision

Admin API is the sole writer of `ezkey_keyset_blob` and `ezkey_encryption_key` metadata. Encode
that with `ezkey.encryption.keyset.writer=true` on Admin only (default `false`). Do not reuse
`rotation.enabled`. In DATABASE/HYBRID mode, non-writer processes poll for the blob up to
`bootstrap-wait` (default 90s) and never generate or upsert a keyset. If the blob is still missing
and `ezkey.encryption.required=true`, fail-closed. After the Java gate, peripheral roles are
SELECT-only on `ezkey_keyset_blob`. Compose `depends_on: admin-api: service_healthy` is
acceleration for clean-start, not the HA contract.

### Alternatives Considered

- **Reuse `rotation.enabled` as the writer flag.** Rejected. Admin must still seed metadata when
  scheduled rotation is off.
- **Infer writer from the JDBC role name.** Rejected. Fragile and hard to unit-test.
- **ShedLock or audit-chain heartbeat as readiness.** Rejected. Overkill or the wrong table and
  grace window.
- **Compose-only start order.** Rejected as the sole contract. Manual or HA restart of Auth must
  wait in-process.

### Consequences

- **Positive.** Grants match the trust boundary; Auth-first empty-blob boot no longer attempts
  INSERT; TX-002 fail-open catch cannot kill a peripheral; operators get a loud not-ready signal.
- **Negative.** Auth/Integration delay or fail if Admin never materializes the blob (intentional
  fail-closed when encryption is required).
- **Neutral.** ADR-0008 reload and ADR-0011 envelope shape are unchanged. FILE mode still loads an
  existing file; non-writers must not generate a missing file.

### Impact

- **Affected components:** `ezkey-core` (`TinkProperties`, `KeyRotationService`),
  `ezkey-core-security` (`TinkKeyManager`), Admin / Auth / Integration APIs, `scripts/db` grants.
- **Affected features:** [`F-encryption-key-rotation`](features-and-phases.md#f-encryption-key-rotation).
- **Backlog:** [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](backlog/ideas/I-2026-07-17-keyset-blob-admin-first-bootstrap.md),
  [`TB-2026-08-28-admin-first-keyset-bootstrap`](backlog/TB-2026-08-28-admin-first-keyset-bootstrap.md).
- **Configuration pointer:** [`../../ezkey-core/CONFIGURATION.md`](../../ezkey-core/CONFIGURATION.md)
  (Encryption at Rest / Keyset Storage).

### Validation

- Unit locks: non-writer never saves encryption-key rows; Tink non-writer never upserts or
  generates; wait-then-load with a stub that appears on the second poll.
- Forced Docker: [`scripts/repro-auth-keyset-readiness.sh`](../../scripts/repro-auth-keyset-readiness.sh)
  Auth-first negative and positive (not a lucky full-stack clean-start).
- `scripts/db/verify-grants.sh` asserts SELECT-only blob and encryption-key for Auth/Integration.

### Related Decisions

- Tink keyset concurrent read / reload path: [ADR-0008](#adr-0008-tink-keyset-sync-concurrent-read-path).
- Tink-native database keyset envelope: [ADR-0011](#adr-0011-tink-native-database-keyset-envelope).

## ADR-0013 — Admin `ROLE_ADMIN` umbrella — keep vs split

### Metadata

- **ID:** ADR-0013.
- **Date:** 2026-09-16.
- **Status:** accepted.
- **Accepted:** 2026-09-17 by Marc / Patrick.
- **Scope:** global (Admin API human authorization; Admin UI role chrome).
- **Owners:** Marc / Patrick (decision); platform architecture.
- **Decision pack:** [`decisions/2026-09-16-admin-role-umbrella/`](decisions/2026-09-16-admin-role-umbrella/README.md)
  (inventory, options A/B/C, test preamble, phasing, accepted verdict).
- **Prior hygiene:** Path A keep-umbrella settled 2026-08-20 in
  [`hygiene/java-controller-role-validation/2026-08-16-pass-1.md`](hygiene/java-controller-role-validation/2026-08-16-pass-1.md)
  (`CTRL-ROLE-002` / `002b`). This ADR elevates that call to the global decision log.

### Context

Every authenticated human admin JWT-equivalent session receives Spring authority `ROLE_ADMIN`,
then `ROLE_GLOBAL_ADMIN` or `ROLE_TENANT_ADMIN`
(`AdminTokenAuthenticationFilter`). Shared operator controllers commonly use
`@PreAuthorize("hasRole('ADMIN')")`. Tenant isolation and Global-only privilege are **not**
implied by that umbrella; they require a second check (`AccessControlService.canAccess*`,
principal tenant scope, or an explicit `GLOBAL_ADMIN` gate). SEC-017 and CTRL-ROLE-001 showed
the fail-open class when the second check is missing. Service-level `@PreAuthorize("hasRole('ADMIN')")`
would not fix isolation either — Admin API services today do not use method-security annotations
for that reason.

The product still wants a sober model for a solo-maintained lab: either keep the umbrella and
make agent discoverability fail-closed in process, or split authorities greenfield-style and
estimate the cutover.

### Decision

**Option C executing Option A:** **keep issuing `ROLE_ADMIN`**. Do not remodel filter
assignment or drop the umbrella in application code as part of this decision. Harden
discoverability and characterization tests. Treat dropping the umbrella (Option B) as an
optional later atomic peel only if isolation defects recur after that hardening, or if a new
role model is funded as an explicit program. Full rationale, reopen criteria, and phasing live
in the decision pack.

Living operational canon for cold agents remains Admin API `AGENTS.md` § Authorization at
controllers (including the 2026-08-20 Path A sentence), now backed by this accepted ADR.

### Alternatives Considered

- **A — Keep umbrella + harden discoverability.** Near-term execution path under this decision.
  Isolation remains object/list scoped; docs/rules/tests carry the load.
- **B — Split authorities greenfield-style.** Issue only type roles; annotate
  `hasAnyRole(GLOBAL,TENANT)` vs `hasRole(GLOBAL)`. Honesty gain for Global-only gates; does
  **not** replace `canAccess*`. Requires coordinated filter + ~36 annotation migration.
  Rejected for now; reopen only per pack criteria.
- **C — Hybrid / phased.** Accept A now; independent peels for 403→404 / dialects; optional
  annotation honesty; stop issuing `ROLE_ADMIN` only in an atomic final step. **Accepted
  framing** (execute A immediately).

### Consequences

- **Positive.** No risky authz cutover; elevates prior hygiene to ADR; clear reopen criteria for
  B; separates deny-shape debt from role remodel.
- **Negative.** Isolation stays opt-in per endpoint; agents that skip AGENTS can still ship
  umbrella-only get-by-id — mitigate with discoverability and characterization tests.
- **Neutral.** Admin UI continues to branch on `adminType`. Hide-existence and SQL-isolation
  workstreams remain separate.

### Impact

- **Affected components:** `ezkey-admin-api` (authz model), `ezkey-admin-ui` (role chrome only),
  `ezkey-tests` (isolation characterization).
- **Affected boundaries:** Admin human session authorities; not Auth API device protocol; not
  Integration API `ROLE_API_KEY`.
- **Living canon:** [`../../ezkey-admin-api/AGENTS.md`](../../ezkey-admin-api/AGENTS.md)
  § Authorization at controllers; [`../../docs/API_SECURITY_MATRIX.md`](../../docs/API_SECURITY_MATRIX.md)
  role story.

### Validation

- Documentary acceptance recorded 2026-09-17 (Marc / Patrick).
- Recommended follow-through (not blockers for this ADR): optional root/agent pointer; fund ACS
  Tenant Admin unit matrix; keep `TenantCrossIsolationSecurityTest` +
  `EncryptionKeyControllerSecurityWebMvcTest` green on authz-touching changes.
- If B is later funded: characterization suite green before removing `ROLE_ADMIN` from the
  filter (see decision pack test preamble).

### Related Decisions

- Hygiene Path A (2026-08-20) is provenance for this acceptance, not a separate ADR.
- Orthogonal: encryption key Global-only (SEC-017) remains; keyset writer ownership is
  [ADR-0012](#adr-0012-admin-owned-keyset-materialization).
