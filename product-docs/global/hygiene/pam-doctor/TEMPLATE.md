# PAM doctor-curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Branch / PR:** _(fill when opened)_
- **Command:** `./scripts/pam-doctor-curated.sh`
- **Report:** `logs/pam-doctor/pam-doctor.curated.md` (local, gitignored)
- **Scope:** `ezkey-pam/src` Linux reference. macOS install track excluded.
- **Operator:** Marc (+ agent)

## Lot under review

Brief numbered overview only (rule + location hint). Keep small (≈3–6).

**Agent briefing style:** do not use a dense options matrix as the primary HITL vehicle. Iterate
one finding at a time; record the decisions table here after HITL closes.

## Decisions

| # | Finding | Decision | Action |
|---|---------|----------|--------|
| 1 | `RULE` — `file` | pending / fix / suppress / skip | What was done |

Decision values:

- **pending** — reported, not yet decided
- **fix** — source change accepted after HITL
- **suppress** — intentional or harmless; reason in `config/pam-doctor/suppressions.json`
- **skip** — fuzzy or not worth inventing work

If it ain't broken, don't fix it. A clear diagnostic does not require a rewrite.

## Context

One short subsection per retained item (what the tool said, where it sits in the PAM flow,
why it might matter, what not to do). Do not paste full tool logs.

### 1 — …

## Test posture

For each accepted **fix**, name the existing harness that already pins the behavior
(`make test` targets: `test-http`, `test-conf`, `test-decision`, `test-sanitize`,
`test-filelog`, `test-challenge`, `test-budget`, `test-session`, `test-diag`, plus stub
scripts). Add a case only when the change is not already pinned. Leave and suppress
get no new tests.

## Follow-ups

- Open items deferred
- Suppressions added
- Code touched

## Out of scope this pass

Rules explicitly not attacked, and the macOS install track.
