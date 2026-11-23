# UI Walk Gate — `WALK-2026-11-23-enrollment-user-id-940`

Canon: [`../../../templates/ui-walk-gate.template.md`](../../../templates/ui-walk-gate.template.md), [`../../ui-walk-done-gate.md`](../../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-11-23-enrollment-user-id-940`
- **Owner (intention):** Julie / Marc
- **Walk executor:** agent walk (Claude Code, scripted Playwright against a mocked Admin API, EN and FR captures); first pass by the Cursor agent at `57fd3b19` (12/12, GO), re-walk after the Walk Gate fixes and the copy reframing; Marc confirms the verdict
- **Single walkable SHA / PR:** [#1455](https://github.com/mgagp/ezkey/pull/1455) at `db5622cc`
- **Created:** `2026-11-23`
- **Surface(s):** Admin UI: enrollment list (`/enrollments`), create dialog, enrollment record (`/enrollments/{id}`)
- **Issue:** [#940](https://github.com/mgagp/ezkey/issues/940) § *Extension: Name / User ID confusion*

## Done (one line)

> One SHA where an operator sees Name and User ID side by side with one label, understands from the help what each is for, and is warned about an empty User ID only where it can matter (application integration), in EN and FR.

## Stack

- Vite dev server (`npx vite`), Admin API mocked with Playwright routes: no backend change in this PR.
- Data: an application integration and the system integration (administrator MFA); three enrollments (application with and without User ID, system without User ID).
- Session: Global Admin, injected into `sessionStorage`.
- Re-run: `ezkey-admin-ui/e2e/walks/enrollment-user-id-940.walk.spec.ts`, `npm run test:walk -- --grep 940` from `ezkey-admin-ui/`.

## Checklist

| # | Assert | Must NOT | Result EN | Result FR |
|---|--------|----------|-----------|-----------|
| 1 | List: User ID column right after Name | User ID column after Integration | PASS | PASS |
| 2 | List: empty User ID reads Not set / Non défini; a set one shows its value | Em dash for empty | PASS | PASS |
| 3 | Create: User ID directly under Name | User ID after contact fields | PASS | PASS |
| 4 | Create: Name help "Device label shown on the phone."; placeholder is a device label | Full person name as placeholder | PASS | PASS |
| 5 | Create: label "User ID (optional)" and the username help | Old "Internal reference (HR ID…)" help | PASS | PASS |
| 6 | Create: no warning before an integration is chosen | Warning on an empty form | PASS | PASS |
| 7 | Create: warning once an application integration is chosen | Copy naming Demo Login or PAM | PASS | PASS |
| 8 | Create: warning disappears once a User ID is typed | Blocking error | PASS | PASS |
| 9 | Create: system integration shows no warning and a neutral help | Application-oriented help for administrator MFA | PASS | PASS |
| 10 | Create: Name capped at 64, User ID at 255 (the columns) | 100 / 255 caps | PASS | PASS |
| 11 | Record: Not set plus warning on an application enrollment; Not set without warning on a system enrollment | Warning on administrator MFA | PASS | PASS |
| 12 | No legacy label (User Identifier, User identifier, ID utilisateur) or product names in the warning | Mixed labels | PASS | PASS |

## Locales

- [x] EN
- [x] FR

## Findings

| ID | Finding | Decision |
|----|---------|----------|
| F1 | First pass: warning shown before an integration was chosen | Fixed in #1455 |
| F2 | First pass: application-oriented User ID help on a system enrollment | Fixed in #1455 (neutral help) |
| F3 | First pass: Name placeholder was a person's name, not a device label | Fixed in #1455 |

Seen, out of scope (no change):
- List width at 1024 px (predates #1455).
- Backend length validation does not match the columns: [#1457](https://github.com/mgagp/ezkey/issues/1457).

## Out of scope (one line)

> No API, mobile or Demo Login change; User ID stays optional and separate from Name.

## Verdict

- [x] PASS proposed on `db5622cc` (agent walk, 24/24 asserts across EN and FR); Marc confirms.
