# Vision Note — `V-2026-07-18-authentication-wait-evolution` Authentication wait evolution

## Metadata

- **ID:** `V-2026-07-18-authentication-wait-evolution`
- **Status:** `draft`
- **Lane:** `B`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Captured by:** Marc
- **Corpus note:** Boundary enrichment after wait-related canon scan (`2026-07-18`)

## Intent

Evolve Ezkey's **backend completion-wait** model — Integration API and Admin API
`/auth-attempts/{id}/wait`, and the related Admin `/passwordless-wait` blocking path — only where
measured operational value justifies the added complexity. Preserve a simple, inspectable HTTP
integration path and backend-authoritative state while exploring lower-cost waiting mechanisms that
reduce thread, connection, database, and network pressure without prematurely committing Ezkey to
WebSockets or another parallel protocol.

## Motivation

The current long-polling design is intentionally straightforward and remains a reasonable trade-off
for Ezkey's target market. It keeps the SDK experience synchronous-like, avoids a messaging
platform, and reads fresh committed state through short database interactions rather than one
long-running transaction.

The same design nevertheless consumes a live HTTP connection, repeatedly queries PostgreSQL, and
has different thread implications in Integration API and Admin API. Preserving the option space now
allows Ezkey to improve this boundary later without losing the rationale for simpler or more
specialized alternatives.

Before this note, wait-transport evolution had no dedicated product-docs owner: living docs and
configuration describe the current long-poll, and a historical passwordless-login note mentioned
optional PostgreSQL `LISTEN/NOTIFY`, but no vision or backlog artifact owned the comparison.

## Directional principles

1. **Evidence before redesign:** establish a workload and resource baseline before selecting a new
   mechanism.
2. **Compatibility first:** prefer internal or additive evolution over replacing the existing wait
   contract.
3. **Authoritative database state:** notifications or events may accelerate observation, but must
   not become a second source of authentication-attempt truth.
4. **Proportional protocols:** WebSockets must be justified by a real bidirectional or multiplexed
   requirement; one-way or ordinary HTTP patterns should be evaluated first.
5. **Operational legibility:** self-hosted operators must be able to understand, configure, and
   diagnose the chosen mechanism.
6. **Failure posture:** an acceleration layer should normally fall back to safe state reads rather
   than make an otherwise valid authentication flow unavailable.

## Potential impact

- **Product milestones:** `P1-operability`, `P2-hardening`, `P3-distribution`
- **Components:** `core`, `admin-api`, `integration-api`, `sdk`, `infra`, `docs`
- **User segments:** backend integrators, SDK consumers, self-hosted operators, and administrators
  using passwordless wait flows

## Signals and constraints

- **Three wait surfaces must stay distinct unless an analysis explicitly widens scope:**
  1. Mobile Auth API `POST /pending` — user-initiated pull ([ADR-0002](../architecture-decisions.md#adr-0002-user-initiated-mobile-polling)).
  2. Admin login `POST /admin/auth/passwordless-wait` — operator session wait.
  3. Integration/Admin `GET /auth-attempts/{id}/wait` — backend completion wait (this note).
- [ADR-0002](../architecture-decisions.md#adr-0002-user-initiated-mobile-polling) rejects automatic
  mobile polling and platform push for the **mobile** path. That accepted decision does **not** by
  itself forbid backend wait optimization (long-poll, tickets, SSE, or database notification
  assist). Any proposal that would also change mobile delivery must challenge ADR-0002 separately.
- Integration API currently enables virtual threads; Admin API currently uses profile-sized Tomcat
  platform-thread pools.
- The wait loop deliberately avoids one transaction spanning the full HTTP wait.
- PostgreSQL is authoritative and shared across the relevant backend processes.
- Admin API, Auth API, and Integration API are separate processes, so in-process publication alone
  cannot solve cross-process wake-up.
- Candidate patterns include tuning the current wait, notification-assisted wake-up, in-process
  acceleration with fallback, decoupled wait tickets, SSE, WebSockets, signed webhooks, and
  conditional short-polling.
- Optional PostgreSQL `LISTEN/NOTIFY` already appears as a non-canonical optimization sketch in
  [`docs/features/ADMIN_PASSWORDLESS_LOGIN.md`](../../../docs/features/ADMIN_PASSWORDLESS_LOGIN.md);
  treat that as rediscovered historical signal, not accepted architecture.
- `I-2026-0004` / `V-2026-0003` concern API-key acceptance and Admin vs Integration placement, not
  wait transport — do not conflate topology with polling mechanics.
- This direction is not a current release blocker and should not displace higher-priority
  operational-readiness work without evidence.

## Promotion criteria

Promote this vision only after:

- the current Admin API and Integration API wait costs are measured under representative
  concurrency;
- target deployment shapes and proxy constraints are explicit;
- the candidate set is compared using consistent network, thread, memory, database, security, SDK,
  and multi-instance criteria;
- a durable architecture decision records the selected posture and fallback behavior;
- actionable work is split into a bounded tracer bullet rather than treating the whole option space
  as one implementation slice.

## Related documents

- Backlog idea:
  [`I-2026-07-18-authentication-wait-evolution`](../backlog/ideas/I-2026-07-18-authentication-wait-evolution.md)
- API contract: [`docs/ENDPOINT.md`](../../../docs/ENDPOINT.md)
- Architecture overview: [`architecture-overview.md`](../architecture-overview.md)
- Mobile pull boundary (orthogonal unless scope widens):
  [ADR-0002](../architecture-decisions.md#adr-0002-user-initiated-mobile-polling)
- Historical optional notification note:
  [`docs/features/ADMIN_PASSWORDLESS_LOGIN.md`](../../../docs/features/ADMIN_PASSWORDLESS_LOGIN.md)
- Integration API rate-limit family context:
  [`rate-limit-baseline-policy.md`](../rate-limit-baseline-policy.md)
- Related mobile protocol evolution:
  [`V-2026-0008-auth-api-protocol-versioning.md`](V-2026-0008-auth-api-protocol-versioning.md)
- Orthogonal API-key / topology posture (not transport):
  [`V-2026-0003-api-key-acceptance-posture.md`](V-2026-0003-api-key-acceptance-posture.md),
  [`I-2026-0004`](../backlog/ideas/I-2026-0004-admin-api-key-acceptance-flag.md)
- Orthogonal passwordless-wait security correction (**done** 2026-06-26):
  [`I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement`](../backlog/ideas/I-2026-06-23-admin-auth-passwordless-wait-challenge-enforcement.md)

## Incubation sources

- Working plan (Cursor):
  [`.cursor/plans/authentication_wait_evolution_brainstorm.plan.md`](../../../.cursor/plans/authentication_wait_evolution_brainstorm.plan.md)
- Lane: `B` — plan incubation, materialized `2026-07-18`
- Plan role: retained option-space record; this vision note owns the directional product posture.
