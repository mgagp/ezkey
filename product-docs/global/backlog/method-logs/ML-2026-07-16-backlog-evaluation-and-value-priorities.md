# Method log — Backlog evaluation and value-priority proposal (2026-07-16)

## Metadata

- Date: 2026-07-16
- Trigger: ponctual product exercise (status review + next-value priorities)
- Scope: backlog state evaluation, September operable-release lens, and cross-backlog value priorities
- Requested output: durable written record of the full analysis and recommendations, with no immediate follow-up execution
- Status: recorded

## Intent of this log

Preserve, in one canonical place, the complete result of the 2026-07-16 exercise:

1. Where Ezkey stands against the prioritized September release timeline.
2. What should come next from a broader product posture (living product, potentially in production for PMEs).
3. A concrete prioritization proposal in three lots: release, J+30, J+90.

This log intentionally does not trigger implementation work. It captures evaluation and direction only.

## Canonical sources used

- product-docs/global/operational-readiness-prioritization-2026-09.md
- product-docs/global/backlog/index.md
- product-docs/global/roadmap.md
- product-docs/global/design-principles.md
- product-docs/methodology/README.md
- product-docs/methodology/workflow-overview.md
- product-docs/methodology/testing-strategy-in-workflow.md
- PRD.md
- docs/PROJECT_POSITIONING.md

## Evaluation — Where we are (September release timeline)

### 1) Priority path completion status

Against the operable-release compass for September 2026:

- Wave A: closed.
- Wave B (integrity cluster R1): closed.
- Wave C (operator UI residuals tied to integrity): closed.
- Wave D (EXP1 soak and simulation): ongoing.

Operational interpretation:

- The implementation critical path that was intentionally prioritized for September has largely been delivered.
- The dominant remaining release risk is now runtime proof and behavior confidence under realistic conditions, not missing structural feature slices.

### 2) Current active pressure points in backlog state

High-signal items still shaping release confidence:

- I-2026-07-10-nightly-integrity-boundary-false-positives (P1, active)
  - Prevent CRITICAL integrity false positives from boundary artifacts.
  - Protect operator trust in alert truthfulness.

Recent hardening momentum (already closed) confirms forward movement and reduced security ambiguity:

- I-2026-07-15-sec-021-recovery-token-privilege-boundary (closed).
- I-2026-07-15-sec-022-api-key-object-authorization (closed).

### 3) Milestone posture fit

The observed status is coherent with roadmap and product intent:

- P1-operability: substantially advanced.
- P2-hardening: clearly active and productive.
- September lens remains explicitly operability-first, not breadth-first.

This aligns with the project thesis (backend-first trust, operator legibility, explicit security posture) and with the release-order compass.

## Broad product posture — Full backlog value reading

### Framing assumption

Ezkey is treated here as a living system potentially operating in production-like PME contexts.
Value is therefore ranked by:

1. operator trust under failure,
2. durable security posture at runtime and data boundaries,
3. maintainable operational performance,
4. adoption friction reduction.

### Value-ranked priorities (cross-backlog)

1. Preserve alert truth and integrity signal credibility
- Finish I-2026-07-10-nightly-integrity-boundary-false-positives.
- Why first: if CRITICAL alerts are noisy, the integrity model loses practical operator value.

2. Harden database trust boundaries
- Advance I-2026-0021 PostgreSQL role/permission matrix (at least first credible R1 hardening slice).
- Why now: backend trust claims are strongest when runtime DB rights enforce audit immutability expectations.

3. Establish repeatable runtime security assurance
- Move I-2026-07-12-security-pentest-curated-hygiene through TB MVP execution.
- Why now: shifts security confidence from episodic effort to repeatable campaign evidence.

4. Continue proof-token storage hardening trajectory
- Progress I-2026-0032 beyond Tier 0 when protocol constraints are explicit and accepted.
- Why: reduces secret exposure and operational crypto burden over time.

5. Remove pre-scale technical debt in key rotation path
- Execute I-2026-0029 indexed encryption key id columns before first production installation.
- Why: prevents avoidable scale/performance friction in re-encryption operations.

6. Secure-by-default operability posture
- Prioritize I-2026-0004 (Admin API API-key acceptance flag) and I-2026-0011 (activation-code default bootstrap posture).
- Why: narrows trust boundary ambiguity and improves default deployment discipline.

7. Adoption and operator convenience accelerators (after trust baseline)
- I-2026-0010 phone-to-phone transfer,
- I-2026-07-11 API key expiresAt edit,
- I-2026-0023 email channel R1,
- I-2026-0024 SMS channel R1 SPI.
- Why later: high product value, but less foundational than trust/operability hardening for near-term release credibility.

## Concrete proposal in 3 lots

### Lot 1 — Release September closure (risk-outcome lot)

Goal: secure operable-release confidence with the smallest high-impact set.

- Finalize and validate I-2026-07-10-nightly-integrity-boundary-false-positives.
- Execute Wave D discipline: EXP1 soak + scenario simulation evidence capture.
- Pull one realistic first slice of I-2026-0021 if capacity allows (audit-log rights hardening first).
- Keep release scope tight: no broad feature expansion.

Expected outcome:

- Alert model remains trusted under routine operation.
- Operability narrative is evidence-backed, not only implementation-backed.

### Lot 2 — J+30 post-release confidence and security routine

Goal: move from release readiness to repeatable assurance.

- Run TB MVP for I-2026-07-12 security-pentest-curated-hygiene and record first campaign cycle.
- ~~Advance I-2026-0021 from analysis to concrete grants/role split where justified.~~ **Done
  2026-07-18** — `TB-2026-07-16-postgresql-application-role-split`, commit `568f1423`;
  clean-start, grants verification, standard/elective suites, and real-mobile flow validated.
- Continue I-2026-0032 by promoting next accepted hardening slice only if protocol implications are explicit.
- Implement I-2026-0029 if still pending.

Expected outcome:

- Security confidence becomes process-driven and periodically repeatable.
- Data-layer trust boundaries become materially stronger.

### Lot 3 — J+90 product value growth (adoption and distribution)

Goal: increase adoption value once trust baseline is stable.

- Progress I-2026-0010 phone transfer ceremony (high user-facing value for lifecycle continuity).
- Deliver I-2026-07-11 API key expiresAt edit (operator efficiency / reduced unnecessary key churn).
- Sequence channel ergonomics:
  - I-2026-0023 email optional operator send,
  - then I-2026-0024 SMS SPI optional operator send.
- Re-evaluate parked/distribution items based on post-release signal.

Expected outcome:

- Stronger day-2 usability and reduced operational friction.
- Better practical adoption posture without diluting core trust model.

## What is explicitly not done in this exercise

- No implementation started.
- No TB creation/update performed by this log.
- No status transition modified in backlog index.
- No release scope expansion decision imposed.

## Decision summary

The September operable-release strategy was correctly prioritized and largely delivered on its critical path.
The next best value sequence is:

1. close runtime trust gaps and prove behavior (release lot),
2. institutionalize security assurance and data-boundary hardening (J+30),
3. accelerate adoption and operator convenience once trust baseline is stable (J+90).

This log is the durable record of the full 2026-07-16 evaluation and recommendation set.
