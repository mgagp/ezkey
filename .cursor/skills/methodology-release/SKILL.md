---
name: methodology-release
description: Classifies a methodology SemVer bump, drafts a release note, and updates the canonical version file when publishing to methodology.ezkey.org. Use after a publication milestone following Lane E feedback cycles.
disable-model-invocation: true
---
# Methodology Release

## Purpose

Prepare a deliberate methodology publication release: determine the SemVer level, draft the release
note, and update `product-docs/methodology-version.properties`.

## Boundary contract

- **Enter when:** a publication milestone is declared — enough methodology improvements justify
  updating `methodology.ezkey.org` and recording a versioned snapshot.
- **Exit when:** bump level is justified, release note is drafted (or written), version properties
  are updated, the release-notes index row is ready, and the release commit is ready to tag.
- **Call next:** site build/preview, annotated Git tag creation, then Cloudflare deploy per
  `product-docs/site/HANDOFF-PHASE-4.md`.
- **Not needed when:** changes are repo-only Lane E decisions with no public site update planned,
  or the change is clearly a PATCH typo with no publication milestone.

## Input

- Current `product-docs/methodology-version.properties`
- Changes since the previous `methodology/v*` Git tag: `product-docs/methodology/`,
  `product-docs/templates/`, new or updated `.cursor/skills/`, public methodology decisions
- Optional: operator brief on why publication is happening now

## Output

- Recommended SemVer bump (`major` / `minor` / `patch`) with one-paragraph rationale
- Draft release note at `product-docs/methodology/release-notes/YYYY-MM-DD-<semver>.md`
- Updated `methodology-version.properties`
- Updated row in `methodology/release-notes/README.md`
- Recommended annotated tag name: `methodology/v<semver>`

## Classification rules

| Level | Test question |
| --- | --- |
| **MAJOR** | Would an adopter following the prior version hit wrong or invalid guidance? |
| **MINOR** | Is there a new template, skill, workflow doc, or substantive public capability? |
| **PATCH** | Is it wording, examples, or precision only — same workflow? |

When uncertain between PATCH and MINOR: new artifact family or skill → **MINOR**; prose-only →
**PATCH**.

## Procedure

1. Read the current version from `methodology-version.properties`.
2. Locate the previous release tag with `git tag --list "methodology/v*"`. Use the latest
   SemVer tag as the primary baseline. If no tag exists, fall back to the last `released` date.
3. Scan git history or recent files under `product-docs/methodology/`, `product-docs/templates/`,
   and `.cursor/skills/` since that baseline.
4. List user-visible changes for an external reader (not Ezkey product delivery from `global/`).
5. Recommend the bump level and next version number.
6. Instantiate `product-docs/templates/methodology-release-note.template.md` as
   `methodology/release-notes/YYYY-MM-DD-<semver>.md`.
7. Update `methodology-version.properties` (`version`, `released`).
8. Add a row to `methodology/release-notes/README.md` (newest first).
9. Remind the operator to build/preview the site and verify the version badge.
10. After the release commit exists, create an annotated tag named `methodology/v<semver>`.

## Rule

Keep release notes short and scannable. Link to methodology decisions for rationale; do not paste
full decision text. One release note per publication milestone — not per decision.

## Related documents

- `product-docs/methodology/methodology-publication-and-versioning.md`
- `product-docs/methodology/decisions/2026-06-04-methodology-release-git-tags.md`
- `product-docs/methodology/decisions/2026-06-02-methodology-semantic-versioning-and-release-notes.md`
- `product-docs/templates/methodology-release-note.template.md`
