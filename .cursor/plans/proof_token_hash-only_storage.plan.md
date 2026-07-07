---
name: Proof Token Hash-Only Storage
overview: "Formal audit of proof token hash-only storage. Tier 0 (device_proof_token) materialized to product-docs 2026-07-06. Tiers 1–2 deferred under I-2026-0032."
materialized: true
materializedAt: 2026-07-06
---

# Proof Token Hash-Only Storage — Working Plan

> **Status:** Tier 0 materialized into canonical `product-docs`. This file is the retained incubation
> source and option-space record. Implementation direction for Tier 0 lives in the tracer bullet below.

## Canonical materialization

- **Materialization lane:** `Lane B` — plan incubation → canonical `product-docs`.
- **Status:** materialized on `2026-07-06`.
- **Backlog idea:** [`product-docs/global/backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md`](../product-docs/global/backlog/ideas/I-2026-0032-proof-token-hash-only-storage.md)
- **Tracer bullet (Tier 0):** [`product-docs/global/backlog/TB-2026-07-06-device-proof-token-hash-only.md`](../product-docs/global/backlog/TB-2026-07-06-device-proof-token-hash-only.md)
- **Test plan:** [`product-docs/global/backlog/test-plans/TSP-2026-07-06-device-proof-token-hash-only.md`](../product-docs/global/backlog/test-plans/TSP-2026-07-06-device-proof-token-hash-only.md)
- **ADR:** [`product-docs/global/architecture-decisions.md`](../product-docs/global/architecture-decisions.md) — ADR-0007
- **Methodology gate:** [`product-docs/methodology/plan-incubation-workflow.md`](../product-docs/methodology/plan-incubation-workflow.md)
- **Plan role after materialization:** retained source; full tier analysis below; execute via TB/TSP.

---

## Executive verdict (summary)

| Token | Hash-only today? | Verdict |
|-------|------------------|---------|
| `device_proof_token` | **Yes** | **Tier 0 — TB-2026-07-06** |
| `enrollment_proof_token` | Partial | Tier 1 — deferred |
| `auth_attempt_proof_token` | No (pull model) | Tier 2 — keep encrypted + hash |

Session-token analogy applies when the server never re-issues the secret. `auth_attempt_proof_token`
must be delivered on pending and rebuilt on respond without client plaintext echo.

---

## Tier 0 — Device proof token (materialized)

See **TB-2026-07-06** for executable slice, evidence plan, and exit criteria.

**Evidence:** `existsByDeviceProofTokenHash` only; no `getDeviceProofToken()` in services; SEC-007.

---

## Tier 1 — Enrollment proof token (deferred)

Hash-only feasible with show-once Admin UX + optional `enrollmentProofToken` on verify request.
Bind can use request token for integration signing without DB decrypt.

---

## Tier 2 — Auth attempt proof token (deferred)

Blocked by: create response omits token; pending must return it; respond verifies signature without
client sending plaintext. Options documented in ADR-0007 (keep encrypted, protocol v2, or deterministic
derivation — not recommended).

---

## Full analysis

The complete plan with code references and diagrams was produced in Cursor Plan mode
(`proof_token_hash-only_45368295.plan.md`). Key Java anchors:

- [`AuthAttemptPendingService.java`](../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptPendingService.java)
- [`AuthAttemptRespondService.java`](../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptRespondService.java)
- [`AuthAttempt.java`](../ezkey-core/src/main/java/org/ezkey/authattempt/domain/entity/AuthAttempt.java)
- [`SensitiveDataHasher.java`](../ezkey-core/src/main/java/org/ezkey/security/SensitiveDataHasher.java)
