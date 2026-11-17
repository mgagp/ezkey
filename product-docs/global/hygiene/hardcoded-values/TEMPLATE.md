# Hardcoded values curated — YYYY-MM-DD pass-N

## Metadata

- **Date:** YYYY-MM-DD
- **main SHA:** _(full or short SHA)_
- **Keyword:** `assessment-curated` (instance: hardcoded values)
- **Mandate:** surface, attention axes, non-goals
- **Operator:** _(who asked; lots stay undecided until HITL)_
- **Method:** [`README.md`](README.md)

## Detection

Commands (read-only `rg`). Counts: raw → filtered → read → shortlisted. Externalization rate
against the ≤ 10–15 % target. Note anything the heuristic missed.

## Shortlist

At most 12–15 rows. Order: (e) → (a) → (b) → (c) → (d).

| # | file:line | value | role | history (blame / PR / issue) | requester | disposition | cost / risk |
| --- | --- | --- | --- | --- | --- | --- | --- |
|  |  |  |  |  | none, or who | keep / keep+comment / named constant / remove / externalize / coherence test / skip | XS–M / low–medium |

Disposition comes from Patrick's question in the [method](README.md). Default is **keep**.

## Edgar checklist

One table per externalization candidate only. Skip this section when the shortlist has none.

| # | Box | This candidate |
| --- | --- | --- |
| 1 | Default equals the old value, proven by a test or a PR note |  |
| 2 | `.env.example` (and compose overlay): var, default, service, restart or recreate |  |
| 3 | "New optional settings" line; no mandatory key without a default |  |
| 4 | Optional recommendation for the existing install, applied only with Marc's go |  |
| 5 | No secret as a default |  |

## Hand to Christophe

Crypto and security properties. List only. No externalization proposal by default.

## Decisions

Filled after HITL, one lot at a time.

| Lot | Rows | Decision | Action |
| --- | --- | --- | --- |
|  |  | keep / comment / constant / remove / externalize / skip | PR or "not this pass" |

## Follow-ups

- Lots deferred to a later PR
- Heuristic gaps to fix before the next delta

## Out of scope

- PAM, crypto parameters, and anything the mandate named as a non-goal
