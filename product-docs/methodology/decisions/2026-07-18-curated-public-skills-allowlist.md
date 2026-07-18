---
public: true
---
# Curate the public skills corpus explicitly

## Date

2026-07-18

## Context

The public methodology explorer derives explanatory skill pages from operational agent skills.
The generator previously discovered every skill directory automatically. That behavior made a new
source-project automation skill public by default, even when it only supported Ezkey-specific
maintenance or editorial work.

Automatic discovery would make the public method grow with the host repository rather than with
the needs of methodology adopters. That conflicts with the method's commitments to simplicity,
proportionality, and earned permanence.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Publish every operational skill | No curation step | Public complexity grows accidentally; project-specific procedures leak into the method |
| Add publication metadata to every skill | Distributed and flexible | Adds another field and policy to all operational skills |
| Maintain one explicit public sequence | Small, visible, deterministic | New public skills require one deliberate list update |

## Decision

Use one explicit public skill sequence as both the publication allowlist and navigation order.

A skill enters the public methodology product only when it explains a reusable method capability
and is deliberately added to that sequence. Operational skills remain private by default.

The generator must fail when the allowlist references a missing skill. It must ignore unlisted
skill directories rather than publishing them as an additional category.

## Consequences

- The public skills corpus remains small and intentional.
- New source-project automations do not expand the methodology automatically.
- Public navigation order and publication eligibility share one visible source.
- Adding a genuinely reusable public skill remains a small, explicit methodology decision.
- `github-issue-promote` is included because it operationalizes the public issue workflow.
- `dependabot-curated` and `monthly-digest` remain source-project skills and are excluded.

## Related documents

- [`2026-05-29-public-skills-derived-site-integration.md`](2026-05-29-public-skills-derived-site-integration.md)
- [`../methodological-values.md`](../methodological-values.md)
- [`../README.md`](../README.md)
