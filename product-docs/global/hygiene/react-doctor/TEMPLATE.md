# Admin UI doctor-curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Branch / PR:** _(fill when opened)_
- **Command:** `npm run doctor:curated` (from `ezkey-admin-ui/`)
- **Report:** `ezkey-admin-ui/logs/react-doctor/react-doctor.curated.md` (local, gitignored)
- **Operator:** Marc (+ agent)

## Lot under review

Brief numbered overview only (rule + location hint). Keep small (≈3–6), including any modest
low-signal allotment.

**Agent briefing style:** do not use a dense options matrix as the primary HITL vehicle. Iterate
one finding at a time in dialogue with the operator; nest the short project-contextual continuous-
learning briefing inside each item turn. Record the final decisions table here after HITL closes.

## Decisions

| # | Finding | Decision | Action |
|---|---------|----------|--------|
| 1 | `RULE` — `path/or/component` | fix / suppress / skip | What was done |

Decision values:

- **fix** — source change accepted after HITL
- **suppress** — intentional, accepted noise, or clear-but-harmless pattern left in place; reason
  in `SUPPRESSED_RULES` inside `ezkey-admin-ui/scripts/doctor-curated.mjs`
- **skip** — fuzzy or not worth inventing work; no suppress unless it will keep nagging without value

Also remember AGENTS.md: **if it ain't broken, don't fix it** — clear diagnosis does not require a rewrite.

## Rationale (short)

One short subsection per item (2–8 lines). Record risks, design posture, and why not the alternatives.
Prefer concrete Admin UI surfaces (DataTable, Dialog, audit logs, pagination) over framework theory.
Do not paste full chat transcripts.

### 1 — …

### 2 — …

## Follow-ups

- Open items deferred (e.g. design-decision migrations such as wholesale `<dialog>`)
- Suppressions added: list rule ids
- Code touched: list paths

## Out of scope this pass

Rules explicitly not attacked (e.g. broad design migrations, giant-component splits).
