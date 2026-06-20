# Design Principles

## Purpose

This document captures the principles that guide architectural and design trade-offs in Ezkey. They are deliberately short and pragmatic. When a decision is hard, these principles help break the tie; when a decision is easy, these principles explain why.

The principles are product-wide. Components may add their own local principles in `design-decisions.md`, but those must not contradict what is here.

This file remains the Ezkey source-project canon. A promoted methodology-level companion now lives
in `product-docs/methodology/design-judgment-principles.md` for the generic subset that should be
visible in the published methodology product without relocating this source-project document.

## Core Principles

### 1. Simplicity and pragmatism

Prefer the simplest solution that meets the need. Target roughly 80% of the value with 20% of the complexity. Add accidental complexity only when it buys something observable.

### 2. Essential over accidental complexity

Essential complexity (what the feature genuinely needs) is acceptable. Accidental complexity (extra abstraction, indirection, ceremony) must be minimized. If an abstraction does not change outcomes, it does not belong.

### 3. Backend-first integrity

Durable state, verification, and policy decisions live on the backend. Clients observe and request; they do not mutate state on their own. This keeps the trust model explicit and auditable.

### 4. Explicit trust boundaries

Every boundary between components is named and documented. For each boundary, the document answers: who is trusted for what, what is verified, and what happens on failure.

### 5. Developer-first and operator-first

The product is designed from the perspective of the engineer integrating it and the operator running it. Interfaces, documentation, and error messages are written for those audiences first, not for marketing or certification narratives.

### 6. Stable external contracts

External contracts (API responses, error model, signed payload formats) follow widely understood standards (REST, RFC 9457, canonical string serialization) and change with discipline. Generated artifacts (OpenAPI specs, clients) are never hand-edited.

### 7. Stay within the chosen stack

Prefer what developers expect for the chosen stack over novel frameworks. New dependencies require explicit justification in an architecture decision. This keeps long-term maintenance predictable.

### 8. Spec-first, test-driven

Behavior is specified before it is implemented. Specs, mappings, workflows, and error models are documented using this system. Acceptance criteria are observable and mapped to tests through the [traceability matrix](spec-test-traceability.md).

### 9. One canonical place per concept

Every product concept has one canonical home in the documentation. When a concept is referenced elsewhere, a link replaces a duplicate. This is how documents stay truthful over time.

### 10. Admin UI sobriety

The Admin UI is a sober, pragmatic operator tool. It avoids decorative clutter and respects the clear role split: **Global Admin** handles IT-level concerns; **Tenant Admin** handles business-level concerns. Visual identity supports that intent; it does not compete with it.

### 11. Lifecycle without surprise

Lifecycle rules prefer reversible actions first. Irreversible actions (revoke, retire, delete) require a reason and strong guards. No persistent downstream cascade; reactivation restores healthy state cleanly. See [ADR-0004](architecture-decisions.md#adr-0004-lifecycle-without-persistent-cascade) and [`lifecycle-model.md`](lifecycle-model.md).

### 12. Security is not a feature, it is a posture

Security posture is continuous: signed payloads, one-time proof tokens, encryption at rest with rotatable keys, audit chain integrity, and explicit error behavior. There is no "security module" to bolt on; security shows up in every component pack.

### 13. Open-source transparency

Trust is strengthened by inspectable code and documentation. The documentation system exists so every meaningful decision, mapping, and flow is discoverable without re-reading the code.

### 14. Beautiful problems (defer sophistication until earned)

When judging pragmatism and complexity for a feature, ask whether a future problem appears **for the right reasons** — for example, integrity validation becoming slow because the platform handles very high audit volume after real adoption.

- If yes: treat it as a **beautiful problem**. Let it manifest in production evidence before adding sophistication (adaptive windows, record caps, sharded validators, and similar). Document the cut line explicitly.
- If no: limit accidental complexity now. Accept a simpler cut line even if a hypothetical edge case remains unoptimized.

This principle pairs with **#1** (simplicity) and **#2** (essential vs accidental complexity). It does not justify deferring security holes or undefined behavior; it governs **performance and scale refinements** that would otherwise be speculative.

### 15. Simple cases stay simple

Methodology adds traceability and quality — it must not add ceremony for its own sake. When a
topic is well-understood, bounded, and low-risk, the path from ideation to implementation must be
short. Iteration is a tool for managing uncertainty, not a mandatory workflow stage. When
uncertainty is low, move to action.

This applies to the methodology process itself: a simple idea should pass through ideation, scoping,
and a single implementation pass without forced intermediary artifacts or staged planning loops.
The fast path must remain genuinely fast.

## How Principles Apply

When designing or reviewing a change:

- Restate the change intent in one line.
- Identify which principles apply.
- If any principle is in tension with the change, document it as an ADR and explain the trade-off explicitly.
- Keep the change proportional to the intent — do not over-engineer, do not under-specify.

## Related Documents

- [`product-intent.md`](product-intent.md)
- [`architecture-overview.md`](architecture-overview.md)
- [`architecture-decisions.md`](architecture-decisions.md)
- [`lifecycle-model.md`](lifecycle-model.md)
- [`admin-ui-list-quick-security-actions.md`](admin-ui-list-quick-security-actions.md)
