# Mobile doctor-curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Branch / PR:** _(fill when opened)_
- **Command:** `yarn doctor:curated` / `./scripts/mobile-doctor-curated.sh` _(flags if any)_
- **Report:** `logs/mobile-doctor/mobile-doctor.curated.md` (local, gitignored)
- **Operator:** Marc (+ agent)

## Lot under review

Brief numbered overview only (rule + location hint). Keep small (≈3–6).

**Agent briefing style:** do not use a dense options matrix as the primary HITL vehicle. Iterate
one finding at a time in dialogue with the operator; record the final decisions table here after
HITL closes.

## Decisions

| # | Finding | Decision | Action |
|---|---------|----------|--------|
| 1 | `RULE` — `path` | fix / suppress / skip | What was done |

Decision values:

- **fix** — source change accepted after HITL
- **suppress** — intentional, accepted noise, or clear-but-harmless over-defense left in place;
  reason in suppressions file
- **skip** — fuzzy or not worth inventing work; no suppress unless it will keep nagging without value

Also remember: **if it ain't broken, don't fix it** — clear diagnosis does not require a rewrite.
Be especially careful in crypto / keystore / proof-token zones.

## Rationale (short)

One short subsection per item (2–8 lines). Record risks, design posture, and why not the alternatives.
Do not paste full chat transcripts.

### 1 — …

### 2 — …

## Follow-ups

- Open items deferred
- Suppressions added: list rule ids
- Code touched: list paths

## Out of scope this pass

Rules explicitly not attacked (e.g. broad `rn-prefer-pressable` mass migration unless chosen).
