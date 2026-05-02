# Roadmap — `<product-name>`

> Template for `global/roadmap.md`. Remove this blockquote on instantiation.

## Purpose

State why this roadmap exists and how it should be read. One short paragraph.

## Reading Model

- **Phases** group work under a single intent and timeframe.
- **Features** inside a phase describe observable capabilities. Detailed descriptions belong in [`features-and-phases.md`](features-and-phases.md).
- This document is the current truth; it evolves but is never a detailed history.

## Phases Overview

| Phase | Intent | Status | Primary outcomes |
|-------|--------|--------|------------------|
| `<phase-id>` | `<one-line intent>` | `planned` | `<outcomes>` |

## Phase Detail

### Phase `<phase-id>` — `<phase name>`

**Intent.** One paragraph describing what this phase sets out to accomplish.

**Status.** `planned` / `in-progress` / `implemented`.

**Key features.**

- `<feature-id>` — short description. Details: [`features-and-phases.md#<feature-id>`](features-and-phases.md#<feature-id>).

**Dependencies.**

- Describe dependencies on other phases, components, or external factors.

**Exit criteria.**

- Observable conditions under which the phase is considered complete.

## Long-Term Horizon

Short section listing directional themes that are not yet planned phases but that shape decisions (for example: platform maturity, compliance targets, ecosystem expansion).

## Related Documents

- [`product-intent.md`](product-intent.md)
- [`features-and-phases.md`](features-and-phases.md)
- [`architecture-overview.md`](architecture-overview.md)
