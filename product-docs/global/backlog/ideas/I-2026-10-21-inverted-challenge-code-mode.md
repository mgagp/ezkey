# Backlog Idea — `I-2026-10-21-inverted-challenge-code-mode` Inverted challenge-code mode (mobile shows, integration enters)

## Metadata

- **ID:** `I-2026-10-21-inverted-challenge-code-mode`
- **Status:** `parked`
- **Priority:** `P3`
- **Created at:** `2026-10-21`
- **Updated at:** `2026-10-21`
- **Last reviewed at:** `2026-10-21`
- **Parked at:** `2026-10-21`
- **Component tags:** `auth-api`, `mobile`, `integration-api`, `docs`, `security`
- **Lane:** `A`
- **Captured by:** Marc
- **GitHub issue:** `#960`

## Intent

Optionally offer an **inverted** challenge-code UX where the **mobile app shows** a 4–6 digit code
and the user types it into the **integrating app**, with no explicit approve/deny on the device —
as an **additive** mode only, never replacing the current default.

## Problem and value

- **Problem:** Some MFA products invert the flow (app shows a code; the login screen collects it).
  Evaluators may ask whether Ezkey should match that shape.
- **Expected value (if ever built):** Meet a concrete adopter need without weakening the default
  direction that counters MFA fatigue and keeps a device-signed approve/deny decision.

## Settled context (do not re-litigate without new signal)

Recorded in [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md) Decision (#960,
Christophe) and issue [#960](https://github.com/mgagp/ezkey/issues/960):

- **Default stays:** integrating app shows a two-digit code; user enters it on mobile, then
  approves or denies.
- **Why:** same principle as CISA number matching (enter numbers from the login screen into the
  app). Honest entropy is 1-in-100; the code counters reflex approval, not brute force.
- **Ezkey-specific:** approve/deny is signed by the device key; inverted mode turns the proof into
  a human-copied code with no device signature bound to the decision, and loses explicit deny as a
  compromise signal.
- **Limit unchanged:** neither direction resists real-time proxy phishing; not
  WebAuthn/FIDO2/passkey-equivalent.

## Scope

- **In scope (on resume):** design and implement inverted mode as an **opt-in additive** path;
  document honest weaker properties (push-delivered OTP, easily relayed by a phishing site).
- **Out of scope (default):** replacing or demoting the current integration-shows / mobile-enters
  default; claiming phishing resistance for either mode.

## Parked (2026-10-21)

**Decision:** Keep the current direction. Park inverted mode until a resume trigger fires. No
`TB-*` and no protocol change until then.

**Resume trigger:**

- A **real adopter need** for the inverted UX.
- **Not before** the platform is stabilized and proven.

**Next review:** on resume trigger only (no scheduled revisit).

## Links

- Decision record: [`docs/SECURITY_POSTURE.md`](../../../../docs/SECURITY_POSTURE.md) § What Ezkey
  does — Decision (#960)
- GitHub: [#960](https://github.com/mgagp/ezkey/issues/960)
- Related (challenge lifecycle, not UX direction):
  [`I-2026-07-07-auth-attempt-challenge-protection`](I-2026-07-07-auth-attempt-challenge-protection.md)
