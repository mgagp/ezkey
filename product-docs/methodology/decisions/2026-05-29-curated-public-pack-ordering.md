---
public: true
---
# Keep curated public pack ordering conceptual and stable

## Date

2026-05-29

## Context

The public methodology explorer had already benefited from replacing alphabetical skill ordering
with a methodology-first sequence. The same structural problem remained in the `methodology` and
`templates` sections: high-value entry points such as `README.md` and `minimum-viable-method.md`
were buried behind alphabetical neighbors, which increased cognitive friction and made the pack
feel less intentional than it actually is.

## Source signal (optional)

- "on a exactement le meme phenomene"
- "c'est vraiment majeur au niveau du confort cognitif"
- "il faudra en tirer une regle claire"

## Working assumptions

- These packs are curated guidance surfaces, not neutral file browsers.
- Readers benefit more from a stable conceptual journey than from alphabetical predictability.
- Future contributors need a small explicit rule so the order does not drift from one session to
  another.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| Keep alphabetical ordering | Zero maintenance, mechanically obvious | Hides entry points, weakens the intended method narrative, recreates the same cognitive friction repeatedly |
| Reorder ad hoc whenever someone notices drift | Low upfront effort | Unstable over time, no convergence rule for humans or AI agents |
| Maintain an explicit conceptual order per curated pack | Stable reader journey, preserves README-first orientation, produces repeatable future decisions | Requires a small maintained ordering rule |

## Decision

Use explicit conceptual ordering for curated packs in the public methodology explorer.

The default rule is:

1. `README.md` first.
2. Then the fastest entry and orientation docs.
3. Then the main working flow.
4. Then specialized variants or alternate lanes.
5. Then reference material.
6. Then examples, decision logs, or archives.

For templates, apply the same logic with template families: ideation-to-delivery first, then
component and boundary design, then governance records, then global product framing.

New items should be inserted into an existing conceptual bucket rather than appended by
alphabetical order.

## Consequences

- The site navigation now uses explicit public ordering for `methodology`, `templates`, and
  `skills`.
- `README.md` consistently opens each curated pack in the explorer.
- The methodology and templates READMEs now describe the same grouping logic used by the public
  explorer.
- `AGENTS.md` carries the compact rule so a cold-start agent or tired human can preserve the same
  ordering model later.

## Related documents

- [../README.md](../README.md)
- [../../templates/README.md](../../templates/README.md)
- repo-wide `AGENTS.md`
- [../../site/server.js](../../site/server.js)
