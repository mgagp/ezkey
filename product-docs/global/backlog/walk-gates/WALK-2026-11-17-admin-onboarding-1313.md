# UI Walk Gate — `WALK-2026-11-17-admin-onboarding-1313`

Canon: [`../../../templates/ui-walk-gate.template.md`](../../../templates/ui-walk-gate.template.md), [`../../ui-walk-done-gate.md`](../../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-11-17-admin-onboarding-1313`
- **Owner (intention):** Julie / Marc
- **Walk executor:** agent walk (scripted captures, EN and FR), reviewed against the #1313 decisions; Marc confirms the verdict
- **Single walkable SHA / PR:** [#1398](https://github.com/mgagp/ezkey/pull/1398) at its head commit (backend from #1395, merged)
- **Created:** `2026-11-17`
- **Surface(s):** Admin UI: administrator record (`/admins?adminId=N`), create dialog, administrator's enrollment record (`/enrollments/{id}`)
- **Issue:** [#1313](https://github.com/mgagp/ezkey/issues/1313) § *Walk Gate (Julie)*

## Done (one line)

> One SHA where creating an administrator lands on the record, the record is the only screen that reveals the bind material (on click, audited), and the Global, Tenant Admin, EN and FR views match the #1313 display rules.

## Stack

- Runtime profile: default Docker clean-start stack on Rootbeer, Admin UI container on `:3090`.
- Admin roles: Global Admin (`admin.docker`) and a Tenant Admin created and enrolled during the walk.
- Login path: passwordless, approved on the Demo Device.
- States walked: `CREATED`, `BOUND`, `VERIFIED` (IMMEDIATE mode) and pending activation (ACTIVATION_CODE mode). A pending administrator has no enrollment yet (`enrollmentId` null), so there is no enrollment record to walk in that state.

## Checklist

| # | Assert | Must NOT | Proof | Result |
|---|--------|----------|-------|--------|
| 1 | IMMEDIATE creation lands on the record with a toast and the bind card | No intermediate credentials screen | captures 01, 16; Playwright P1 | PASS |
| 2 | Opening the record loads nothing secret; "Show QR" fetches the material; the card says each reveal is audited | No `/onboarding*` request before the click | captures 02, 17; Playwright P7 | PASS |
| 3 | `CREATED`, revealed: QR, binding code and proof token, the QR on its own line | The reveal button must not overlap the QR | captures 03, 18 | PASS after fix F1 |
| 4 | `BOUND`, revealed: binding code with the "already scanned" warning | No QR, no proof token | capture 05; Playwright | PASS |
| 5 | `VERIFIED`: no bind card | No QR, code or token anywhere on the record | captures 07, 13; QR endpoint 409 | PASS |
| 6 | ACTIVATION_CODE: one-time code behind the saved-confirmation gate, then the pending record with the code's expiry | No enrollment link, no bind card, no second "created" message | captures 09, 10, 11, 14; Playwright P2, P6 | PASS after fix F2 |
| 7 | Global Admin: enrollment link on the record; the enrollment record links back to the administrator by name | Enrollment record shows no QR, challenge, token, contact email, edit or send action | captures 04, 06, 08; Playwright P3 | PASS |
| 8 | Tenant Admin: same record and bind card, no enrollment row; `GET /enrollments/{id}` answers 403 | No enrollment id or link on screen | captures 13, 14, 17, 18; Playwright P4 | PASS |
| 9 | Email hint under the administrator's email | No copy suggesting the email serves recovery or notification | captures 02, 11 | PASS |

## Locales

- [x] EN
- [x] FR (every record state; the IMMEDIATE create dialog and its toast walked in EN, the activation-code step and its landing in FR)

## Findings

| ID | Finding | Decision |
|----|---------|----------|
| F1 | With the QR shown, the "Hide QR" button sat on the same line as the QR frame and overlapped it (both inline) | Fixed in #1398: the QR frame is a block (`w-fit`) |
| F2 | ACTIVATION_CODE: the creation was announced twice, by a toast and by the code step's success alert (already the case on `main`) | Fixed in #1398: the toast only confirms IMMEDIATE creation, where the dialog closes |
| F3 | `ENDPOINT.md` (#1395) and design-principles §10 said `/enrollments/{id}` answers 404 to a Tenant Admin; it answers 403 (integration tenant check) | Docs corrected in #1398; Playwright P4 asserts 403 |

Seen, out of scope (no change):
- The administrator's enrollment record keeps its danger zone (revoke, delete), as before #1313.
- The administrator record is still a dialog over the list: [#1399](https://github.com/mgagp/ezkey/issues/1399).

## Out of scope (one line)

> No lifecycle, API or audit change; no new approval timing (the login and bind flows are unchanged).

## Verdict

- [x] PASS proposed on the walkable head of #1398 (agent walk); Marc confirms.
