---
name: document-hygiene-curated
description: >-
  Reviews legacy or duplicate documents for canonicality, discoverability, residual historical
  value, and whether Git history plus existing canon already carry the signal. Use when the
  operator asks for a document-hygiene pass, document archaeology, archive-vs-delete decisions,
  or to decide whether a report, note, migration record, or legacy plan should stay canonical, be
  folded into canon, or be retired.
disable-model-invocation: true
---

# Document hygiene curated

## Purpose

Turn one document at a time into a clear disposition: keep canonical, fold into canon and delete
legacy copies, archive only when the file itself adds distinct historical value, delete when the
signal already lives elsewhere, or retarget pointers so a cold agent can find the substance
quickly.

This is **hygiene**, not program. Do **not** invent `I-*` / `TB-*` per document. Prefer the lightest
change that protects canonicality and discoverability.

## Boundary contract

- **Enter when:** the operator says `document-hygiene-curated`, asks for document archaeology, or
  wants to decide whether a legacy note or report should remain, be folded into canon, or be
  removed.
- **Exit when:** the document has a disposition and, when needed, the canonical target has been
  updated, the legacy copy has been removed or clearly marked as historical, and a dated campaign
  note exists under `product-docs/global/hygiene/document-hygiene/`.
- **Call next:** a normal editor or implementation pass for any canonical document that needs a
  rewrite after the disposition is chosen.
- **Not needed when:** the work is really plan/scaffold pruning (`corpus-ablation`) or a broader
  investigation pass with code-level findings (`assessment-curated`).

## Authority

- Lane notes: [`product-docs/global/hygiene/document-hygiene/README.md`](../../product-docs/global/hygiene/document-hygiene/README.md)
- Campaign template: [`product-docs/global/hygiene/document-hygiene/TEMPLATE.md`](../../product-docs/global/hygiene/document-hygiene/TEMPLATE.md)
- Hygiene index: [`product-docs/global/hygiene/README.md`](../../product-docs/global/hygiene/README.md)
- Methodology: [`product-docs/methodology/README.md`](../../product-docs/methodology/README.md) § *Three rules worth keeping*
- Root keyword: `AGENTS.md` keyword table
- Copilot mirror: `.github/copilot-instructions.md` § Code hygiene keywords

## Decision criteria

| Criterion | Question | Typical outcome |
|----------|----------|-----------------|
| Canonicality | Does this document currently define or govern behavior, policy, or a decision that remains live? | Keep or promote into the canonical place |
| Discoverability | Can a cold agent find the substance elsewhere through canon, code, or Git history without this copy? | Fold, retarget, or delete the redundant copy |
| Residual value | Does the file preserve historical rationale, migration trace, or a useful legacy snapshot that would be lost otherwise? | Keep only as a clearly marked historical companion |
| Archive sufficiency | Would Git history already preserve the evidence if the file disappeared? | Prefer deletion once canonical substance is captured |
| Habilitation fit | Is the document mostly a crutch for older-model discoverability rather than a current source of truth? | Delete or reduce to the smallest possible pointer |

## Default decision order

1. **Keep canonical** only when the document still governs a live decision.
2. **Fold into canon** when the live substance belongs in a better canonical home.
3. **Delete** when the same signal is already present in canon and Git history.
4. **Archive as historical companion** only when the file itself adds a distinct trace.
5. **Retarget pointers** only when the old path still helps a cold agent land on the canon.

If two outcomes are plausible, prefer the one that removes duplicated prose while preserving the
smallest reliable entry path.

## Per-document loop (HITL)

1. Point at one document.
2. Read the document and the nearest canonical neighbors, but do not widen scope needlessly.
3. Classify one of five dispositions:
   - **Keep canonical** — it is still the source of truth.
   - **Fold into canon** — move the live substance into the right canonical doc, then delete or mark the legacy copy.
   - **Delete** — redundant and discoverability is already preserved elsewhere.
  - **Archive as historical companion** — keep only if the trace itself is useful and the file is clearly labelled legacy.
   - **Retarget pointers** — keep a short stub that points to the real canon when the old path still has value as a doorway.
4. Brief the operator with the single best package, not a menu of equally weighted options.
5. Wait for Go / Keep / archive / delete / rewrite before editing.

## Operating rule

Git history is the archive. Do not preserve redundant documents just to satisfy traceability if the
substance already lives in a better canonical place. A stub is not a compromise by default; it must
earn its keep as an actual entry path.

## Kickoff

```text
document-hygiene-curated — review one legacy document.
Read .cursor/skills/document-hygiene-curated/SKILL.md, then the lane README and the target
file. Apply the decision criteria: canonicality, discoverability, residual value, archive
sufficiency, habilitation fit. One document per turn. Wait for Go.
```
