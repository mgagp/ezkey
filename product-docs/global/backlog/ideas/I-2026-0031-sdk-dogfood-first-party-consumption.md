# Backlog Idea — `I-2026-0031` SDK dogfood: first-party consumption and honest narrative

## Metadata

- **ID:** `I-2026-0031`
- **Status:** `parked`
- **Vision:** [`V-2026-06-30-sdk-dogfood-integrity-posture`](../../vision/V-2026-06-30-sdk-dogfood-integrity-posture.md)
- **Priority:** `—` (was `P3`; not on September 2026 operable-release compass)
- **Created at:** `2026-06-30`
- **Updated at:** `2026-06-30`
- **Last reviewed at:** `2026-06-30`
- **Parked at:** `2026-06-30`
- **Phase tags:** `P3-distribution`
- **Component tags:** `admin-ui`, `admin-api`, `sdk`, `sdk-java`, `docs`, `ezkey-demo-app-acme`
- **Captured by:** Marc

## Intent

Make Ezkey's **SDK consumption dogfood** explicit, honest, and progressively stronger: document
what first-party apps use today, align M2M reference apps with Integration API + published SDKs,
and only then evaluate optional admin-session client or BFF patterns — avoiding a big-bang Admin UI
migration.

## Problem and value

- **Problem:** External narrative implies "we ship SDKs" and "we eat our own dog food," but Admin
  UI consumes Orval Admin API clients, Admin API auth uses in-process `ezkey-core`, and Integration
  SDKs are validated mainly by demo/reference apps. Stale Java SDK README still describes Admin+Auth
  API coverage while `EzkeyClient` implements Integration API M2M only. This creates a credibility
  gap for evaluators and weakens internal clarity on which client to use when.
- **Expected value:** Credible, inspectable first-party adoption path; SDKs improved by real use;
  canonical docs that distinguish cryptographic dogfood vs SDK dogfood; no forced wrong-tool coupling.

## Scope

- **In scope (Phase 0 — documentation / canon):**
  - Vision note and grill session (done 2026-06-30).
  - One canonical "client selection" doc or section: Integration SDK vs Admin API client vs mobile
    Auth API vs in-process core (for maintainers).
  - Refresh Java SDK README to match actual `EzkeyClient` scope; cross-link TypeScript SDK brief.
  - Audit public site / essay claims for collapsed "dogfood" wording.
- **In scope (Phase 1 — M2M alignment, depends on `I-2026-0004`):**
  - Demo ACME + Java SDK examples → Integration API base URL.
  - Confirm functional tests and clean-start docs match.
- **In scope (Phase 2 — design pack, optional):**
  - Component design pack: SDK dogfood matrix, gaps, and phased TB candidates.
  - Evaluate thin **admin passwordless client** (browser-safe TypeScript) vs status quo (Orval +
    login page) — decision record required before implementation.
- **Out of scope (default):**
  - Rewriting Admin UI to call Integration API with API keys.
  - Replacing Admin API in-process auth with HTTP SDK calls.
  - Multi-language SDK expansion beyond current Java + TypeScript Integration focus.
  - Big-bang extraction of Admin UI login into a NestJS BFF solely for SDK parity.

## Proposed phased trajectory (no big bang)

| Phase | Goal | Risk | Typical artifacts |
| ----- | ---- | ---- | ----------------- |
| **0** | Honest canon + README accuracy | Low | `V-*`, docs, grill |
| **1** | M2M reference alignment | Low | `TB-*` tied to `I-2026-0004` |
| **2** | Design pack: admin-session client? BFF? | Medium | Component pack, ADR |
| **3** | Implement only winning slice(s) | Medium | `TB-*`, targeted UI/backend changes |

## Dependencies

- [`I-2026-0004`](I-2026-0004-admin-api-key-acceptance-flag.md) — Integration API as canonical M2M;
  Demo ACME alignment.
- [`F-sdk-and-cli-growth`](../../features-and-phases.md) — program umbrella.

## Open questions (for grill / design pack)

1. Should we publish a **first-party "admin console client"** package (passwordless login helpers
   only), or is Orval + documented login flow sufficient for R1?
2. Is a **minimal Admin UI BFF** (Node/Java) worth the operational cost to mirror integrator
   backend patterns, or does demo-api in `ezkey-sdk-typescript` suffice as the reference?
3. Should Java SDK remain Integration-only, or split packages (`integration-sdk` vs future
   `admin-sdk`) for clarity?
4. What automated check (if any) proves "reference app uses published SDK version from reactor"?

## Acceptance signals (program-level)

- Canon doc answers "which client for which boundary" without reading source.
- At least one first-party M2M app (Demo ACME) documented and tested against Integration API +
  Java SDK.
- TypeScript SDK demo remains the official Node reference; linked from product docs.
- Public copy does not claim Admin UI uses Integration SDK.
- Any new admin-session client package has explicit non-goals (no API keys in browser).

## Parked (2026-06-30)

**Decision:** Program paused after pre-analysis. Insight captured; active execution deferred so
effort stays on the September 2026 operable-release compass
([`operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)
— integrity cluster, soak, structural work).

**What remains valid (do not re-litigate without new signal):**

- Layer **A** (cryptographic dogfood) is strong; layer **B** (SDK consumption) is a distribution
  narrative topic, not an operability blocker.
- Admin UI must **not** adopt Integration SDK for login (wrong API surface and trust boundary).
- Pre-analysis grill decisions **D1–D6** are **confirmed** — see grill session.

**Not folded into this park:**

- [`I-2026-0004`](I-2026-0004-admin-api-key-acceptance-flag.md) — Integration API M2M alignment
  and Demo ACME retargeting remain **independent** (trust boundary, `P1`).

**Resume triggers (any one):**

- Explicit **distribution / SDK** milestone push (`F-sdk-and-cli-growth`).
- Third-party demand for a packaged **admin-session** SPA client.
- SDK friction blocking adopters on demo-acme or `ezkey-sdk-typescript` demo-api.
- Post–September 2026 operable release with calendar for `P3-distribution` work.

**Next review:** after September 2026 milestone gate or on resume trigger.

## Related artifacts

- [`V-2026-06-30-sdk-dogfood-integrity-posture`](../../vision/V-2026-06-30-sdk-dogfood-integrity-posture.md)
- [`2026-06-30-sdk-dogfood-grill-me.md`](../grill-sessions/2026-06-30-sdk-dogfood-grill-me.md)
