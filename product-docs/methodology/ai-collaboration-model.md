# AI Collaboration Model

## Purpose

This document defines how humans and AI agents collaborate using the product-docs structure as shared context.

## Collaboration principles

- Keep context bounded and explicit.
- Prefer linked artifacts over duplicated explanations.
- Separate global intent from component execution detail.
- Preserve an auditable chain of decisions and status changes.

## Roles in collaboration

- **Human owner**: decides priorities, validates trade-offs, approves direction.
- **AI analyst**: expands assumptions, alternatives, and exception paths.
- **AI planner**: helps incubate a live working plan, compare options, and converge before canonical materialization.
- **AI implementer**: executes bounded plan slices and updates artifacts.
- **AI reviewer**: checks quality gates, risks, and traceability integrity.

One person can play multiple roles, but role intent should remain explicit.

## Context establishment protocol

Before analysis or implementation, establish:

1. active idea or tracer bullet ID,
2. affected components,
3. relevant global and component documents,
4. quality gates that must pass.

## Handoff format

Every major handoff should include:

- scope summary,
- decisions taken,
- open questions,
- next action,
- evidence links.

## AI performance guidance

- Use narrow bounded contexts for deep reasoning.
- Use cross-linked complementary artifacts for global coherence.
- Prefer incremental convergence over one-shot large plans.
- When a live working plan is the chosen entrypoint, treat it as a legitimate incubation artifact rather than accidental pre-methodology residue.
