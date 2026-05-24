# Blitz File Naming in Multi-Branch and Multi-Worktree Contexts

## Date

2026-05-22

## Context

The `blitz-intake-pattern.md` originally used `_blitz-YYYY-MM-DD[-N].md` where `[-N]` is a
per-session ordinal. This convention works in a single-branch context but creates a filename
collision risk in multi-branch and multi-worktree setups: two developers (or the same developer
across two worktrees) starting their first blitz on the same day both produce
`_blitz-2026-05-22.md`. The collision cannot be resolved at merge time without content inspection,
which defeats the purpose of a lightweight workflow.

This decision was made in the same working session that introduced `multi-branch-workflow.md`,
updated `nomenclature.md` to adopt date+slug IDs, created the deferred-index rule for `V-*` and
`I-*` artifacts, and established the `decisions/` folder itself.

The decision was produced through live analysis in a Copilot session — an example of the
"méthoblitz méthodologique" pattern: using the methodology's own blitz technique to produce
methodological decisions in real time.

## Working assumptions

**Orthogonality assumption**: developers (human and AI agents) working on different branches are
coordinated at the human level. Blitz sessions on different branches cover distinct topics. A solo
developer with multiple worktrees works on orthogonal workstreams. A team coordinates so that
simultaneous blitz sessions do not overlap in subject matter.

This is a pragmatic working assumption — it sidesteps intractable coordination complexity while
matching the actual practice of a disciplined solo developer or a coordinated team. It is not a
technical guarantee: the methodology trusts human coordination for topic orthogonality and uses the
naming convention as the enforcement mechanism.

**Consequence of orthogonality**: if topics are distinct by design, their slugs will be distinct
too. The slug is therefore both a human-readability enhancement *and* a collision-prevention
mechanism, with no global counter or index lookup required.

## Options considered

| Option | Advantages | Disadvantages |
|---|---|---|
| `[-N]` ordinal (status quo) | Simple; no decision at session start | Local counter → filename collision at merge |
| `[-N]` ordinal + branch name suffix | Collision-safe | Verbose; branch name in filename is fragile (branches are renamed/deleted) |
| Date+slug (required on feature branch) | Collision-safe; human-readable; consistent with `V-*`/`I-*` convention | Requires choosing a topic name at session start |
| Timestamp (HHMMSS) instead of slug | No coordination needed | Not human-readable; does not reflect topic; ugly in archive |

## Decision

On a **feature branch or Git worktree**: the slug is **required**; the ordinal is not used.

On **`main` or single-branch session**: the `[-N]` ordinal is acceptable as a fallback when the
theme is not yet clear at session start.

File naming:
- Feature branch / worktree: `_blitz-YYYY-MM-DD-<slug>.md`
- Main / single-branch: `_blitz-YYYY-MM-DD[-N].md`

The slug is chosen **at session start** — it answers "what is this blitz about?" before capture
begins. This aligns the blitz naming convention with the date+slug format already adopted for all
other new artifacts (`V-*`, `I-*`, `TB-*`, `R-*`) in the multi-branch workflow.

## Consequences

- `blitz-intake-pattern.md` updated: Phase 0 Setup now instructs the operator to choose a slug at
  session start, with the feature-branch vs. main distinction made explicit. The File naming table
  is restructured to show both contexts.
- No backward-compatible rename of existing blitz archives is required: the rule applies to new
  sessions only. Legacy archives using the ordinal format remain valid.
- The orthogonality assumption is now an explicit, documented working assumption rather than an
  implicit expectation embedded in the naming rule.

## Related documents

- [blitz-intake-pattern.md](../blitz-intake-pattern.md)
- [multi-branch-workflow.md](../multi-branch-workflow.md)
- [nomenclature.md](../nomenclature.md)
