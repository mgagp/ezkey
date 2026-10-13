## Summary

<!-- What changed and why (product language). -->

## Linked issue

Closes #

## What was tested

- [ ] CI green on this PR (`ci-gate` and the jobs that ran)
- [ ] CodeQL / code-scanning: zero open alerts on this branch
- [ ] Zero unresolved review threads
- [ ] Local checks matching the changed area (see `docs/DEVELOPMENT.md` § Continuous Integration)
- [ ] Manual / Walk evidence when this touches Admin UI or other operator-facing flows (link or describe)

## Risk / rollback

<!-- Who is affected, how to revert if needed. -->

## Checklist

- [ ] Decisions recorded in the owning doc (or N/A)
- [ ] Docs updated when behavior or operator workflow changed
- [ ] Can this request exceed 100 s? (If yes: 202 + polled job, server-side cap, DB single-slot — see `product-intent.md` / #789)
- [ ] No secrets, tokens, or credentials in the diff
- [ ] No Walk-only / local-only patches committed by mistake
