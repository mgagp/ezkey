# JavaMelody curated campaign — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **Keyword:** `javamelody-curated`
- **Command:** `./scripts/javamelody-curated.sh` _(flags if any)_
- **Stack / workload:** `./ezkey-tests/clean-start.sh --with-java-melody` + _(e.g. 3× 2h operational churn)_
- **Report:** `logs/javamelody/javamelody.curated.md` (local, gitignored)
- **Raw outputs:** `logs/javamelody/raw/` (local, gitignored)
- **Operator:** Marc (+ agent)
- **Status:** Open / Closed

## Runtime context

- **Collector:** `http://localhost:8088`
- **Period:** `tout`
- **Coverage caveat:** operational churn loops Admin create/GET + Auth pending/respond. Integration API is expected-quiet. Crypto API is not in JavaMelody.

## Lot under review

Brief numbered overview only (finding + API + family). Keep small (about 3–6).

**Agent briefing style:** iterate one finding at a time; record the decisions table here after HITL
closes. Do not paste raw XML dumps.

## Decisions

| # | Finding | API | Family | Decision | Action |
| --- | --- | --- | --- | --- | --- |
| 1 | `JM-001` title | Admin/Auth/Integration | http/sql/spring/pool | fix / defer / skip | What was done |

Decision values:

- **fix** — source/config change accepted after HITL
- **defer** — acknowledged; later pass or program if the cost grows
- **skip** — expected cost, coverage gap, or not worth acting on

## Rationale (short)

One short subsection per item (2–8 lines). Record risks, design posture, and why not the alternatives.

### 1 — …

## Follow-ups

- Open items deferred
- Complementary searches run (controller map, Postgres, churn logs)
- Code/config/docs touched

## Out of scope this pass

- Hit-count-only restatements of the churn loop
- Integration API silence
- Crypto API (excluded by design)
- Heap histograms / thread dumps unless lastValue showed a memory/thread problem
