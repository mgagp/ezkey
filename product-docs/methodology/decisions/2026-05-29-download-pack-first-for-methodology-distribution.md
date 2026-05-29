---
public: true
---
# Prefer a download pack before a methodology installer

## Date

2026-05-29

## Context

The methodology has reached a point where it stands on its own as a public surface: curated
methodology docs, templates, a derived public skills layer, and a dedicated Cloudflare-hosted
site. This creates a new opportunity: make the methodology easy to adopt locally, not only easy to
read online.

The question is not whether a local distribution path is valuable, but what form it should take
without violating the methodology's own values. A downloadable package could expose immediate value
with low implementation cost. A richer installer flow could also be attractive, but risks adding
accidental complexity too early.

## Source signal (optional)

- "la méthodologie se tient maintenant de façon autoportante"
- "prévoir une fonctionnalité de download"
- "minimum d'effort avec le maximum de rendement"
- "rester conforme aux valeurs méthodologiques de simplicité"

## Working assumptions

- Public distribution should improve real adoption, not merely add a marketing flourish.
- The first distribution path should stay compatible with proportional rigor and evidence before
  automation.
- The canonical distinction between public explanatory surfaces and operational editor-local assets
  should remain explicit.
- A small packaging step inside the existing site pipeline is acceptable; a cross-platform
  installer framework is not yet justified.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep the methodology online-only | Zero added build or packaging logic | Misses a high-leverage adoption path for readers who want immediate local use |
| Build an installer-first experience | Strong adoption story, potentially convenient for some users | Adds automation and packaging complexity before evidence shows it is needed |
| Publish a download pack first, and defer installer work until repeated friction is observed | High value for low effort, aligned with current site pipeline, easy to test and revise | Requires a small amount of packaging and curation logic |

## Decision

Adopt the third option.

The preferred next step for public methodology distribution is a **download pack**, not an
installer.

This pack should remain intentionally small and explicit. The default shape is one downloadable
archive with two clearly documented usage paths:

1. **Reference pack** — local reading and adaptation of the methodology, templates, glossary, and
   derived public skills.
2. **Workspace starter** — optional installation-oriented material that helps a team place the
   methodology into a repo or workspace without making editor-local operational files the primary
   public corpus.

The pack is generated from the existing site/build pipeline. A richer installer is deferred until
manual adoption shows repeated friction that justifies additional automation.

## Consequences

- The methodology now has an explicit distribution posture: package first, automate later only if
  earned.
- Future implementation should favor one archive with clear internal paths over multiple premature
  delivery formats.
- The packaging design must preserve the distinction between derived public skills and canonical
  operational skill sources.
- The first implementation slice should stay small: button, generated archive, and concise local
  guidance.

## Related documents

- [../methodological-values.md](../methodological-values.md)
- [../README.md](../README.md)
- [../../site/README.md](../../site/README.md)
- [../../site/build.js](../../site/build.js)
- [../../../.cursor/skills/README.md](../../../.cursor/skills/README.md)
