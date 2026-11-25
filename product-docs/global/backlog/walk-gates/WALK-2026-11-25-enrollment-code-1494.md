# UI Walk Gate — `WALK-2026-11-25-enrollment-code-1494`

Canon: [`../../../templates/ui-walk-gate.template.md`](../../../templates/ui-walk-gate.template.md), [`../../ui-walk-done-gate.md`](../../ui-walk-done-gate.md).

## Metadata

- **ID:** `WALK-2026-11-25-enrollment-code-1494`
- **Owner (intention):** Marc
- **Walk executor:** agent walk (Claude Code, scripted Playwright against a mocked Admin API, EN and FR captures); Marc confirms the verdict
- **Single walkable SHA / PR:** the #1494 PR (SHA recorded in the PR)
- **Created:** `2026-11-25`
- **Surface(s):** Admin UI: enrollment record (`/enrollments/{id}`), administrator bind card (`/admins/{id}`), activation and recovery on `/login`; Demo Device new-enrollment field
- **Issue:** [#1494](https://github.com/mgagp/ezkey/issues/1494) (lot 0 of `I-2026-11-03-enrollment-invitation-links`)

## Done (one line)

> Wherever the Admin UI shows an enrollment QR code, the operator can copy the same text the QR holds, composed by the server, only while the proof token is visible, and sees why when there is nothing honest to copy, in EN and FR.

## Stack

- Vite dev server, Admin API mocked with Playwright routes (the walk asserts the UI; the server composer is covered by Admin API tests).
- Data: three enrollments (CREATED with a code, CREATED without an Auth API address, VERIFIED), one Tenant Admin at `CREATED`, one activation response, one recovery and reset response.
- Session: Global Admin injected into `sessionStorage`; activation and recovery run signed out.
- Re-run: `ezkey-admin-ui/e2e/walks/enrollment-code-1494.walk.spec.ts`, `npm run test:walk -- --grep 1494` from `ezkey-admin-ui/`.

## Checklist

| # | Assert | Must NOT | Result EN | Result FR |
|---|--------|----------|-----------|-----------|
| 1 | Record: card titled Enrollment code / Code d'enrôlement, value masked | Bare proof token on screen | PASS | PASS |
| 2 | Record: hint says the 6-digit binding challenge goes through another channel | Challenge inside the text | PASS | PASS |
| 3 | Record: "Copy enrollment code" puts the exact server text on the clipboard | Client-composed text | PASS | PASS |
| 4 | Record: Show reveals the JSON, which holds no challenge | Challenge in the JSON | PASS | PASS |
| 5 | Record: no "Copy token" button or token label left | Two things to copy | PASS | PASS |
| 6 | Record without an Auth API address: no copy, and the reason names `ezkey.qr.auth-base-url` | A copy without `authUrl` | PASS | PASS |
| 7 | Record after bind (VERIFIED): no enrollment code card | Code after `CREATED` | PASS | PASS |
| 8 | Administrator: nothing before the audited reveal | Code loaded with the record | PASS | PASS |
| 9 | Administrator: after the reveal, the code, masked, with its copy button | Token label or "Copy token" | PASS | PASS |
| 10 | Activation: manual entry offers the enrollment code, masked, with its copy | "Copy token" | PASS | PASS |
| 11 | Recovery (after reset): manual entry offers the enrollment code, masked, with its copy | "Copy token" | PASS | PASS |

## Locales

- [x] EN
- [x] FR

## Findings

| ID | Finding | Decision |
|----|---------|----------|
| F1 | The Demo Device manual field took only the bare token, against the issue's premise that no client did | Marc, 2026-11-25: the field accepts the pasted code (same parser as its QR import) |
| F2 | Activation and recovery notices still named the proof token | Fixed in the PR (they name the enrollment code) |

## Out of scope (one line)

> Mobile paste field (#1185), a required `authUrl` (#1300), email content, links, any change to bind or verify.

## Verdict

- [x] PASS proposed (agent walk, 2 × 11 rows across EN and FR); Marc confirms.
