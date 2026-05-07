# Product Orientation Notes

## Purpose

This document is a lightweight log of directional product notes that are not yet stable enough for the roadmap or feature catalog.

## Usage model

Use short entries. Promote mature entries to:

- `../roadmap.md`,
- `../features-and-phases.md`,
- or backlog ideas in `../backlog/ideas/`.

## Entry template

### `V-YYYY-NNNN` `<short title>`

- **Date:** `YYYY-MM-DD`
- **Status:** `draft` / `under-review` / `promoted` / `archived`
- **Intent:** one short paragraph
- **Signals:** key indicators, evidence, or constraints
- **Potential impact:** components, phases, or user segments
- **Next step:** promote, refine, or archive

### `V-2026-0001` Enrollment-scoped local-auth posture for mobile respond

- **Date:** `2026-05-07`
- **Status:** `under-review`
- **Intent:** Explore a product direction where local authentication protection for mobile `respond` evolves from global setting to enrollment-scoped posture, with explicit handling of capability constraints and key lifecycle implications.
- **Signals:** Current model is globally toggled, not enrollment-scoped, and not backend-attested; security investigations highlight the need for explicit trust-boundary wording and pragmatic Android-first hardening.
- **Potential impact:** mobile security UX, enrollment lifecycle semantics, future backend policy hooks, Auth API and Admin API operator expectations, and test strategy updates.
- **Next step:** promote as backlog discovery item `I-2026-0001` and run a bounded capability/design analysis before implementation commitment.
