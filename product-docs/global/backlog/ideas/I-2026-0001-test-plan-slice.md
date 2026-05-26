# Test Plan Slice — `I-2026-0001` Discovery for per-enrollment local-auth policy

## Purpose

Define the minimum test strategy for the discovery and design slice of `I-2026-0001`.

## Metadata

- **Target:** `I-2026-0001`
- **Date:** `2026-05-07`
- **Owner:** `product + ai collaboration`
- **Captured by:** Marc

## Change risk summary

- Primary risk: incorrect assumptions about Android keystore/local-auth behavior leading to unsafe design commitments.
- Secondary risk: UX and policy model drift between mobile intent and backend/operator expectations.
- Components touched: mobile (primary), auth-api/admin-api/admin-ui (future-facing impact).

## Unit tests

- **Required:**
  - mobile unit tests for local-auth preference model transitions (global to per-enrollment data behavior once implemented).
  - tests for downgrade/upgrade guard logic if protected mode semantics change.
- **Optional:**
  - dedicated helper tests for enrollment-level preference migration utilities.
- **Rationale:** cheap and fast validation for logic and migration behavior.

## Functional tests

- **Required scenario(s):**
  - mobile enrollment + pending/respond flow with local protected confirmation enabled for one enrollment and disabled for another (when feature exists).
  - negative flow where policy requirement cannot be met, with clear user outcome.
- **Optional scenario(s):**
  - backend policy hook simulation (if contract work starts in same slice).
- **Rationale:** validates real end-to-end behavior on Docker stack with Demo Device context.

## Elective tests

- **Candidate(s):**
  - key lifecycle edge checks under preference changes across app restarts.
- **Run now?** no
- **Rationale:** defer until implementation reaches first working slice.

## Operational tests

- **Candidate(s):**
  - long-run churn with mixed enrollment local-auth postures.
- **Run now?** no
- **Rationale:** not needed for initial discovery/design slice.

## UI tests (Playwright)

- **Candidate(s):**
  - Admin UI scenario only if/when operator-facing policy controls are introduced.
- **Run now?** no
- **Rationale:** current slice is mobile discovery-first; no immediate Admin UI workflow change.
- **UI testability notes (if deferred):**
  - when UI controls are introduced, include stable selectors and explicit state labels for policy posture.

## Execution evidence

- Commands run: not applicable for discovery-only slice.
- Result summary: planning baseline established; execution evidence pending implementation slice.
- Follow-up test debt (if any): finalize exact automated scenarios during `TB-*` promotion.
