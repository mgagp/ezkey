# Design Principles

## Purpose

This document captures the principles that guide architectural and design trade-offs in Ezkey. They are deliberately short and pragmatic. When a decision is hard, these principles help break the tie; when a decision is easy, these principles explain why.

The principles are product-wide. Components may add their own local principles in `design-decisions.md`, but those must not contradict what is here.

This file remains the Ezkey source-project canon. The generic, publishable subset of these
principles is condensed into the values compass in
[`product-docs/methodology/README.md`](../methodology/README.md).

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

Security posture is continuous: signed payloads, one-time proof tokens, encryption at rest with rotatable keys, audit chain integrity, and explicit error behavior. There is no "security module" to bolt on; security shows up in every component pack. SOC 2 Trust Service Criteria may be used as mapping vocabulary for that discipline; they are not a certification target — see [`normative-posture.md`](normative-posture.md).

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

### 16. External capabilities via SPI; no vendor lock-in in the core

Core function never depends on a specific external vendor. Optional capabilities that require a
third party — SMS, email, immutable archival storage — arrive through a **Service Provider
Interface (SPI)** implemented by **separate peripheral projects** (for example an SMS adapter, an
S3 / object-lock adapter, a Cloudflare R2 adapter). Ezkey must remain **fully operational,
self-contained**, with none of them configured; an operator who declines an SPI simply accepts the
documented limit of doing without it.

This preserves the project's **conceptual integrity and autonomy**: the core stays lean and
provider-neutral, and no single vendor (AWS, Cloudflare, Twilio, …) can become a hard dependency.
When a capability genuinely belongs outside the core, the right answer is an SPI boundary plus a
peripheral implementation — not core code that imports a vendor SDK.

This principle pairs with **#4** (explicit trust boundaries), **#7** (stay within the chosen
stack), and **#13** (open-source transparency). Applied instances:
[`V-2026-0007`](vision/V-2026-0007-sms-integration-spi.md) (SMS),
[`V-2026-0005`](vision/V-2026-0005-email-integration-strategy.md) (email), and
[`V-2026-06-28`](vision/V-2026-06-28-audit-archive-export-spi.md) (audit archive export). The
trade-off discussion that distilled this principle is recorded in
[`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md) and method log
[`backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md`](backlog/method-logs/ML-2026-06-28-integrity-honesty-and-export-spi.md).

### 17. Name fail-open vs fail-closed at critical boundaries

When a component, side effect, or control can fail, name the **failure posture** explicitly:

- **Fail-closed:** the primary operation or path stops (or refuses new work) when the control
  fails. Prefer this when continuing would silently weaken a security, integrity, or trust
  guarantee the product claims.
- **Fail-open:** the primary operation continues when the control fails. Prefer this when
  blocking would harm availability more than the control protects, and when the failure remains
  **observable** (log, metric, alert) rather than silent to operators.

Ask, for each critical boundary: *if this step fails, does the user-visible or security-relevant
path continue or stop — and how do we know?* Record the choice in the brief, TB, ADR, or posture
doc when the trade-off is non-obvious. Do **not** invent a control matrix for every call site;
apply the vocabulary where availability, integrity, or honesty of claims are in tension.

This principle pairs with **#4** (explicit trust boundaries), **#12** (security as posture), and
**#1** / **#2** (keep the analysis light). First product capture that elevated the vocabulary:
[`backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md`](backlog/ideas/I-2026-07-18-audit-log-fail-open-exception-swallow.md).
Honesty surface: [`../../docs/SECURITY_POSTURE.md`](../../docs/SECURITY_POSTURE.md).

## How Principles Apply

When designing or reviewing a change:

- Restate the change intent in one line.
- Identify which principles apply.
- If any principle is in tension with the change, document it as an ADR and explain the trade-off explicitly.
- Keep the change proportional to the intent — do not over-engineer, do not under-specify.
- When a side effect or control can fail, name **fail-open** vs **fail-closed** (principle **#17**)
  if availability and integrity/honesty pull in different directions.

## Related Documents

- [`product-intent.md`](product-intent.md)
- [`architecture-overview.md`](architecture-overview.md)
- [`architecture-decisions.md`](architecture-decisions.md)
- [`lifecycle-model.md`](lifecycle-model.md)
- [`admin-ui-list-quick-security-actions.md`](admin-ui-list-quick-security-actions.md)
