# Nomenclature

## Purpose

This document defines stable IDs and status vocabulary for vision, backlog, and tracer-bullet work.

## Identifier conventions

- `V-YYYY-NNNN` — vision notes
- `I-YYYY-NNNN` — backlog ideas
- `TB-YYYY-NNNN` — tracer bullets
- `R-YYYY-NNNN` — legacy retrofit slices
- `F-<short-kebab-name>` — feature catalog entries
- `ADR-XXXX` / scoped ADR IDs — architecture or design decisions

## Filename conventions

- `V-YYYY-NNNN-<slug>.md`
- `I-YYYY-NNNN-<slug>.md`
- `TB-YYYY-NNNN-<slug>.md`
- `R-YYYY-NNNN-<slug>.md`

Use stable IDs. Do not reuse retired identifiers.

## Backlog status vocabulary

- `captured` — raw idea recorded.
- `triaged` — clarified and categorized.
- `incubating` — being explored but not yet execution-ready.
- `ready` — actionable for tracer-bullet promotion.
- `active` — currently under execution.
- `done` — fully delivered and closed.
- `parked` — intentionally paused for later review.
- `archived` — retained for history, not active.
- `dropped` — explicitly rejected.

## Priority vocabulary

- `P0` critical
- `P1` high
- `P2` medium
- `P3` low

## Retrofit status vocabulary

- `captured` — retrofit slice initialized from source plans.
- `mapped` — extracted signal mapped to canonical destinations.
- `integrated` — canonical destinations updated in this slice.
- `archived` — retrofit slice closed and retained for history.
