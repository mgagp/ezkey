# Backlog Idea — `I-2026-07-18-authentication-wait-evolution` Authentication wait evolution

## Metadata

- **ID:** `I-2026-07-18-authentication-wait-evolution`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Last reviewed at:** `2026-07-18`
- **Corpus note:** Boundary enrichment after wait-related canon scan (`2026-07-18`)
- **Progression markers:** `P1-operability`, `P2-hardening`, `P3-distribution`
- **Component tags:** `core`, `admin-api`, `integration-api`, `sdk`, `infra`, `docs`
- **Lane:** `B`
- **Captured by:** Marc
- **GitHub issue:** none

## Intent

Evaluate and converge on a proportionate evolution of Ezkey's authentication-attempt wait
mechanism, preserving the current simple HTTP contract unless measured resource, network, proxy, or
SDK constraints justify an internal optimization or an additive alternative.

## Problem and value

- **Problem:** The current wait mechanism is a deliberate simplicity trade-off: it holds an HTTP
  request while repeatedly reading current attempt and supersession state. Integration API parks a
  virtual thread, while Admin API currently uses platform-thread Tomcat pools. The design remains
  acceptable for the expected market, but its actual concurrency envelope, database cost, proxy
  behavior, and next evolution path have not been compared systematically.
- **Expected value:** Preserve a clear record of realistic alternatives, quantify when the current
  model becomes limiting, and choose the smallest improvement that meaningfully strengthens
  operability and SDK integration without introducing accidental protocol complexity.

## Scope

- **In scope:**
  - Backend completion waits: Integration/Admin `GET /auth-attempts/{id}/wait` and, where the same
    service loop is shared, Admin `POST /admin/auth/passwordless-wait` resource implications.
  - Measure the current Admin API and Integration API wait paths.
  - Define representative low, expected, and stress concurrency scenarios.
  - Compare tuning, Admin API virtual threads, PostgreSQL notification-assisted wake-up,
    in-process acceleration with fallback, wait tickets, SSE, WebSockets, signed webhooks, and
    conditional short-polling.
  - Evaluate network, thread, memory, database, connection-pool, Caddy/proxy, multi-instance,
    authorization, audit, rate-limit, cancellation, and SDK implications.
  - Decide whether the best first slice is internal-only, additive at the API boundary, or no
    change beyond tuning and documentation.
- **Out of scope:**
  - Implementing any candidate before the comparison converges.
  - Changing the mobile Auth API `pending` pull model or revisiting
    [ADR-0002](../../architecture-decisions.md#adr-0002-user-initiated-mobile-polling) (user-initiated
    mobile polling / no platform push) unless a later analysis explicitly widens scope.
  - API-key acceptance or Admin vs Integration topology (`I-2026-0004` / `V-2026-0003`).
  - Passwordless-wait challenge-enforcement security semantics
    (`I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`, **done** 2026-06-26).
  - Replacing PostgreSQL as authoritative auth-attempt state.
  - Hand-editing generated OpenAPI artifacts.
  - Creating a broad real-time messaging platform for hypothetical future use.

## Candidate set retained for analysis

1. Keep the existing endpoint and tune polling, limits, and documentation.
2. Enable and validate virtual threads on Admin API.
3. Use PostgreSQL `LISTEN/NOTIFY` to wake waiters while retaining coarse polling fallback.
4. Use in-process publication as a local acceleration only.
5. Add a credential-bound wait-ticket start/status protocol.
6. Add Server-Sent Events for one-way terminal-state delivery.
7. Add WebSockets only if a multiplexed bidirectional requirement emerges.
8. Add signed webhooks as a separate backend-integration capability.
9. Offer lightweight conditional short-polling by auth-attempt identifier.
10. Combine compatible elements in a staged hybrid while preserving the current wait contract.

## Key assumptions

- Current market expectations do not require thousands of simultaneous waits per installation.
- Backward compatibility and simple SDK ergonomics have higher value than eliminating every
  blocked or parked execution context.
- A notification mechanism is an optimization signal, not durable state.
- Multi-process topology matters even before multi-replica high availability: Auth API writes state
  that Integration API and Admin API waiters observe.
- A new `waitId` needs independent value beyond the existing `authAttemptId`; otherwise it adds a
  lifecycle without reducing essential complexity.

## Risks and exceptions

- Optimizing without a measured baseline could increase operational complexity while producing no
  meaningful user or capacity gain.
- Virtual threads can still consume connections and memory and may be pinned by some blocking
  operations; validation must use the actual Spring/JDBC stack.
- PostgreSQL notification loss or reconnect gaps require a safe database-read fallback.
- In-memory wait tickets or waiter registries complicate multi-instance routing and restart
  behavior.
- SSE and WebSockets keep long-lived connections and introduce proxy, reconnect, authorization,
  and SDK obligations.
- Webhooks create outbound security and delivery obligations such as SSRF controls, signatures,
  retries, and idempotency.
- A new external mode changes the API contract and therefore requires Java changes, tests,
  clean-start, generated spec refresh, dependent client generation, and Postman review.

## Promotion notes

Keep this idea `incubating` until:

- the workload model and current baseline are recorded;
- deployment and multi-instance assumptions are explicit;
- candidates are scored against one common set of criteria;
- a preferred first slice and fallback posture are selected;
- the first slice has bounded success criteria and validation evidence suitable for a new
  `TB-*`.

The likely first analysis should challenge the plan's tentative hybrid recommendation rather than
adopt it by default.

## Links

- Vision:
  [`V-2026-07-18-authentication-wait-evolution`](../../vision/V-2026-07-18-authentication-wait-evolution.md)
- Current API contract: [`docs/ENDPOINT.md`](../../../../docs/ENDPOINT.md)
- Current wait service:
  [`AuthAttemptWaitService.java`](../../../../ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptWaitService.java)
- Integration API wait controller:
  [`IntegrationApiAuthAttemptController.java`](../../../../ezkey-integration-api/src/main/java/org/ezkey/integration/api/controller/IntegrationApiAuthAttemptController.java)
- Admin API wait controller:
  [`AuthAttemptController.java`](../../../../ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java)
- Mobile pull boundary (do not conflate):
  [ADR-0002](../../architecture-decisions.md#adr-0002-user-initiated-mobile-polling)
- Historical optional `LISTEN/NOTIFY` sketch:
  [`docs/features/ADMIN_PASSWORDLESS_LOGIN.md`](../../../../docs/features/ADMIN_PASSWORDLESS_LOGIN.md)
- Rate-limit family context:
  [`rate-limit-baseline-policy.md`](../../rate-limit-baseline-policy.md)
- Related Auth API capability-versioning idea:
  [`I-2026-0025-auth-api-protocol-capability-versioning`](I-2026-0025-auth-api-protocol-capability-versioning.md)
- Orthogonal API-key / topology posture:
  [`I-2026-0004-admin-api-key-acceptance-flag`](I-2026-0004-admin-api-key-acceptance-flag.md)
- Orthogonal passwordless-wait security correction (**done** 2026-06-26):
  [`I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`](I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement.md)

## Incubation sources

- Working plan (Cursor):
  [`.cursor/plans/authentication_wait_evolution_brainstorm.plan.md`](../../../../.cursor/plans/authentication_wait_evolution_brainstorm.plan.md)
- Lane: `B` — plan incubation, materialized `2026-07-18`
- Plan role: retained option-space record; this idea owns analysis progression toward a bounded
  tracer bullet.
