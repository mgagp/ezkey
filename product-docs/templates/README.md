# Templates

Four templates, kept after the 2026-08 methodology ablation because each maps to one of the
documentary levels in [`../methodology/README.md`](../methodology/README.md). See that document's
ablation note for what used to be here.

- [vision-note.template.md](vision-note.template.md) — `V-*` directional product orientation.
- [backlog-idea.template.md](backlog-idea.template.md) — `I-*` one concrete idea.
- [tracer-bullet-brief.template.md](tracer-bullet-brief.template.md) — `TB-*` bounded execution slice.
- [architecture-decision.template.md](architecture-decision.template.md) — `ADR-*` design decision, global or component-scoped.
- [cold-agent-plan-review.prompt.md](cold-agent-plan-review.prompt.md) — Prompt template for fresh-session plan review and hardening before cold-agent execution.
- [ui-walk-gate.template.md](ui-walk-gate.template.md) — Admin UI walk/done gate checklist for Isabelle (and intention owners).

## Using a template

Copy it into the target document, fill it in, and **delete sections that do not apply** rather than
leaving them empty. Link to specs, tests, and decisions instead of duplicating their content.

## Reusing outside Ezkey

The templates are product-agnostic. Copy `product-docs/templates/` and
[`../methodology/README.md`](../methodology/README.md) into the target repository, replace
product-specific vocabulary, and recreate the `global/` and `components/` skeletons the templates
point to.
