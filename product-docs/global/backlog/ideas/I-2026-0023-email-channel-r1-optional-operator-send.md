# Backlog Idea — `I-2026-0023` Email channel R1: optional operator-triggered delivery

## Metadata

- **ID:** `I-2026-0023`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-05-19`
- **Updated at:** `2026-05-24`
- **Last reviewed at:** `2026-05-24`
- **Phase tags:** `P1-operability`
- **Component tags:** `admin-api`, `admin-ui`, `infra`, `docs`

## Intent

Implement the **R1 email channel slice** from `V-2026-0005`: optional, **operator-triggered** send for enrolment and admin-activation workflows; Java Mail + SMTP configuration (Global Admin); repositioned disclaimers; contextual help for **integrated delivery** (manual external channels). Ezkey stays operational without SMTP or after send failure.

## Problem and value

- **Problem:** Disclaimers today read like temporary warnings; operators lack a deliberate, documented choice between integrated on-screen delivery and SMTP-assisted delivery.
- **Expected value:** Normative clarity for PME deployments; email when wanted without forcing SMTP; honest fallback to minimized on-screen QR.

## Scope

- **In scope (R1):**
  - SMTP configuration surface (Global Admin only).
  - **Send by email** action on: enrolment invite material, admin activation code (both optional per operator).
  - Email body: QR visible + short instruction text.
  - SMTP failure: error message; on-screen QR remains available (minimized).
  - Disclaimer copy update (educational, posture-aware — not “coming soon”).
  - Contextual help: integrated mode allows manual external channel (copy, drag-drop, Teams, etc.).
  - Documentation of **integrated delivery** vs **SMTP-assisted delivery** postures (see grill session D7-6).
- **Out of scope (R1):**
  - Automatic send on workflow events.
  - HTML templates / branding polish.
  - SMS (`V-2026-0007`).
  - Other email workflows (recovery, notifications) beyond enrolment + admin activation.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md`](../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md).

## Promotion notes

Move to `ready` when design pack outlines Admin API mail service + UI wireframes. Depends on SMTP property design in `CONFIGURATION.md`.

## Links

- Vision: `V-2026-0005`
- Grill: `../grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md`
- Profiles: `V-2026-0010`
- Guide: `../../operator-alignment-guide.md`
