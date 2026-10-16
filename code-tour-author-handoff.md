<decision>
Final Patrick nits after dual GO on f3163a10: restrict UncertainDecision to no-HTTP-response transport (truthful vs aligning FSM to treat 400/429 as uncertain — smaller and matches Q10 intent); match 503 by status; Try-again after 503 dismisses error while keeping attempt; leave-hint only on Ready; move REBASE report under docs/analysis/. Did not reopen Q1–Q14 or unified 409.
</decision>
<risk>
Try-again path now depends on `attempt` still being set after retryable respond errors — correct for 503; loadPendingAttempt still used when no attempt (true GlobalError from pending load). Reviewers should confirm leave-hint absence during Responding is intentional (leave allowed without deny while in-flight).
</risk>
<verified>
yarn jest app/hooks/__tests__/usePendingAuth.test.ts — 28 passed. Pushed 1ff322a0; awaiting ci-gate + CodeQL.
</verified>
