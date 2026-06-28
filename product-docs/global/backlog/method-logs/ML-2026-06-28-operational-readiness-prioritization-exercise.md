# Method log — Operational readiness prioritization exercise (2026-06-28)

## Metadata

- **Date:** `2026-06-28`
- **Trigger:** Post-merge closeout on `main`; question whether Alerts P3 UI is the right next slice
- **Outcome document:** [`../../operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)

## Summary

Maintainer review concluded that **integrity cluster work** (`I-2026-0005`, `I-2026-0006`,
`I-2026-0007`, vision `V-2026-0004`) should **precede** Alerts list matrix polish because:

- alerts exist for **exceptional operational events** (degradation, integrity rupture);
- meaningful validation requires heartbeat, nightly batch, and remediation flows;
- project is in **experimental deployment** mode — prioritize eliminating structural unknowns and
  preserve time for **EXP1 soak** before September 2026 release target.

## Actions taken

1. Created canonical prioritization document `operational-readiness-prioritization-2026-09.md`.
2. Wired discoverability: backlog index, roadmap, session-start guide.
3. Updated `I-2026-0028` P3 Alerts row → **deferred** pending integrity cluster.

## Not in scope of this exercise

- TB promotion or implementation start (awaiting maintainer go/no-go).
- GitHub issue creation for integrity cluster (optional visibility later).
