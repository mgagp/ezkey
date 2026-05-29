---
public: true
---
# Minimum Viable Method as an explicit entry track

## Date

2026-05-29

## Context

An evaluation of the methodology found that its conceptual quality and internal rigor were strong,
but that its entry cost remained higher than some popular methodologies that are better packaged
for first contact.

The issue was not missing substance. The issue was accessibility: the methodology had a strong full
form, but lacked an explicit lightweight path that a newcomer could grasp in minutes.

The goal of this refinement was to improve accessibility without diluting rigor or creating a new
framework layer.

## Source signal (optional)

- "Avoir une track qui va exposer rapidement un Minimum Viable Method, ça, je crois que ça a vraiment une valeur."
- "plusieurs méthodologies populaires ont comme qualité d'être mieux documentées, plus accessibles"

## Working assumptions

- The methodology should stay project-backed and lightweight.
- Accessibility should improve through better entry packaging, not through a larger artifact graph.
- A newcomer-friendly path must still route into the same canonical methodology, not a parallel one.
- The explorer tracks and action buttons are part of the methodology entry experience and must stay aligned with source docs.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep the current entry only | No extra documentation surface | Leaves first-contact accessibility weaker than it should be |
| Add a lightweight entry document only | Improves source readability | Misses the explorer track and button integration |
| Add a Minimum Viable Method document and expose it as a dedicated explorer track | Improves accessibility in both source and explorer without changing the method core | Requires coordinated updates across docs and site track surfaces |

## Decision

Adopt the third option.

The methodology now includes an explicit **Minimum Viable Method**: a short source document plus a
dedicated **Start** track in the local explorer.

This entry is not a second methodology. It is a lightweight front door into the same methodology.

## Consequences

- New source document: `product-docs/methodology/minimum-viable-method.md`.
- Methodology entry docs now point to the minimum viable path before the full reading path.
- Explorer tracks now include a dedicated `start` track.
- Explorer topbar buttons, home cards, and styles are updated so the new track is reachable from the same action surfaces as Discover / Apply / Present.

## Related documents

- [`../minimum-viable-method.md`](../minimum-viable-method.md)
- [`../README.md`](../README.md)
- [`../workflow-overview.md`](../workflow-overview.md)
- [`../session-start-guide.md`](../session-start-guide.md)
- [`../../site/tracks.json`](../../site/tracks.json)
