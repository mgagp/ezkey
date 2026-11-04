# Backlog Idea — `I-2026-0023` Email channel R1: optional operator-triggered delivery

## Metadata

- **ID:** `I-2026-0023`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-11-04`
- **Last reviewed at:** `2026-11-04`
- **GitHub issue:** `#1206`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-api`, `admin-ui`, `infra`, `docs`
- **Captured by:** Marc

## Direction change (2026-11-04)

Marc decided on `#1206` that email goes through a **connector** (`V-2026-11-04-connector-spi`, ADR-0017). There is no Java Mail in the core; SMTP credentials stay with the connector. The email carries the **QR**; the challenge stays on screen, or goes by SMS later. The sections below are adjusted accordingly.

## Intent

Implement the **R1 email channel slice** from `V-2026-0005`: optional, **operator-triggered** send for enrolment and admin-activation workflows; through an email connector configured by the Global Admin; repositioned disclaimers; contextual help for **integrated delivery** (manual external channels). Ezkey stays operational without SMTP or after send failure.

## Slice status (2026-07-25)

| Slice | Status | Artifact |
|-------|--------|----------|
| **Integrated delivery posture** (Admin UI notices + help + canon; no SMTP) | **Done** (2026-07-25); UX: collapsed disclosure summary | [`TB-2026-07-25-integrated-delivery-posture`](../TB-2026-07-25-integrated-delivery-posture.md) |
| **Email-assisted delivery** (connector + Send by email + mail body) | **Deferred** — remains the open R1 half of this idea | Same `I-2026-0023` |

After the posture TB closes, keep this idea `incubating` until email R1 lands through the connector (`#1206`).

**Admin UI presentation (posture slice):** educational copy lives behind a collapsed
`IntegratedDeliveryNotice` label (“Integrated delivery” / “Livraison intégrée”; bootstrap
variant on activation). Full body expands on click — same honesty as D7, lighter daily dialogs.

## Problem and value

- **Problem:** Disclaimers today read like temporary warnings; operators lack a deliberate, documented choice between integrated on-screen delivery and email-assisted delivery.
- **Expected value:** Normative clarity for PME deployments; email when wanted without forcing SMTP; honest fallback to minimized on-screen QR.

## Scope

- **In scope (R1 — full idea):**
  - Email connector and its configuration (Global Admin only); SMTP credentials stay with the connector.
  - **Send by email** action on: enrolment invite material, admin activation code (both optional per operator).
  - Email body: QR visible + short instruction text; **never the challenge**.
  - Send failure: error message; on-screen QR remains available (minimized).
  - Disclaimer copy update (educational, posture-aware — not “coming soon”).
  - Contextual help: integrated mode allows manual external channel (copy, drag-drop, Teams, etc.).
  - Documentation of **integrated delivery** vs **email-assisted delivery** postures (see grill session D7-6).
- **In scope (posture TB only):** Admin UI copy, alert severity, help, canon notes — **no** SMTP.
- **Out of scope (R1):**
  - Automatic send on workflow events.
  - HTML templates / branding polish.
  - SMS (`V-2026-0007`).
  - Other email workflows (recovery, notifications) beyond enrolment + admin activation.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md`](../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md).

## Promotion notes

Mail half: executes as steps 3–5 of `#1206`, after the delivery contract (step 2). Posture half executes via `TB-2026-07-25-integrated-delivery-posture` without waiting for that pack.

## Links

- Vision: `V-2026-0005`, `V-2026-11-04-connector-spi`
- Grill: `../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md`
- Posture TB: `../TB-2026-07-25-integrated-delivery-posture.md`
- Profiles: `V-2026-0010`
- Guide: `../../operator-alignment-guide.md`
