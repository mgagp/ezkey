# AI Collaboration Model

## Purpose

This document defines how humans and AI agents collaborate using the product-docs structure as shared context.

## Collaboration principles

- Keep context bounded and explicit.
- Prefer linked artifacts over duplicated explanations.
- Separate global intent from component execution detail.
- Preserve an auditable chain of decisions and status changes.
- Preserve internal methodological integrity: each lane and skill should make its state boundaries
  clear enough for a later human or agent to resume without hidden memory.

## Roles in collaboration

- **Human owner**: decides priorities, validates trade-offs, approves direction.
- **AI analyst**: expands assumptions, alternatives, and exception paths.
- **AI planner**: helps incubate a live working plan, compare options, and converge before canonical materialization.
- **AI implementer**: executes bounded plan slices and updates artifacts.
- **AI reviewer**: checks quality gates, risks, and traceability integrity.

One person can play multiple roles, but role intent should remain explicit.

## Context establishment protocol

Before analysis or implementation, establish:

1. active anchor: methodology decision topic, active idea, tracer bullet, working plan, or observed existing behavior,
2. affected components or methodology surfaces,
3. relevant global and component documents,
4. quality gates that must pass.

When the session starts from existing implemented behavior, classify early whether the work is a
pure local technical defect or a corpus-level intent change that requires re-entry through `TB-*`,
`I-*`, or `V-*`.

## Skill boundary protocol

Each project skill should define a small boundary contract:

- **Enter when** the condition that makes the skill useful is true.
- **Exit when** the skill has produced the evidence or state transition it owns.
- **Call next** names the usual follow-up skill or workflow step.
- **Not needed when** preserves the fast path for simple or already-settled work.

These contracts keep the methodology navigable without turning it into a heavy process engine.

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
