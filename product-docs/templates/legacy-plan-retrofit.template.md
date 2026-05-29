# Legacy Knowledge Retrofit — `R-YYYY-MM-DD-<slug>` `<topic>`

## Metadata

- **ID:** `R-YYYY-MM-DD-<slug>`
- **Topic:** `<short topic>`
- **Status:** `captured` / `mapped` / `integrated` / `archived`
- **Date:** `YYYY-MM-DD`
- **Owner:** `<name>`
- **Source type:** `plan` / `verbal` / `ad-hoc-code-history` / `mixed`
- **Source period:** `<YYYY-MM>` or range
- **Source references:** list of plan paths and/or verbal capture references
- **Sources annotated:** `yes` / `no` _(mark yes only after retrofitted_by is added to each source file)_
- **Confidence:** `high` / `medium` / `low`
- **Validation needed:** `yes` / `no` (if `yes`, list unknowns below)

## Intent

Describe what this retrofit slice aims to preserve and make discoverable.

## Search scope

_(Optional for plan-type slices with bounded sources. Mandatory for verbal or mixed slices.)_

- Searched: `plans/`, `.cursor/plans/`, etc.
- Found relevant: [selected sources]
- Excluded: [found but out of scope, with reason]

## Extracted signal

### Decisions

- decision 1
- decision 2

### Invariants and rules

- invariant 1
- invariant 2

### Patterns and implementation posture

- pattern 1
- pattern 2

### Risks and known trade-offs

- risk/trade-off 1
- risk/trade-off 2

### Test and validation signal

- existing test evidence
- missing validation evidence

## Canonical mapping

List where each extracted signal is mapped:

- destination doc and section
- reason for destination choice

## Changes applied

Explicit checklist — one entry per planned canonical destination. Unchecked items carry forward as gaps.

- [ ] `<destination doc>` — `<summary of change>`
- [ ] `<destination doc>` — deferred, reason: `<reason>`

## Residual gaps

- gap 1
- gap 2

## Principle candidates (optional)

Capture durable project values or principles surfaced by this retrofit slice.

- candidate 1 (`proposed` / `adopted` / `superseded`)
- candidate 2 (`proposed` / `adopted` / `superseded`)

## Next action

- continue with next source batch
- or archive this retrofit slice
