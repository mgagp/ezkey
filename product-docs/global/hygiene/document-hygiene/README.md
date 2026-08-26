# Document hygiene curated — campaign notes

Lightweight HITL decision track for punctual `document-hygiene-curated` passes.

This lane is for **legacy documents, duplicate reports, and historical notes** whose live substance
may already exist elsewhere. It answers one question at a time: should this document remain
canonical, be folded into canon, stay as a historical companion, or be retired?

Use this lane when the operator is doing document archaeology, consolidation, or archive-vs-delete
decisions. Do **not** use it for plan scaffold pruning (`corpus-ablation`) or for code-level
investigations (`assessment-curated`).

Example pattern: when the live mechanics already live in a canonical guide and the operator-facing
decision matrix already lives elsewhere, a standalone audit/reporting document is usually a
historical companion or delete candidate, not the source of truth.

If a newcomer can recover the same practical answer from the canonical sources plus Git history,
prefer delete over leaving a stub unless the stub itself materially improves discoverability.

## Invoke

- Cursor skill: [`.cursor/skills/document-hygiene-curated/SKILL.md`](../../../.cursor/skills/document-hygiene-curated/SKILL.md)
   (keyword **`document-hygiene-curated`**)
- Root pointer: [`AGENTS.md`](../../../AGENTS.md) keyword table

## Discovery criteria

The pass should explicitly check:

1. **Canonicality** — does the document still govern current behavior, policy, or a live decision?
2. **Discoverability** — can a cold agent find the substance elsewhere in canon, code, or Git
   history without this copy?
3. **Residual value** — does the file preserve a useful historical trace, migration note, or
   archive snapshot?
4. **Archive sufficiency** — would Git history be enough if this file disappeared?
5. **Habilitation fit** — is the document mostly a workaround for older-model discoverability rather
   than a current source of truth?

## Dispositions

| Disposition | Meaning |
|------------|---------|
| Keep canonical | The file remains the source of truth. |
| Fold into canon | Move the live substance into the canonical doc, then retire the legacy copy. |
| Archive as historical companion | Keep it only when the trace itself is valuable and the legacy status is explicit. |
| Delete | The document is redundant and the signal is already preserved elsewhere. |
| Retarget pointers | Keep a short stub or pointer when the old path still helps discovery. |

## Related lanes

| Path | Keyword | Role |
|------|---------|------|
| [`assessment-curated/`](../assessment-curated/) | `assessment-curated` | White-box investigation hygiene and handoffs |
| [`corpus-ablation/`](../corpus-ablation/) | `corpus-ablation` | Ephemeral scaffold pruning into canon |
| [`react-doctor/`](../react-doctor/) | `doctor-curated` | Admin UI React hygiene |
| [`java-doctor/`](../java-doctor/) | `java-doctor-curated` | Java static-analysis hygiene |
| [`dependabot/`](../dependabot/) | `dependabot-curated` | Weekly dependency triage |

## Campaign structure

Each pass should record:

- the document under review,
- the canonical neighbor(s),
- the decision and rationale,
- what changed in canon, if anything,
- whether the legacy copy was retired or kept as a historical companion.

Use the template for dated campaign instances.
