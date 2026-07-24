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

**Channel send actions (email, SMS):** adapter/SMTP **configuration** = Global Admin (TI). **Send** in enrolment/activation workflows = **Global Admin or Tenant Admin** — whichever role operates that workflow in the deployment geometry below.

## Admin UI copy posture

Use a simple role-aware vs role-agnostic split for operator-facing copy:

- **Post-login shell identity is role-aware** (for example, Global Admin vs Tenant Admin labels in the main console chrome).
- **Pre-auth product messaging is role-agnostic** (shared login-level positioning that does not assume operator role before session context exists).

When copy can become inaccurate across scopes, prefer neutral wording such as "within your scope" over tenant-only phrasing.

## Deployment operator geometries

Ezkey supports **two common adopter shapes**. Both are valid product postures; neither requires platform “profile code” (`V-2026-0010`). Phase 2 profile elaboration documents which geometry applies to a given installation.

### All-in-one (simplified PME)

| Aspect | Typical pattern |
|--------|-----------------|
| **Operators** | One or more **Global Admins** only (e.g. primary + backup when primary is away) |
| **Tenants** | Optional; operator may use **System Tenant** and skip tenant-admin ceremony |
| **Day-to-day** | Global Admin creates integrations, API keys, enrollments, and may trigger channel sends (email/SMS) directly |
| **Analogue** | Single IT/generalist runs the whole Ezkey instance on-prem |
| **When it fits** | Small team, one integration, pragmatism over role separation |

### Segmented (role-separated)

| Aspect | Typical pattern |
|--------|-----------------|
| **Operators** | Multiple **Global Admins** (platform/IT) + one or more **Tenant Admins** per business unit |
| **Tenants** | Explicit tenant boundaries mirroring org structure |
| **Day-to-day** | Tenant Admin owns integrations, enrollments, API keys for their tenant; Global Admin owns platform health, crypto, integrity, channel **configuration** |
| **Analogue** | IT runs the platform; DevOps/business admins run integrations per unit |
| **When it fits** | Richer org chart, separation of duties, multi-tenant production |

### Design consequences (shared)

- **Do not force** tenant-admin creation or a third API binary when the adopter chose all-in-one — same principle as minimal Admin+Auth install and API-key opt-in (`I-2026-0004`).
- **Do not hide** Global Admin from domain workflows when they legitimately operate all-in-one (integrations, enrollments, send-by-channel).
- **Marketing / presales:** these geometries are the human-facing story behind deployment profiles; Phase 2 elaboration (`I-2026-0017`) captures client-specific choices; Phase 3 (`I-2026-0018`) generates config artefacts.

See also: [`product-intent.md`](product-intent.md) (audiences), [`vision/product-orientation-notes.md`](vision/product-orientation-notes.md) (`V-2026-0010`).

For an adopting SME or team, **Ezkey is never the core business**. It is the pragmatic way to run strong authentication — a necessary capability, not the product they sell or operate daily.

Design consequences:

- **Dashboard (3-second test):** Does it work or not? If not, one obvious control leads to understanding and resolution quickly.
- **Investigation and configuration:** Minimize ceremony; prefer self-contained Ezkey over external ITSM inside the product.
- **Operational quality bar:** Prefer **invisible when healthy** — *it just works* — over rich observability platforms duplicated inside Ezkey (no mini-Grafana, no alert-on-alert).
- **When unhealthy:** Be explicit and actionable (alerts, banners, widgets with scope) without nagging beyond visible state.

This posture pairs with **Design Principle #1** (simplicity), **#5** (operator-first), and **#14** (beautiful problems — defer scale tooling until adoption earns it).

## List quick security actions

During a suspected compromise, operators must cut credential exposure **without hunting through
detail pages**. Paginated lists may include a **trailing actions column** only for incident-relevant
controls (API key revoke, admin deactivate; enrollment deactivate planned).

Full eligibility matrix, icon vs label guidance, and anti-patterns:
[`admin-ui-list-quick-security-actions.md`](admin-ui-list-quick-security-actions.md).

## Usage in workflow

- Reference in `grill-me` and component design packs when role visibility or dashboard copy is in question.
- Promote concrete rules into RBAC docs or Admin UI specs when a feature ships; this guide stays stable orientation text.
