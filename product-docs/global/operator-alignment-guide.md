# Operator Alignment Guide

## Purpose

This document captures **structural analogies** and **adopter posture** that guide product and UI decisions. Use it during backlog triage, grill-me sessions, Admin UI design, and role-based visibility choices so human and AI collaborators converge quickly without re-deriving intent each session.

Related: [`product-intent.md`](product-intent.md) (audiences), [`design-principles.md`](design-principles.md) (#5 operator-first, #10 Admin UI sobriety).

## Admin role analogies

| Role | Analogue (adopter organization) | Typical concerns | Ezkey surface |
|------|----------------------------------|------------------|---------------|
| **Global Admin** | **IT / platform operations** (infrastructure team) | Environment health, cryptography, audit integrity, alerts, key rotation, deployment posture | Full platform ops: integrity remediation, alert resolution, snooze, crypto batches, system widgets |
| **Tenant Admin** | **DevOps-style bridge** between development and IT | Integrations, enrollments, API keys, day-to-day tenant administration | Business-oriented admin; **no** crypto-ops or integrity-remediation surfaces |

These analogies are **orientation tools**, not permission matrices. Authoritative rules remain in lifecycle governance, RBAC implementation, and component packs.

**Decision reflex:** when a feature touches heartbeat, integrity validation, manipulation remediation, encryption keys, or platform-wide alerts → default **Global Admin only**. When it touches integration/enrollment/API-key workflows → Tenant Admin is in scope; confirm whether Global Admin also needs visibility.

## Adopter posture: Ezkey is not core business

For an adopting SME or team, **Ezkey is never the core business**. It is the pragmatic way to run strong authentication — a necessary capability, not the product they sell or operate daily.

Design consequences:

- **Dashboard (3-second test):** Does it work or not? If not, one obvious control leads to understanding and resolution quickly.
- **Investigation and configuration:** Minimize ceremony; prefer self-contained Ezkey over external ITSM inside the product.
- **Operational quality bar:** Prefer **invisible when healthy** — *it just works* — over rich observability platforms duplicated inside Ezkey (no mini-Grafana, no alert-on-alert).
- **When unhealthy:** Be explicit and actionable (alerts, banners, widgets with scope) without nagging beyond visible state.

This posture pairs with **Design Principle #1** (simplicity), **#5** (operator-first), and **#14** (beautiful problems — defer scale tooling until adoption earns it).

## Usage in workflow

- Reference in `ezkey-grill-me` and component design packs when role visibility or dashboard copy is in question.
- Promote concrete rules into RBAC docs or Admin UI specs when a feature ships; this guide stays stable orientation text.
