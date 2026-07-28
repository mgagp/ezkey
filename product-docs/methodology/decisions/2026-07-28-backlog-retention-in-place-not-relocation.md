---
public: false
---
# Backlog retention stays in place; tracer-bullet placement and sub-artifact naming corrected

## Date

2026-07-28

## Context

A documentation-triage session investigating the volume of process/ceremony content under
`product-docs/global/` (companion to
[`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md)
and [`2026-07-28-retire-method-log-artifact-type.md`](2026-07-28-retire-method-log-artifact-type.md))
audited `product-docs/global/backlog/` end to end (185 files) and found three separate structural
gaps, none of which touch the artifact model itself (`I-*`/`TB-*`/`V-*`/`R-*` remain unchanged):

1. **Unused archival mechanism.** `backlog/README.md` and `statuses-and-lifecycle.md` described an
   `archived/<year>/` folder as the destination for `archived`-status ideas. That folder existed
   (created 2026-05-07) but held **zero files** three months later. In real practice, every closed
   item stayed in `ideas/` (or at `backlog/` root for tracer bullets) with an updated status and a
   row in `index.md`'s "Recently completed" table — the written policy and the actual habit had
   diverged, and the written policy was the one that was never followed.
2. **Undocumented tracer-bullet placement.** 12 of 49 `TB-*` files lived under `backlog/ideas/`
   (which the README described as `I-*` territory) with no distinguishing rule from the 37 at
   `backlog/` root; status alone did not explain the split (`active`, `done`, and `promoted` tracer
   bullets existed on both sides).
3. **Two sub-artifacts misfiled under their parent's ID.** `ideas/I-2026-0001-test-plan-slice.md`
   (a test-plan slice, which has its own kind and folder: `test-plans/TSP-*`) and
   `ideas/TB-2026-0001-grill-me.md` (a grill session, which has its own kind and folder:
   `grill-sessions/*-grill-me.md`) were both named by borrowing their parent's ID prefix instead of
   using their own artifact kind's naming and location. This produced literal on-disk ID collisions
   (`I-2026-0001-*` and `TB-2026-0001-*` each matching two unrelated files).

A fourth, related finding — `index.md` omitting real `I-*`/`TB-*` entries — is a **content** gap
(missing rows), not a structural naming or retention-model gap, and is tracked by the resync itself
rather than by this decision.

## Working assumptions

- Written policy should describe **actual practice**, not an aspirational mechanism nobody uses. If
  three months of real closures never used a described mechanism, the mechanism is wrong, not the
  practice.
- Status metadata plus an index row is sufficient discoverability for a corpus this size (185
  files). A physical archive folder adds a second source of truth (path *and* status) for no
  observed benefit.
- Sub-artifacts (grill sessions, test-plan slices) are cheap to place correctly from the start: each
  already has a defined folder and naming convention (`grill-sessions/`, `test-plans/TSP-*`) that
  the two misfiled files simply did not use.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Formalize the physical-move archival model** — add year subfolders, migrate all `done`/`archived` items into them | Matches the original README wording | Retroactively "fixes" a mechanism that three months of real practice already rejected; adds migration churn for no discoverability gain over `index.md` |
| **B. Correct the written policy to match practice (in-place retention); document a TB-* placement rule; fix the two misfilings** | Zero risk (no content moves for closed ideas); resolves the actual friction (undocumented TB split, real ID collisions) | Loses the folder-based "at a glance" separation of active vs. historical files (mitigated by `index.md`'s tables and per-file status) |
| **C. Leave everything as-is** | Zero effort | Reproduces the exact drift being diagnosed; the two ID collisions remain a real correctness risk (two files sharing a filename prefix invites the wrong file being edited or linked) |

## Decision

Adopt **Option B**.

1. **Archival stays in place.** `archived` (and `done`, `parked`, `dropped`) remain valid status
   values recorded in the idea's own metadata; no file moves on a status change alone. The unused
   `archived/` folder is removed. `backlog/README.md` and `statuses-and-lifecycle.md` are corrected
   to state this explicitly.
2. **Tracer bullets live at `backlog/` root only.** `ideas/` is reserved for `I-*` files.
   `statuses-and-lifecycle.md` gains a "Tracer bullet placement" section recording this rule. The 11
   legitimate `TB-*` files previously under `ideas/` (excluding the misfiled grill session) are
   relocated to `backlog/` root.
3. **Sub-artifacts use their own kind's naming and folder, never their parent's ID prefix.**
   `ideas/I-2026-0001-test-plan-slice.md` moves to `test-plans/TSP-2026-05-07-mobile-local-auth-per-enrollment-discovery.md`;
   `ideas/TB-2026-0001-grill-me.md` moves to
   `grill-sessions/2026-05-07-mobile-local-auth-capability-discovery-grill-me.md`. Parent files
   (`I-2026-0001`, `TB-2026-0001`) are updated to cross-link the new paths.

## Consequences

- `backlog/README.md`, `backlog/statuses-and-lifecycle.md` updated.
- 12 files relocated (11 tracer bullets + folder correction; the grill session and test-plan slice
  moved as part of the same pass); `index.md` paths updated where it linked the old locations.
- No artifact-type change: `I-*`, `TB-*`, `V-*`, `R-*` and their status vocabularies in
  [`../nomenclature.md`](../nomenclature.md) are unaffected.
- Follow-up (tracked by the resync, not this decision): `index.md` gained rows for `I-*`/`TB-*`
  entries that existed on disk but were never listed.

## Related documents

- [`2026-07-28-legacy-documentation-default-gravity-to-adr.md`](2026-07-28-legacy-documentation-default-gravity-to-adr.md)
- [`2026-07-28-retire-method-log-artifact-type.md`](2026-07-28-retire-method-log-artifact-type.md)
- [`../../global/backlog/README.md`](../../global/backlog/README.md)
- [`../../global/backlog/statuses-and-lifecycle.md`](../../global/backlog/statuses-and-lifecycle.md)
- [`../../global/backlog/index.md`](../../global/backlog/index.md)
