# Critical review — 2026-11-05 — `pr1209-connector-spi`

## Metadata

- **Date:** 2026-11-05
- **Keyword:** `critical-review`
- **Target:** PR #1209 @ `1bfb5a675`
- **Extras:** #1206, `V-2026-0005`, `V-2026-0007`, `I-2026-0023`, `I-2026-0024`, `I-2026-11-03-enrollment-invitation-links`
- **Model / effort:** Claude Fable, high effort. Author was Claude Opus. A different model line but the same vendor, since no other vendor's tooling was available on this workstation: independence is partial.
- **Angle:** first opening to real-world services: trust boundaries, SME operability, step order, directory mapping, claims against code
- **Launcher:** Marc
- **Verdict:** GO with amendments
- **Amend rate:** 11 / 11
- **Status:** Closed

## Decisions

| # | Finding | Severity | Decision | Notes |
| --- | --- | --- | --- | --- |
| 1 | An activation code equals both factors | S1 | amend | Never goes through a connector, by email or SMS |
| 2 | The #1180 rule hides the challenge at `BOUND` | S1 (reduced) | amend | Option A: readable at `CREATED` and `BOUND`, with a guard. Comment on #1180 |
| 3 | Directory sync alone does not reach the Duo-style goal | S2 | amend | Honest sentence in the vision; the UI says what comes next after a send |
| 4 | Send path as a mail relay on the community instance | S2 | amend | Option A: fixed template, caps, no evaluator sessions or self-registered tenants |
| 5 | Unauthenticated verify burns an invitation | S2 | amend | Fix quickly and cleanly, outside this program: public instance, best practice (#1024). Already public; the review did not find it |
| 6 | QR phishing reduced to wording | S2 | amend | Option A: named as residual risk; server host shown before step 7 (#1185) |
| 7 | Directory table gaps and a factual error | S2 | amend / defer | Error and immediate deactivation fixed; the rest moved to the directory phase questions |
| 8 | Timeout treated as failure | S2 | amend | Three outcomes, send id for traceability only, no deduplication |
| 9 | Log test could pass despite a leak | S2 | amend | Canary on real container output, including base64 and PNG shapes |
| 10 | Contract frozen too early | S2 | amend | `v0` until step 6. The review's reordering was not followed |
| 11 | The mail provider's data role | S3 | amend | Wording fixed now; posture section at step 5; `I-2026-11-05-privacy-obligations-reminder` |

## Lessons (1–3)

- The review exposed an implicit product intent, not only defects: arguing a delayed-challenge scenario led Marc to state the bind session rule, now canonical. Three of its findings then shrank.
- It sized one consequence on bulk volumes outside the target. The prompt's compass lacked the SME scale, now in `product-intent.md`.
- It did not search open issues and presented #1024 as new. The launcher also relayed a wrong detail before checking it (auto-refresh). Verify claims before relaying them.

## Follow-ups

- Amendments landed on the author's branch (PR #1209); the bind session rule is in PR #1224.
- ADR-0018 accepted by Marc, 2026-11-05.
- Public shell READMEs still predate ADR-0018: one pass later, on `#1206`.
