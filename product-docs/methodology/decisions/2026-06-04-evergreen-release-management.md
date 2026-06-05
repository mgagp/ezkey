---
public: true
---
# Evergreen release management

## Date

2026-06-04

## Context

The methodology already covers product direction, backlog shaping, implementation slices, tests,
traceability, and closeout. A project still needs a way to declare what is included in a release,
when work is feature-complete, how stabilization happens, and how patch fixes on a release line
relate back to `main`.

This matters most for projects that keep moving forward over time: open-source projects, SaaS-like
deployments, and small to medium product teams. These projects may publish versions and maintain a
short patch line, but they usually do not need a full enterprise release-train or long-term-support
system.

## Preserved critique and recommendation

The following release-management critique should remain visible:

1. **Lightweight level, worth integrating soon.**
   A `release-brief` template should capture version, objective, scope, links to `I-*` / `TB-*` /
   tests / closeout, release state, branch/tag, risks, and exclusions. This gives most of the value
   without heavy process.

2. **Medium level, formalize carefully.**
   A release workflow can use statuses such as `planned`, `scoped`, `feature-complete`,
   `stabilizing`, `released`, `maintaining`, and `closed`. Gates should ensure that included work is
   merged to `main`, tested, closed out, and traceable.

3. **Heavy level, avoid by default.**
   Multi-branch maintenance, hotfix backports, support windows, deliberate divergence between
   `main` and release branches, and exceptions not reintegrated are real concerns, but they should
   activate only when a project truly maintains multiple release lines.

4. **Strength of the idea.**
   Release management completes the software development lifecycle by making it explicit what is in
   a release, what is excluded, which fixes live only on a release line, which fixes must return to
   `main`, and which fixes are superseded by another decision.

5. **Likely corpus impacts.**
   The proportional starting set is a new `release-brief` template, a short
   `release-management-workflow.md`, and a decision record. A `release-scope` or `release-manager`
   skill may be useful later. A dedicated release-branch or hotfix-followup skill should wait for
   repeated evidence that manual tracking is drifting.

6. **Angles to keep in view without overbuilding now.**
   Rollback, revert, temporary mitigation, data migrations, compatibility, public release notes vs.
   internal release briefs, support windows, end-of-life, security hotfixes, and forward-port rules
   are all valid concerns. For the current methodology level, only the forward-port rule and the
   rollback/mitigation posture need to be represented explicitly.

## Evergreen positioning

Adopt an **Evergreen release-management model** as the default.

In this model:

- `main` remains the canonical forward-moving line;
- a release is a named point of publication, not a permanent parallel universe;
- a release branch may exist for stabilization and patch fixes;
- patch fixes should normally be forward-ported to `main`;
- non-forward-ported fixes require an explicit reason, such as being superseded by a different
  change on `main`;
- long-term support, end-of-life policy, and multi-line maintenance are outside the default method.

This matches projects where releases move forward over time and only a small number of deployable
versions are usually active at once.

## Decision

Add release management to the methodology at the lightweight and medium levels:

- create `release-management-workflow.md`;
- create `release-brief.template.md`;
- document the Evergreen default;
- defer release-maintenance skills and long-term support conventions until real usage proves they
  are needed.

## Consequences

- Projects can define release scope from traceable, completed methodology artifacts.
- A release can enter `feature-complete` and stabilization without inventing a separate tool-specific
  process.
- Release branches are supported, but not required for every release.
- Patch and hotfix work has a place to record forward-port or non-forward-port rationale.
- The methodology remains complete enough to cover release readiness while avoiding enterprise
  release-management weight by default.

## Validation signal

This change helped if a future release can be scoped from existing `I-*`, `TB-*`, test, closeout,
and decision links without reconstructing intent from memory or tracker tickets.

## Related documents

- [`../release-management-workflow.md`](../release-management-workflow.md)
- [`../../templates/release-brief.template.md`](../../templates/release-brief.template.md)
- [`../artifact-identity-and-review.md`](../artifact-identity-and-review.md)
- [`../minimum-viable-method.md`](../minimum-viable-method.md)
