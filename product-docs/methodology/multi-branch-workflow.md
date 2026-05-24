# Multi-Branch and Multi-Worktree Workflow

## Purpose

This document defines conventions for working on product-docs artifacts across parallel Git
branches or Git worktrees without creating document contention. It applies to solo developers
working on orthogonal topics simultaneously, and to teams with multiple developers on independent
branches.

## Why this matters

The methodology produces both **branch-safe artifacts** (one file per idea, one file per tracer
bullet) and **shared accumulator artifacts** (index tables, vision note accumulators). The
accumulator artifacts are the primary source of merge conflicts in a multi-branch context. This
document defines the rules that eliminate or minimize that friction.

---

## Rule 1 — Deferred index update (mandatory)

**Never update index files on a feature branch. Update them only on `main` post-merge.**

This applies to:

- `product-docs/global/backlog/index.md` (backlog idea index)
- `product-docs/global/vision/product-orientation-notes.md` (vision note index)
- `product-docs/global/legacy-retrofit/index.md` (retrofit slice index)

**On a feature branch:**

1. Create the artifact file (`I-*`, `V-*`, `TB-*`, `R-*`) in its target folder.
2. Do **not** edit any index file.
3. The artifact exists on the branch without index registration — this is intentional.

**At merge / post-merge on `main`:**

1. An agent or human reads all artifact files in the target folder.
2. Updates the relevant index to include any new entries from merged branches.
3. Resolves any ID collisions (see Rule 2 below).

This makes each branch's document work entirely self-contained and non-conflicting by default.

---

## Rule 2 — Artifact IDs and collision resolution

### For new artifacts (post-migration)

New artifacts use a **date + slug identifier** that requires no counter lookup:

```
I-YYYY-MM-DD-<slug>     (backlog ideas)
V-YYYY-MM-DD-<slug>     (vision notes)
TB-YYYY-MM-DD-<slug>    (tracer bullets)
R-YYYY-MM-DD-<slug>     (retrofit slices)
```

Since the slug is chosen to describe the topic and the date anchors it, two parallel branches
will only collide if they create artifacts about exactly the same topic on the same day — which is
operationally improbable and trivially detectable at merge.

If a collision does occur: differentiate the slug. No renumbering needed.

### For legacy artifacts (pre-migration NNNN format)

Existing `I-YYYY-NNNN`, `V-YYYY-NNNN`, etc. identifiers are stable — do not rename them.

When working on a feature branch that needs a new legacy-format artifact (unusual after the
migration; prefer date+slug for all new artifacts), note the last-used ID from `main`'s
`index.md` in the branch description or first commit message. This is advisory only.

**Collision resolution at merge**: if two branches both created the same NNNN ID, keep the
further-along one as-is and renumber the other, updating cross-links in that artifact file. This
is rare because legacy NNNN format is retired for new work.

---

## Rule 3 — Vision note files (individual, not accumulator)

Vision notes live as individual files in `product-docs/global/vision/`:

```
product-docs/global/vision/V-YYYY-MM-DD-<slug>.md
```

The `product-orientation-notes.md` file is a lightweight index table — not an accumulator. New
vision notes are created as standalone files; `product-orientation-notes.md` is updated only
post-merge per Rule 1.

This matches the pattern already used for backlog ideas in `product-docs/global/backlog/ideas/`.

---

## Conflict resolution guide (when a conflict does occur)

Most conflicts are **safe "take both"** resolutions:

| File | Conflict type | Resolution |
|------|--------------|------------|
| `backlog/index.md` | Two rows added to the table | Take both rows; renumber if NNNN collision |
| `vision/product-orientation-notes.md` | Two rows added to the index table | Take both rows |
| `legacy-retrofit/index.md` | Two rows added to the table | Take both rows |
| Any `I-*`, `V-*`, `TB-*`, `R-*` file | Two branches created the same filename | Rare with date+slug; differentiate one slug |

Living documents (`architecture-decisions.md`, `features-and-phases.md`, etc.) that two branches
both modify represent inherent semantic conflicts — not methodology artifacts — and must be resolved
with domain judgment.

---

## Summary checklist for opening a branch

1. **Create artifact files** (`I-*`, `V-*`, `TB-*`, `R-*`) freely — no coordination needed.
2. **Do not edit** `backlog/index.md`, `product-orientation-notes.md`, or
   `legacy-retrofit/index.md`.
3. **Use date+slug IDs** for all new artifacts — no counter lookup, no contention.
4. **At merge on `main`**: run an index update pass (manual or via the `ezkey-index-refresh` skill
   when available) to register new artifacts.
