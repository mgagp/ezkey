---
public: true
---
# Methodology semantic versioning and release notes

## Date

2026-06-02

## Context

The Ezkey monorepo uses a preliminary Maven version (`0.0.1-SNAPSHOT`) because the product is not
yet at official Release 1.0. That is correct for the software artifact.

The publishable methodology product is intentionally semi-detached: it ships through
`methodology.ezkey.org` and the download pack, not through Maven coordinates. Until now it had no
canonical version number, no release history, and no explicit publication workflow for declaring
what changed between site updates.

External readers and adopters need a lightweight way to know which methodology version they are
viewing and whether a site update introduced meaningful changes.

## Source signal (optional)

- "Établir un mécanisme afin de versionner le volet méthodologie."
- "Tendre vers du Semantic Versioning… majeur / révision d'un item existant / petit correctif sans
  breaking changes."
- "Afficher proprement la version actuelle… section Release Notes dans la navigation à gauche."
- "Un skill associé… encadrer la génération d'un release note et la décision du niveau de révision."
- "Naming pattern : date avec le numéro de version — pas de slug."
- "Simple, pragmatique, focus… une petite trace historique comme n'importe quel autre artefact."

## Working assumptions

- Methodology versioning is **independent** of the Ezkey product Maven version.
- SemVer applies to the **published methodology product**, not to every git commit or Lane E
  decision.
- Lane E may produce many small methodology decisions between publications; only a deliberate
  **publication release** bumps the version and adds a release note.
- Release notes stay short and scannable. They summarize user-visible changes; they do not replace
  methodology decisions for rationale.
- A dedicated skill is justified because version classification and release-note drafting benefit
  from focused corpus analysis (`product-docs/methodology/`, recent decisions, templates, skills).

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Reuse Maven `${revision}` from pom.xml** | Single version surface | Couples methodology lifecycle to unreleased product versioning; wrong audience signal |
| **B. Git tags only** | Familiar to developers | Opaque to site readers; poor discoverability without leaving the explorer |
| **C. Properties file + SemVer + release notes on the public site** | Clear canonical version; visible to readers; independent lifecycle; fits existing publication pipeline | Requires a small publication ritual and one new artifact family |
| **D. Full changelog per methodology file** | Maximum granularity | Disproportionate ceremony; fights proportional rigor |

## Decision

Adopt **Option C**.

### 1. Canonical version source

- File: `product-docs/methodology-version.properties`
- Fields: `version` (SemVer `MAJOR.MINOR.PATCH`), `released` (`YYYY-MM-DD`)
- Updated only during a deliberate methodology publication release.

### 2. SemVer interpretation for the methodology product

| Bump | When | Examples |
| --- | --- | --- |
| **MAJOR** | Breaking change to how adopters should apply the method | Lane renumbering with migration impact, removed artifact types, renamed core workflow steps that invalidate prior guidance |
| **MINOR** | Backward-compatible capability addition | New template with directives, new skill, new lane or workflow doc, substantive new public decision |
| **PATCH** | Clarification or correction without workflow impact | Rephrase, precision in a skill, typo fix, non-breaking template tweak |

When uncertain between PATCH and MINOR, prefer **MINOR** if a new template, skill, or workflow
surface was added; prefer **PATCH** if only wording or examples changed.

### 3. Release notes artifact family

- Location: `product-docs/methodology/release-notes/`
- Index: `release-notes/README.md`
- Instance naming: `YYYY-MM-DD-<semver>.md` (date of publication + version; no slug)
- Template: `product-docs/templates/methodology-release-note.template.md`
- All release notes publish by default on the methodology site.

### 4. Publication workflow guide

- Canonical guide: `methodology/methodology-publication-and-versioning.md`
- Invoked after enough Lane E feedback cycles justify a public site update.

### 5. Skill: `methodology-release`

- Helps classify the SemVer bump from recent methodology changes.
- Drafts the release note from focused analysis of the methodology corpus.
- Updates `methodology-version.properties` and cross-links affected docs.

### 6. Site presentation

- Display current methodology version in the explorer top bar.
- Place **Release notes** early in the left navigation (after the methodology README).
- List release notes newest-first.

## Consequences

- New files: version properties, publication guide, release-note template, release-notes folder,
  `methodology-release` skill, site version API/display.
- Lane E remains the reflective loop; publication release is a separate, deliberate milestone.
- Download pack manifest should include the version properties file for offline adopters.

## Related documents

- [`../methodology-publication-and-versioning.md`](../methodology-publication-and-versioning.md)
- [`../../templates/methodology-release-note.template.md`](../../templates/methodology-release-note.template.md)
- [`../release-notes/README.md`](../release-notes/README.md)
- [`2026-05-29-methodology-publication-boundary.md`](2026-05-29-methodology-publication-boundary.md)
- [`2026-05-28-methodology-feedback-and-post-delivery-reentry.md`](2026-05-28-methodology-feedback-and-post-delivery-reentry.md)
