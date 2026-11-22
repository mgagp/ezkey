# Demo Login — end-to-end test matrix

What Demo Login does in each case, proven on a clean-start stack: Playwright drives Demo Login in one
browser and the Demo Device (the phone) in another. Screenshots below are produced by the run
(`screenshots/`). Last run: 2026-11-21, 21/21 passed (`main` + #1430).

## How to run it

From the repository root, with Docker:

```bash
cd ezkey-tests && ./clean-start.sh && cd ..
mvn -q test -pl ezkey-tests -Dtest=AdminTokenCreationTest   # caches a Global Admin token
cd ezkey-demo-login && npm ci && npm run e2e   # takes EZKEY_ADMIN_TOKEN from that cache
```

The global setup builds the image, creates two integrations, an API key and the enrollments
(`e2e/provision.mjs`; the result stays in memory, handed to the tests through the environment), adds the application users with the CLI, and activates the Demo Device
enrollments through its screens. Tests run against a dedicated container (`ezkey-demo-login-e2e`,
port 18082) with a 30-second request lifetime, so the "expired" case takes seconds instead of two
minutes. The container is restarted with other settings for the configuration cases.

The three ezkey.org guided-tour images come from `e2e/tour-screens.spec.ts`, run on demand:
`E2E_TOUR=1 npm run e2e -- e2e/tour-screens.spec.ts`, then `cwebp -q 82` each PNG into
`sites/ezkey-org/community-guided-tour/community-tour-0{5,6,7}-demo-login-*.webp`.

## Users

| Application user | Ezkey side | Used by |
|---|---|---|
| `alice` | active enrollment on the Demo Device | 01–06, 14, session cases |
| `frank` | (disabled in the application) | 08 |
| `erin` | no enrollment at all | 09 |
| `bob` | enrollment created, never activated on a device | 10 |
| `carol` | enrollment activated, then deactivated by an administrator | 11 |
| `dave` | enrollment activated, then revoked by an administrator | 12 |
| `oscar` | active enrollment, but in another integration | 13 |
| `mallory` | not an application user | 07, 18 |

## Matrix

| # | Case | What the user sees | Reference shown | Screenshots |
|---|------|--------------------|-----------------|-------------|
| 01 | Approved, with a challenge code | Code to pick, countdown, then the signed-in page. Reload keeps the session; sign out ends it. | — | [form](screenshots/01a-login-form.png) · [waiting](screenshots/01b-waiting-with-code.png) · [device](screenshots/01c-device-approve.png) · [device result](screenshots/01d-device-result.png) · [signed in](screenshots/01e-signed-in.png) |
| 02 | Approved, no challenge code | Waiting without a code, then signed in | — | [waiting](screenshots/02a-waiting-no-code.png) · [device](screenshots/02b-device-approve.png) |
| 03 | Denied on the device | "Sign-in refused" | — | [device](screenshots/03a-device-deny.png) · [app](screenshots/03b-refused.png) |
| 04 | Wrong code picked on the device | "Sign-in refused — the device answer did not match" (Ezkey status `INVALID`) | — | [device](screenshots/04a-device-wrong-code.png) · [device result](screenshots/04b-device-result.png) · [app](screenshots/04c-app-result.png) |
| 05 | Nobody answers | Countdown, then "Request expired" | — | [countdown](screenshots/05a-waiting-countdown.png) · [expired](screenshots/05b-expired.png) |
| 06 | The user cancels | "Request cancelled"; nothing is pending on the device any more | — | [app](screenshots/06a-cancelled.png) · [device](screenshots/06b-device-nothing-pending.png) |
| 07 | Username unknown to the application | "This account cannot sign in to this application." Ezkey is not called. | `account-unavailable` | [app](screenshots/07-unknown-user.png) |
| 08 | User disabled in the application | Same message as 07 (no hint that the account exists) | `account-unavailable` | [app](screenshots/08-disabled-user.png) |
| 09 | No Ezkey enrollment for the user | "No active Ezkey device is linked to this account." | `enrollment-unavailable` | [app](screenshots/09-no-enrollment.png) |
| 10 | Enrollment never activated | same as 09 | `enrollment-unavailable` | [app](screenshots/10-enrollment-not-activated.png) |
| 11 | Enrollment deactivated | same as 09 | `enrollment-unavailable` | [app](screenshots/11-enrollment-deactivated.png) |
| 12 | Enrollment revoked | same as 09 | `enrollment-unavailable` | [app](screenshots/12-enrollment-revoked.png) |
| 13 | Enrollment in another integration | same as 09: Ezkey resolves the user only within the API key's integration | `enrollment-unavailable` | [app](screenshots/13-enrollment-other-integration.png) |
| 14 | Second request while one waits | The new request waits; the first one ends as expired (integration policy `REPLACE`, the default) | — | [second](screenshots/14a-second-request-waits.png) · [first](screenshots/14b-first-request-superseded.png) |
| 15 | Wrong API key secret | "This application cannot reach Ezkey with its current settings." Log: Integration API 401. | `configuration-error` | [app](screenshots/15-wrong-api-key.png) |
| 16 | Integration API unreachable | "Ezkey cannot be reached right now." | `service-unavailable` | [app](screenshots/16-ezkey-unreachable.png) |
| 17 | No API key configured | Banner on the form, then "not connected to Ezkey yet" | `not-configured` | [app](screenshots/17-not-configured.png) |
| 18 | Too many attempts from one address | "Too many attempts. Wait a minute." | `rate-limited` | [app](screenshots/18-rate-limited.png) |
| 19 | Forged session cookie | Sign-in form | — | — |
| 20 | Expired session | Back to the sign-in form | — | [app](screenshots/20-session-expired.png) |
| 21 | French | Same flow in French | — | [form](screenshots/21a-fr-login-form.png) · [signed in](screenshots/21b-fr-signed-in.png) |

Device enrollment, done by the setup: [Demo Device verify step](screenshots/00-device-enroll-alice.png).

## Design choices the matrix shows

- **The application owns its users.** A name it does not know (07) or a disabled user (08) never
  reaches Ezkey, and gets the same message, so the screen does not reveal which accounts exist.
- **Enrollment problems get one message** (09–13). The precise cause is in the application log
  (`ezkey_call_refused ... status=... problemType=...`), where an operator looks; the user is told
  to contact an administrator. The short reference code helps that conversation.
- **Configuration problems are not blamed on the user** (15–17): the message points to the
  application, and the log names the cause.

## Known platform behaviour

Case 06 cancels immediately after the start, while the back end's first wait call is in flight on
the same API key. Before #1433 this collided on the key's optimistic lock and the cancel got a 401;
the case keeps no pause so it guards that fix.

Also seen: the Demo Device requires a challenge code even to deny a request (case 03 fills one).
