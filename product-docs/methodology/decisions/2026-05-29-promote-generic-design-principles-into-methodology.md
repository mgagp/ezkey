---
public: true
---
# Promote generic design principles into the methodology without relocating the Ezkey canon

## Date

2026-05-29

## Context

`product-docs/global/design-principles.md` contains a mix of product-specific Ezkey design rules and
more general design-judgment principles that are useful beyond Ezkey itself.

The methodology publication boundary already excludes `product-docs/global/` from the published
methodology product. That remains correct. However, leaving all of the generic signal trapped in a
source-project document makes the public methodology weaker than it needs to be.

The question is not whether to publish `global/design-principles.md` directly. The boundary says no.
The question is how to extract and restate the reusable methodological signal without relocating or
destabilizing the source-project canon.

## Source signal (optional)

- "plusieurs sont spécifiques à ezkey"
- "design-principles.md lui est vraiment générique et je crois a de la valeur"
- "réintégrer dans metho proprement"
- "minimiser les restructurations tout en mettant en évidence, au niveau de la publication, ces principes additionnels"

## Working assumptions

- The Ezkey product canon should stay in `product-docs/global/`.
- Publication boundary remains intact: source-project documents are not promoted by widening the
  publication surface.
- Reusable signal should be promoted by restating it in method-level canon.
- The promoted methodology document should complement existing methodology docs instead of replacing
  them.

## Structured evaluation

| Source principle | Category | Publication treatment | Rationale |
| --- | --- | --- | --- |
| 1. Simplicity and pragmatism | Promote almost as-is | Publish in methodology companion | Broad design judgment rule with no Ezkey-only dependency |
| 2. Essential over accidental complexity | Promote almost as-is | Publish in methodology companion | Directly reusable as a general design trade-off rule |
| 3. Backend-first integrity | Keep in Ezkey canon | Mention only as source-project hook or case-study concern | Tied to Ezkey's trust model and backend-first product posture |
| 4. Explicit trust boundaries | Promote with light generalization | Publish as explicit boundaries and verification posture | Reusable principle, but wording should not assume Ezkey's exact trust model |
| 5. Developer-first and operator-first | Generalize before promotion | Publish as primary-user-centered design judgment | Reusable, but should not assume Ezkey's exact operator model |
| 6. Stable external contracts | Promote almost as-is | Publish in methodology companion | Broadly reusable for API and contract discipline |
| 7. Stay within the chosen stack | Promote almost as-is | Publish in methodology companion | General maintainability principle |
| 8. Spec-first, test-driven | Promote with light methodological alignment | Publish in methodology companion | Strong fit with existing methodology canon and traceability rules |
| 9. One canonical place per concept | Promote almost as-is | Publish in methodology companion | Broadly reusable documentation and design integrity principle |
| 10. Admin UI sobriety | Keep in Ezkey canon | Mention only as source-project hook or case-study concern | Explicitly tied to Ezkey admin roles and product surface |
| 11. Lifecycle without surprise | Keep in Ezkey canon | Mention only as source-project hook or case-study concern | Valuable, but tightly coupled to Ezkey lifecycle semantics and local ADRs |
| 12. Security is not a feature, it is a posture | Keep in Ezkey canon | Mention only as source-project hook or case-study concern | Strong principle, but current formulation is domain- and mechanism-specific |
| 13. Open-source transparency | Promote with light generalization | Publish as documentation-backed transparency | Reusable, but should not require Ezkey-specific open-source framing |
| 14. Beautiful problems | Promote almost as-is | Publish in methodology companion | High-signal generic rule for deferring speculative sophistication |
| 15. Simple cases stay simple | Generalize before promotion | Publish in methodology companion and align with fast-path methodology language | Reusable and highly valuable, but needs method-level wording rather than Ezkey-local prose |

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Leave the source file untouched and unpromoted | No documentation work, no structural change | Keeps valuable generic signal invisible in the public methodology |
| Move `global/design-principles.md` into `methodology/` | Maximizes visibility, single document | Blurs source-project vs methodology boundary and destabilizes the Ezkey canon |
| Keep the Ezkey source file in place, then promote the reusable subset into a new methodology document | Preserves boundary, minimizes restructuring, makes generic signal publishable | Requires one extra companion document and explicit cross-links |

## Decision

Adopt the third option.

Keep `product-docs/global/design-principles.md` where it is as the Ezkey source-project canon.

Create a new methodology companion document that promotes the reusable design-judgment subset into
method-level canon for publication. This companion should:

1. restate the reusable principles in method-level language,
2. avoid live dependency on `product-docs/global/` in public reading flows,
3. complement `analysis-and-design-canon.md` and `methodological-values.md` rather than replace
   them,
4. leave the Ezkey-specific design rules in the source-project canon.

## Consequences

- The public methodology gains a stronger design-trade-off layer without widening the publication
  boundary.
- The Ezkey source-project keeps its own intact product-level canon.
- Readers can distinguish methodology-level design judgment from product-specific doctrine.
- Future source-project principles can be evaluated with the same three-category filter: promote,
  generalize, or keep local.

## Related documents

- [../design-judgment-principles.md](../design-judgment-principles.md)
- [../analysis-and-design-canon.md](../analysis-and-design-canon.md)
- [../methodological-values.md](../methodological-values.md)
- [2026-05-29-methodology-publication-boundary.md](2026-05-29-methodology-publication-boundary.md)

Source-project companion mentioned in context: `product-docs/global/design-principles.md`
