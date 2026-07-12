# Java doctor-curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Branch / PR:** _(fill when opened)_
- **Command:** `./scripts/java-doctor-curated.sh` _(flags if any)_
- **Report:** `logs/java-doctor/java-doctor.curated.md` (local, gitignored)
- **Operator:** Marc (+ agent)

## Lot under review

Brief list of findings chosen for HITL (rule + location hint). Keep small (≈3–6).

## Decisions

| # | Finding | Decision | Action |
|---|---------|----------|--------|
| 1 | `RULE` — `Class` | fix / suppress / skip | What was done |

Decision values:

- **fix** — source change accepted after HITL
- **suppress** — intentional, accepted noise, or clear-but-harmless over-defense left in place;
  reason in `suppressions.json` (and/or SpotBugs exclude)
- **skip** — fuzzy or not worth inventing work; no suppress unless it will keep nagging without value

Also remember AGENTS.md: **if it ain't broken, don't fix it** — clear diagnosis does not require a rewrite.

## Rationale (short)

One short subsection per item (2–8 lines). Record risks, design posture, and why not the alternatives.
Do not paste full chat transcripts.

### 1 — …

### 2 — …

## Follow-ups

- Open items deferred (e.g. sensitive dig needing characterization tests)
- Suppressions added: list rule ids
- Code touched: list paths

## Out of scope this pass

Rules explicitly not attacked (e.g. broad `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`, PMD complexity).
