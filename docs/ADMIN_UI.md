# Ezkey Admin UI

## Purpose

The Ezkey Admin UI is the primary human administration surface for operating Ezkey day to day.

It exists to give administrators a practical, browser-based way to manage tenants, integrations, enrollments, administrator accounts, API keys, audit visibility, and selected platform operations without changing Ezkey's backend-first trust model.

The UI is important because it makes the system operable. It is not the root of trust. Trust, authorization, cryptographic verification, and durable state remain authoritative on the backend.

## Who It Is For

The Admin UI serves two operator audiences with different responsibilities.

### Global Admin

Global Admins handle instance-wide and cross-tenant concerns, including:

- tenant administration,
- platform-level oversight,
- sensitive operational actions such as encryption key operations,
- visibility across the broader administrative surface.

This is the IT-style role in Ezkey.

### Tenant Admin

Tenant Admins handle tenant-scoped administration, including:

- integrations,
- enrollments,
- administrator lifecycle within their tenant,
- API keys,
- day-to-day security and operational follow-up for their business scope.

This is the business-oriented operator role in Ezkey.

## Design Position

The Admin UI follows the same philosophy as the rest of Ezkey:

- **Pragmatic**: optimized for real operator tasks rather than decorative administration screens.
- **Backend-first**: the UI exposes and drives backend capabilities; it does not replace backend authority.
- **Role-aware**: the visible surface adapts to the logged-in administrator type.
- **Self-hosted and inspectable**: the UI belongs to an operationally visible, self-hosted product surface.

The intent is not to build an exhaustive wizard-driven console that explains every field in documentation. Ezkey prefers concise external documentation plus contextual help in the product where that is the better fit.

## Capability Map

The Admin UI currently covers these main areas.

### Access and session

- passwordless administrator login,
- challenge-based approval when required,
- recovery-code funnel for break-glass access,
- session management and logout.

### Tenant administration

Available to Global Admins:

- list and inspect tenants,
- create tenants,
- update tenant details,
- activate and deactivate tenants.

### Integration administration

Available for day-to-day tenant operations:

- list and inspect integrations,
- create integrations,
- manage lifecycle actions,
- use integrations as the parent scope for enrollments and API keys.

### Enrollment administration

- list and inspect enrollments,
- create and update enrollments,
- review lifecycle state,
- test authentication against a bound device,
- deactivate, reactivate, revoke, or otherwise manage enrollment lifecycle according to backend rules.

### Administrator administration

- list and inspect administrators,
- create peer administrators within allowed scope,
- retrieve onboarding material through the supported workflow,
- activate, deactivate, and manage administrator lifecycle,
- regenerate recovery codes when policy allows.

### Authentication visibility

- list authentication attempts,
- inspect current and historical attempt state,
- follow operational outcomes without using raw API traffic.

### Audit visibility

- inspect audit logs,
- support security review and operational investigation,
- provide a day-to-day human view of security-relevant events.

### API key administration

- create API keys for integrations,
- inspect API key state and metadata,
- update allowed configuration,
- revoke keys,
- support operational rotation workflows.

### Encryption key operations

Available to Global Admins:

- inspect encryption keys,
- trigger or monitor selected key lifecycle actions,
- review re-encryption batch activity through the administrative surface.

## Typical Operator Workflows

The Admin UI should mainly be understood through a small set of recurring workflows rather than through a screen-by-screen manual.

### 1. Sign in and establish an operator session

An administrator enters their username, approves the request in the Ezkey mobile application, and enters a challenge when required. If the device is unavailable, the recovery-code funnel allows a constrained recovery path that leads to enrollment reset rather than a normal full console session.

### 2. Establish a tenant and its operating space

A Global Admin creates or updates a tenant, then uses the Admin UI to manage the tenant's working surface and administrative boundaries.

### 3. Create and manage integrations

A Global Admin or Tenant Admin creates integrations, inspects them later, and manages their lifecycle as part of the tenant's day-to-day administration.

### 4. Create and manage enrollments

Operators create enrollments, monitor their lifecycle, troubleshoot binding or verification status, and manage enrollment changes when users or devices change.

### 5. Provision and maintain administrator access

Administrators create peer admins, hand off onboarding information through the supported flow, preserve recovery-code discipline, and manage activation or deactivation over time.

### 6. Manage API credentials for integrated systems

Operators create API keys, communicate the one-time secret safely, rotate keys when needed, and revoke keys that should no longer be used.

### 7. Investigate security and operational activity

Operators use audit logs and authentication-attempt views to answer questions such as:

- who logged in,
- which actions occurred,
- what failed,
- what requires follow-up.

### 8. Perform platform-sensitive operations

Global Admins use the platform-focused parts of the UI for actions such as encryption key review, rotation-related follow-up, and re-encryption monitoring.

## What This Document Covers vs Other Docs

This document is intentionally the middle layer between product philosophy and detailed reference.

### Use this document for

- understanding the purpose of the Admin UI,
- understanding the role split,
- understanding what the UI is responsible for,
- orienting operators and contributors around the main workflows,
- understanding where to go next in the documentation set.

### Use other docs for

- **API contract and request/response detail**: [`ENDPOINT.md`](ENDPOINT.md)
- **browser security, CSP, token handling, and deployment model**: [`admin-ui-security.md`](admin-ui-security.md)
- **recovery-code and reset behavior in the login experience**: [`ADMIN_UI_RECOVERY.md`](ADMIN_UI_RECOVERY.md)
- **encryption-key rotation and re-encryption operations**: [`REENCRYPTION_OPERATIONS.md`](REENCRYPTION_OPERATIONS.md)
- **broader product framing and backend-first philosophy**: [`../PRD.md`](../PRD.md) and [`PROJECT_POSITIONING.md`](PROJECT_POSITIONING.md)
- **system architecture and trust placement**: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- **local development and module setup**: [`../ezkey-admin-ui/README.md`](../ezkey-admin-ui/README.md)

## What This Document Deliberately Avoids

To keep the documentation maintainable and non-duplicative, this page does not try to provide:

- a description of every screen and every visible field,
- a restatement of endpoint-level contracts,
- a replacement for in-product contextual help,
- deep implementation detail about frontend internals.

That material either belongs in specialized documentation or directly in the product experience.

## Recommended Reading Paths

### If you are operating Ezkey

1. Read this page.
2. Read [`admin-ui-security.md`](admin-ui-security.md) for deployment and browser-security expectations.
3. Read [`ENDPOINT.md`](ENDPOINT.md) only when you need API-level detail behind a UI workflow.

### If you are integrating or extending Ezkey

1. Read this page.
2. Read [`../PRD.md`](../PRD.md), [`PROJECT_POSITIONING.md`](PROJECT_POSITIONING.md), and [`ARCHITECTURE.md`](ARCHITECTURE.md).
3. Use [`ENDPOINT.md`](ENDPOINT.md) as the detailed backend contract reference.

## Summary

The Admin UI is Ezkey's primary operator console, but not a separate source of truth. It gives Global Admins and Tenant Admins a practical operational surface over the same backend-authoritative security model that defines the rest of the product.

That is the intended balance: a real administration console, backed by explicit APIs and trust boundaries, documented enough to be understandable without turning the repository into a duplicate user manual.
