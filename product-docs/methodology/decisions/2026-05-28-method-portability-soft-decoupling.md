---
public: false
---
# Method portability — soft decoupling from the source project

## Date
2026-05-28

## Context

The methodology corpus (published at `methodology.ezkey.org`) was authored entirely inside
the ezkey project and inherited a strong textual coupling to it. Two surfaces carried that
coupling visibly:

1. **Skill names** — every methodology skill was prefixed `ezkey-` (e.g. `ezkey-vision-intake`,
   `ezkey-tracer-bullet-promote`). To an external reader, this signalled "internal ezkey
   tooling" rather than "general method action".
2. **Taxonomies inside the corpus** — component tags (`admin-api`, `auth-api`, …) were
   enumerated as a closed Ezkey-specific list, while progression marker governance mixed the
   reusable `P0`–`P4` method vocabulary with Ezkey roadmap synchronization rules.

An audit of the corpus showed ~80–85% of the substance is generic, ~15–20% is incidental
coupling. The substance is worth being reusable by other projects; the coupling actively
obscures that.

This decision records the chosen posture for resolving that tension.

## Working assumptions

- The methodology is a **by-product** of building ezkey, not a separately maintained framework.
- The author remains a developer on ezkey, not a framework maintainer.
- External adopters, if any, treat the corpus as inspiration — there is no support contract,
  no versioned releases, no community channel.
- Ezkey continues to be the **reference case study** that anchors the method to a real practice.
- The `methodology.ezkey.org` domain stays as-is; the chrome remains `ezkey · methodology` to
  reflect honest provenance.

If those assumptions break (e.g. real external demand materialises and starts driving feature
requests), this decision should be revisited and a stronger extraction posture considered.

## Options considered

| Option | Advantages | Disadvantages |
|---|---|---|
| **A. Status quo** (keep `ezkey-` everywhere, no changes) | Zero effort | Caps reusability; reads as internal docs, not a method |
| **B. Soft decoupling** (strip `ezkey-` from skill names, generalize taxonomies, add a case-study page) | Restores readability as a method; preserves provenance honestly; low ongoing cost | Requires a one-off rename pass and editorial review |
| **C. Full extraction** (separate repo, new identity, neutral domain) | Maximum portability | Creates a second maintenance surface; high orphaning risk; premature given no external demand |

## Decision

Adopt **Option B — soft decoupling**.

Concretely:

1. **Skill names**: strip the `ezkey-` prefix from all twelve methodology skills.
   The naked verbs (`vision-intake`, `tracer-bullet-promote`, `closeout`, …) read as method
   actions, applicable wherever the method is used. No replacement prefix.
2. **Component tags**: in `nomenclature.md` and the rich view, document the concept generically
   ("a component tag identifies which bounded context an artifact targets") and move the closed
   list of ezkey component names into the dedicated case-study page.
3. **Progression markers**: keep the opinionated `P0-P4` vocabulary in the method, but move
   Ezkey-specific roadmap synchronization rules and concrete project binding into the case study.
4. **Case study**: a single new page documents ezkey as the source project — its components,
   milestones, corpus root, and which examples in the method are drawn from it. Methodology
   docs that lean on ezkey-specific examples link to this page rather than carrying the
   coupling inline.
5. **What we explicitly do not do**: no new name, no version numbers, no separate repo,
   no community surface, no contribution guidelines for the method as such.

## Consequences

Files updated (this work):

- `.cursor/skills/ezkey-*/` → `.cursor/skills/*/` (twelve directories renamed via `git mv`).
- Cross-references in `.cursor/rules/product-docs-workflow-bootstrap.mdc`, `.cursor/skills/README.md`,
  root `AGENTS.md`, `.github/copilot-instructions.md`, `CONTRIBUTING.md`, and all methodology
  documents that name skills (README, session-start-guide, decision records, etc.).
- Published surfaces: `product-docs/methodology/view/index.html`,
  `sites/ezkey-org/methodology.html`, `sites/ezkey-org/fr/methodologie.html`,
  `scripts/publish-methodology-view.ps1`.
- Methodology explorer rebuild and republish to `methodology.ezkey.org`.

Files updated (later phases — separate decisions or commits):

- `nomenclature.md` — generalize component-tag definitions and separate reusable progression
   marker vocabulary from Ezkey-specific roadmap binding.
- New file: a single ezkey case-study page inside the methodology corpus.
- Editorial pass to neutralize incidental ezkey examples where they are not pedagogical.

## Related documents

- [`../README.md`](../README.md) — methodology entry point (will be updated).
- [`../nomenclature.md`](../nomenclature.md) — to be generalized in a follow-up.
- [`../../../sites/ezkey-org/methodology.html`](../../../sites/ezkey-org/methodology.html) —
  marketing rich view; carries the cross-surface CTA to the explorer.
- [`../../../scripts/publish-methodology-view.ps1`](../../../scripts/publish-methodology-view.ps1)
  — regenerates the rich views.
