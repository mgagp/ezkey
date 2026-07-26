# Tracer Bullet Brief — `TB-2026-07-25-integrated-delivery-posture` Integrated delivery posture (Admin UI)

## Metadata

- **ID:** `TB-2026-07-25-integrated-delivery-posture`
- **Status:** `done`
- **Related idea:** `I-2026-0023`
- **Lane:** `D`
- **Posture:** `single-pass`
- **Created at:** `2026-07-25`
- **Updated at:** `2026-07-25`
- **Closed at:** `2026-07-25`
- **Captured by:** Marc
- **GitHub issue:** _(none — canon sufficient; optional issue deferred)_

## Objective

Prove that Admin UI notices for enrollment QR / binding challenge / onboarding bind material
describe **integrated delivery** as a first-class product posture — not a temporary “technology
preview” or “coming soon” warning — while email/SMTP remains deferred on `I-2026-0023`.

Daily operator UX keeps the posture **discoverable but collapsed** (short summary label; full copy
on expand), so dialogs stay light without losing educational content.

## Boundaries in scope

- Admin UI EN/FR copy for `previewNotice` / `credentialsPreviewNotice` (enrollments, admins
  onboarding, login activation, login recovery)
- Shared `IntegratedDeliveryNotice` disclosure (collapsed by default); common summary labels
- Contextual help topics: enrollments, enrollment-detail, admins, login
- Product-docs: this TB, `I-2026-0023` slice note, `V-2026-0005` signal, backlog index,
  light `operator-alignment-guide` note

## Out of scope

- SMTP configuration, mailer, “Send by email” actions
- SMS SPI (`V-2026-0007` / `I-2026-0024`)
- Platform profile-aware code (`V-2026-0010`)
- Java / OpenAPI / `update-specs`
- Playwright gate (copy/posture only)

## First executable slice

1. Rewrite posture notices to D7 voice (`integrated-delivery`; manual external channels OK;
   optional SMTP later as product option, not “coming soon”).
2. Present notices via collapsed `<details>` disclosure (`IntegratedDeliveryNotice`); keep QR
   load errors as `warning`.
3. Extend help bodies with a short integrated-delivery paragraph.
4. Amend canon (`I-2026-0023`, `V-2026-0005`, index, operator-alignment).

## Rollback or fallback posture

- Revert the change set restores prior “technology preview” framing (or expanded inline copy).
- No runtime feature flag; copy/UI-only rollback is sufficient.

## Critical flows

| Flow | Nominal | Exception |
|------|---------|-----------|
| Create enrollment success | Collapsed “Integrated delivery” summary; expand for full copy | N/A (copy) |
| Enrollment detail credentials | Same disclosure pattern | N/A |
| Admin onboarding dialog | Same disclosure pattern | N/A |
| Login activation success | Collapsed “Integrated delivery (bootstrap)” summary | QR image error stays warning |
| Login recovery after reset | Collapsed integrated-delivery disclosure | QR image error stays warning |
| Help `?` on enrollments / admins / login | Mentions integrated delivery + manual channels | N/A |

## Evidence plan

### Automated

- None required beyond existing Admin UI lint/typecheck if the change set is opened in a PR.

### Manual exploratory (required — short)

1. Create enrollment → success dialog shows collapsed summary; expand reads full posture.
2. Enrollment detail credentials disclosure (if token visible).
3. Admin onboarding dialog disclosure.
4. Login activation success: collapsed bootstrap summary (not a warning banner).
5. Open `?` help on enrollments and admins.

### Playwright

- Skipped for this slice (copy/posture only; no workflow or guard change).

## Quality gates

- **Analysis:** grill D7 + `V-2026-0005` already settled; this TB executes the posture half only.
- **Design:** collapsed disclosure by default (operator-first daily UX); full copy on expand;
  help drawer retains pedagogical depth.
- **Implementation:** EN/FR parity; body keys stay `previewNotice` / `credentialsPreviewNotice`;
  summaries in `common:integratedDelivery.*`.

## Exit criteria

1. No Admin UI posture notice still says “Technology preview” / “Préversion technique” /
   “until dedicated integrations” / “not part of this release” for these surfaces. ✅
2. Posture copy is collapsed by default behind an Integrated delivery summary on all five
   surfaces. ✅
3. Help topics document integrated delivery. ✅
4. `I-2026-0023` records posture slice vs remaining SMTP R1; this TB closed when merge-ready. ✅

## Closeout (2026-07-25)

- EN/FR notices rewritten to integrated-delivery voice.
- **UX amendment (same day):** notices moved into shared
  `IntegratedDeliveryNotice` (`<details>` collapsed by default) after operator feedback that
  always-visible long copy was heavy for daily use. Activation no longer uses an `info` Alert
  for posture; recovery/enrollments/admins use the same disclosure.
- Help: login, enrollments, enrollment-detail, admins (EN/FR).
- Canon: `I-2026-0023` slice table, `V-2026-0005`, backlog index, operator-alignment note.
- Playwright skipped (copy/posture only). Manual UI glance recommended on clean-start when convenient.
- Remaining on `I-2026-0023`: SMTP-assisted delivery R1.

## Links

- Vision: [`../vision/V-2026-0005-email-integration-strategy.md`](../vision/V-2026-0005-email-integration-strategy.md)
- Idea: [`ideas/I-2026-0023-email-channel-r1-optional-operator-send.md`](ideas/I-2026-0023-email-channel-r1-optional-operator-send.md)
- Grill: [`grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md`](grill-sessions/blitz-2026-05-08-1-D7-email-grill-me.md)
