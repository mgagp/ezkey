---
public: true
---
# Public skills integration through a derived site layer

## Date

2026-05-29

## Context

The methodology already depends on named skills for human-AI collaboration and bounded workflow
execution. However, the canonical skill texts live under `.cursor/skills/`, which is appropriate
for operational use but weaker for public discoverability.

The public methodology site already exposed skill concepts indirectly through the AI collaboration
model, the session-start guide, the rich view, and track narration. What was missing was a proper
public navigation surface that exposed the skill system itself without making `.cursor/skills/`
the public corpus of record.

## Working assumptions

- The source of truth for skill behavior should remain `.cursor/skills/`.
- The public site should expose skills for discoverability, traceability, and explanation.
- The public representation should stay derived to avoid dual maintenance.
- Added build complexity is acceptable only if it stays small, explicit, and local to the site pipeline.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep skills only in `.cursor/skills/` | Zero new packaging logic | Public discoverability stays indirect and editor-local |
| Publish `.cursor/skills/` directly as a raw site root | Minimal implementation effort | Leaks editor-local structure and exposes operational text too directly |
| Generate a derived public skills layer during site preparation and package it with the site | Keeps a single source of truth while adding public navigation and explanation | Adds a small transformation step to the site pipeline |

## Decision

Adopt the third option.

The methodology site now packages a **derived public skills layer** generated from `.cursor/skills/`.
This layer is meant for explanation and navigation. It does not replace the canonical operational
skill files.

## Consequences

- The site pipeline gains a small generation step that prepares public skill pages before serving or building the site.
- The public site can expose skills in navigation, search, and track reading without duplicating skill maintenance by hand.
- The site remains method-first: skills are attached to the methodology, not published as a separate framework.
- Future refinement can add richer cards or dedicated visualization without changing the source-of-truth location.

## Related documents

- [`../ai-collaboration-model.md`](../ai-collaboration-model.md)
- [`../README.md`](../README.md)
- methodology site pipeline README
- source-repo `.cursor/skills/README.md`
