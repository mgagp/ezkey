# GitHub Issues Workflow

## Purpose

GitHub Issues is an **optional traceability layer** on top of the core methodology.
It is not required by the workflow. Use it when an idea warrants external visibility,
contributor collaboration, or milestone tracking.

## Label taxonomy

Labels are applied to issues and PRs. The taxonomy uses five groups:

| Group | Prefix | Purpose |
|-------|--------|---------|
| Lane | `lane:` | Methodology lane the work belongs to |
| Type | `type:` | Nature of the change |
| Component | `component:` | Affected module or area |
| Priority | `priority:` | Relative urgency |
| Status | `status:` | Current workflow state |
| Cross-cutting | _(none)_ | `breaking-change`, `good-first-issue`, `needs-discussion` |

**Lane labels**
- `lane:a` — structured product work backed by V-*/I-*/TB-* artifacts
- `lane:b` — engineering initiative backed by a tech note
- `lane:c` — fast fix or chore, no artifact required

**Type labels**
- `type:feat`, `type:fix`, `type:refactor`, `type:test`, `type:docs`, `type:chore`, `type:security`

**Component labels**
- `component:core`, `component:admin-api`, `component:auth-api`, `component:integration-api`,
  `component:admin-ui`, `component:mobile`, `component:crypto-api`, `component:sdk`,
  `component:migration`, `component:infra`, `component:site`, `component:methodology`

**Priority labels**
- `priority:p0` — Critical, blocks release or production
- `priority:p1` — High, current sprint focus
- `priority:p2` — Medium, next sprint candidate
- `priority:p3` — Low, backlog, no urgency

**Status labels**
- `status:needs-challenge` — Grill Me challenge not yet done
- `status:ready` — Challenge done, cleared for implementation
- `status:blocked` — Blocked on external dependency or decision

## When to create an issue (trigger)

Apply the **"title that stands alone" test**: if you can write a GitHub issue title that
a stranger could read and immediately understand the intent without reading the I-* file,
the idea is ready for an issue.

**Primary window:** right after triage (`status: triaged`) and before or at `incubating`.

**Alternative window:** after the Grill Me challenge is complete and `status: ready`.

Do not create issues for ideas still in `captured` state — they are too rough.

## Skill: `ezkey-github-issue-promote`

### When to invoke

Call this skill when an I-* idea passes the "title that stands alone" test and you want
to open a corresponding GitHub issue.

### What the skill does

1. Reads the target I-* artifact.
2. Checks whether a Grill Me challenge has been completed (looks for a "Grill Me" or
   "Challenge" section with substantive content).
3. Generates a **proposed issue title** and **body** following the template below.
4. Proposes the appropriate labels (lane, type, component, priority, status).
5. Adds a `github_issue: #NNN` line to the I-* artifact's `## Metadata` section once
   the issue number is known.

The skill **proposes** — it does not auto-create. A human or an explicit agent action
must approve and run `gh issue create`.

### Issue body template

```markdown
## Intent

<one paragraph from I-* Intent section>

## Problem and value

- **Problem:** <from I-* Problem>
- **Expected value:** <from I-* Expected value>

## Scope

- **In scope:** <from I-*>
- **Out of scope:** <from I-*>

## Grill Me status

<"Challenge complete — see product-docs/global/backlog/ideas/I-*.md" if done,
 or "Challenge not yet done — label: status:needs-challenge">

## Traceability

- **Backlog idea:** `I-YYYY-NNNN` in `product-docs/global/backlog/ideas/`
- **Lane:** `A` / `B` / `C`
```

### Traceability back-reference

After creating the issue, add to the I-* `## Metadata`:
```
- **GitHub issue:** `#NNN`
```

And add to `## Links` if a branch or PR exists:
```
- GitHub branch: `feature/NNN-i-yyyy-mm-dd-<slug>`
- GitHub PR: `#NNN`
```

## Branch naming convention

```
feature/<issue-number>-<artifact-slug>
```

Example: for issue `#42` and idea `I-2026-05-24-admin-audit-log`,
the branch is `feature/42-i-2026-05-24-admin-audit-log`.

## PR convention

PR title should follow conventional commit format:
```
feat(component): short description (#42)
```

PR body must include:
```
Closes #42
```

And a traceability block:
```
## Traceability
- Closes: #NNN
- Backlog idea: I-YYYY-NNNN
- Tracer bullet: TB-YYYY-NNNN (if exists)
```

## Grill Me output placement

When a Grill Me challenge is completed on an issue-backed idea:
1. Add the challenge output to the I-* artifact under a `## Grill Me` section.
2. Post the same output (or a summary) as a **comment** on the GitHub issue.
3. Update the issue label from `status:needs-challenge` → `status:ready`.

## Traceability model (bidirectional)

```
V-* vision note
  └── I-* backlog idea  ←→  GitHub issue #NNN
        └── TB-* tracer bullet  ←→  GitHub PR #NNN
              └── Branch feature/NNN-<slug>
```

Each artifact references the next level via its `## Metadata` and `## Links` sections.
GitHub issues and PRs reference back to the canonical artifact via body traceability block.
