# Critical review — 2026-10-08 — pr711-northwind-vision

## Metadata

- **Date:** 2026-10-08
- **Keyword:** `critical-review`
- **Target:** PR #711 @ `feb763e2` (`V-2026-10-07-multi-tenant-closed-testing-demo-app`)
- **Extras:** Play closed-testing posture; `MOBILE_PLAY_PUBLISHING.md`
- **Model / effort:** Claude Opus 5.5 high (fresh read-only cloud agent; ≠ author family)
- **Angle:** Play premise, onboarding load, trigger security
- **Launcher:** Mathieu
- **Reviewer agent:** CR1 (ephemeral report; not committed)
- **Verdict:** GO with amendments
- **Amend rate:** 9 / 9 (100%)
- **Status:** Closed

## Decisions

Marc accepted all findings. Clarifications on the two S1 items:

| # | Finding | Severity | Decision | Notes |
| --- | --- | --- | --- | --- |
| 1 | Play “production” vs compass closed-testing only | S1 | amend | Clarified: production goal for **mobile** only; compass line amended accordingly |
| 2 | Lock 6 couples TS SDK dogfood with Play path | S1 | amend | Adjusted: extend **Acme** (access codes) rather than new Northwind app; TS SDK dogfood = separate subject |
| 3 | Play success / engagement criteria missing | S2 | amend | — |
| 4 | Non-developer onboarding under-specified | S2 | amend | — |
| 5 | Multi-tenant tests without oracles | S2 | amend | — |
| 6 | Security: free controls missing; costly ones droppable | S2 | amend | — |
| 7 | Play wording “consecutive/continuously” imprecise | S3 | amend | — |
| 8 | unknown-user vs generic error | S3 | amend | — |
| 9 | Verified-facts table copies API contract | S3 | amend | — |

## Lessons (1–3)

- Caught a real contradiction with the live compass and a disproportionate premise (TS greenfield before M1).
- Respected settled locks C1–C5; did not reopen without new signal.
- Prefer in-stack Acme extension when the goal is Play engagement realism.

## Follow-ups

- Amendments applied on the vision / Acme M1 path (see PR #726).
- Method not yet in-repo at review time — this note backfills discoverability.
