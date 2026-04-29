# Mobile Availability Out-Of-Band Design

## Status

Discussion design only. No code change is proposed in this document.

## Executive Summary

The availability badge idea remains viable only if it is treated as a non-authoritative UX signal,
kept outside the cryptographic enrollment and authentication protocol.

The key design choice is not the badge itself. The real design choice is whether Ezkey should
introduce a new credential only to read a last-known state.

Current conclusion:

- Do not carry availability inside the signed `bind` / `verify` / `pending` / `respond` flows.
- If the feature is ever implemented, prefer a separate read-only status surface.
- For a low-priority first version, avoid introducing a brand new token concept unless the team
  decides the separation value is worth the operational cost.
- The lightest viable option is to reuse the existing `enrollmentProofToken` on a separate,
  explicitly non-authoritative status endpoint.
- The cleanest long-term option is a dedicated read-only status token, but that adds cost and is
  hard to justify for a small UX feature.

This document recommends a phased posture:

1. If the feature is built soon and kept low priority, reuse the existing enrollment proof token on
   a separate endpoint.
2. Introduce a dedicated status token only if the feature proves useful enough to deserve stronger
   capability separation.

## Why The Protocol Rollback Is The Right Call

Ezkey's protocol value comes from cryptographic continuity and clarity. Availability does not decide
whether an enrollment or auth attempt is cryptographically valid. It is an operator-derived,
mobile-facing comfort signal.

Once availability is signed and embedded into canonical payloads, it stops behaving like a minor UX
feature and starts behaving like protocol material:

- it changes signed payload formats,
- it propagates into generated clients,
- it affects mobile verification logic,
- it creates contract pressure around `204 No Content`,
- it increases rollback and compatibility cost.

That is too much protocol weight for too little product value.

## Product Posture For An Out-Of-Band Feature

If Ezkey keeps an availability feature, it should adopt this posture:

- The status is informative, not authoritative.
- The status never gates authentication flows.
- The status never replaces `pending`.
- The status can be stale.
- The true result is still revealed by the real protocol flow.

In UX terms, the app should behave as if it is showing "last known server status", not "current
truth guaranteed by protocol".

## Risk And Consequence Analysis

### Case 1: False Unavailable

The app shows `UNAVAILABLE` or `REVOKED`, but the enrollment would actually work.

Most likely user outcomes:

- the user waits,
- the user retries manually,
- the user assumes the app is a bit stale or buggy,
- the user contacts an admin.

Main impact:

- friction,
- confusion,
- support cost.

Security impact:

- low, if the real auth flow remains unchanged.

### Case 2: False Ready

The app shows `READY`, but the enrollment is disabled or revoked.

Most likely user outcomes:

- the user initiates a login,
- no usable pending request appears, or the real flow fails,
- the user concludes that the status was stale.

Main impact:

- expectation mismatch,
- reduced confidence in the badge.

Security impact:

- low, if the real auth flow remains unchanged.

### Case 3: Staleness Without Attack

This is the most realistic case.

Examples:

- admin deactivated an enrollment recently,
- integration state changed,
- tenant state changed,
- app has not refreshed for a while,
- network temporarily failed during a refresh.

This is likely much more common than an active attacker manipulating a marginal MFA product's
availability badge.

### Case 4: Active Manipulation Of The Status Channel

If the app already uses HTTPS correctly, an attacker who can still manipulate this read-only status
channel usually has a strong foothold already:

- compromised device,
- hostile local trust store,
- instrumentation,
- advanced interception setup.

In those scenarios, the attacker can often already suppress, alter, or distort ordinary UI state.
The marginal security gain of signing a low-value availability badge is therefore limited compared
with the protocol cost.

## What Comparable Products Usually Do

Products in adjacent spaces usually separate critical approval flows from device or account health
signals.

Common patterns:

- authentication or approval decisions remain strongly validated,
- device/account state is refreshed by a separate API,
- the UX accepts that this state may be stale,
- discrepancies are resolved at action time,
- the UI often communicates recency rather than absolute truth.

Typical wording patterns are closer to:

- "Last known state: available"
- "Needs attention"
- "Temporarily unavailable"
- "Last checked 5 minutes ago"

That family of UX is a better fit for Ezkey than pushing this signal into signed MFA payloads.

## Goals For A Separate Availability API

- Provide a simple read-only status for a stored enrollment.
- Keep the critical MFA protocol unchanged.
- Keep the mobile app implementation simple.
- Limit server and infrastructure abuse with conservative rate limiting.
- Avoid detailed operator-facing reasons.
- Make stale data acceptable and explicit.

## Non-Goals

- Do not prove availability cryptographically.
- Do not replace `pending`.
- Do not expose tenant, integration, or policy internals.
- Do not guarantee real-time correctness.
- Do not add a background polling architecture.

## Proposed API Shape

### Endpoint

Recommended shape:

```http
POST /api/v1/mobile/enrollment-availability
Content-Type: application/json
```

### Request

Low-friction version using existing enrollment proof token:

```json
{
  "enrollmentId": 123,
  "enrollmentProofToken": "<opaque enrollment proof token>"
}
```

Alternative version using a dedicated status token:

```json
{
  "enrollmentId": 123,
  "statusToken": "<opaque read-only status token>"
}
```

### Response

```json
{
  "availability": "UNAVAILABLE",
  "checkedAt": "2026-04-27T18:32:14Z",
  "staleAfterSeconds": 300
}
```

### Semantics

- `availability`: opaque mobile-facing state only
- `checkedAt`: server timestamp for this answer
- `staleAfterSeconds`: soft freshness guidance for local display and refresh behavior

Allowed values:

- `READY`
- `UNAVAILABLE`
- `REVOKED`

No detailed reasons should be exposed.

### Error Model

The endpoint should stay deliberately boring.

- `200 OK`: status returned
- `400 Bad Request`: malformed request
- `401` or `403`: only if the project later decides this endpoint must participate in a stronger
  auth model
- `404` should generally be avoided to reduce enumeration hints
- `429 Too Many Requests`: rate limit exceeded

Client-facing failure posture:

- keep last known status,
- mark it as stale if needed,
- never block the user from trying the real auth flow.

## Mobile UX Posture

The UI should present the result as a helpful signal, not a promise.

Recommended framing:

- `Available`
- `Temporarily unavailable`
- `Revoked`

Recommended supporting text:

- `Last checked just now`
- `Last checked 5 min ago`
- `Status may be delayed`

The app should not auto-refresh aggressively. Good refresh moments are:

- app launch,
- foreground resume,
- explicit manual refresh,
- after successful enrollment verify,
- optionally after a completed auth response.

## Rate Limiting And Abuse Control

This endpoint should be cheaper than `pending`, but still treated seriously.

Suggested baseline:

- per enrollment: modest burst, low sustained rate,
- per client IP: fallback cap,
- optional device fingerprint if such a concept exists later,
- no background 2-second polling model.

Practical posture:

- 1 quick refresh on foreground resume is acceptable,
- 1 manual refresh by user is acceptable,
- repeated hammering should return `429`.

## The Token Question

This is the real design friction.

The feature is small. Creating a whole new token concept just for a small status badge may be too
expensive relative to the value.

There are three realistic approaches.

### Option 1: Reuse `enrollmentProofToken`

#### Idea

Do not create any new token. The status endpoint accepts the existing `enrollmentProofToken`, but
the endpoint remains entirely outside the signed MFA flows.

#### Why This Is Attractive

- zero new credential concept,
- zero new issuance ceremony,
- no extra token lifecycle to explain,
- probably the lowest implementation and maintenance cost,
- fits a low-priority feature.

#### Drawbacks

- the same secret participates in both core protocol and a non-critical UX endpoint,
- this widens the usage surface of the proof token,
- it is less clean from a capability-separation perspective,
- future teams may be tempted to couple the status endpoint too tightly to protocol behavior.

#### Security Consequence

If this token leaks, the additional capability is read-only access to an opaque 3-state status.
That is not nothing, but it is materially less severe than write or approval capabilities.

#### Operational Cost

- minimal,
- no schema change required if the proof token already exists,
- no new rotation model beyond what already exists.

#### Verdict

This is the best choice if the feature remains low priority and the product wants the lightest
possible implementation.

### Option 2: Dedicated Stored Status Token

#### Idea

Each enrollment receives a separate opaque read-only token dedicated to the availability endpoint.
The backend stores only a hash of it, similar in spirit to proof-token handling.

#### Why This Is Attractive

- clean capability separation,
- the status feature can evolve independently,
- compromise of this token does not expose the protocol proof token,
- better conceptual hygiene.

#### Drawbacks

- new token lifecycle to document,
- new issuance path,
- likely schema and DTO changes,
- extra rotation/revocation behavior,
- more developer and operator explanation for a small feature.

#### What It Could Be Based On

Preferred form:

- a random opaque 128-bit or 192-bit value,
- generated once when the enrollment becomes usable,
- stored hashed server-side,
- returned once to the mobile app,
- rotated only when an operator explicitly resets enrollment credentials or when the enrollment is
  re-established.

Why random instead of derived:

- simpler mental model,
- easier targeted revocation,
- no hidden dependency on proof-token material,
- easier future auditing.

#### Operational Cost

- moderate,
- requires issuance, storage, docs, and tests,
- probably too much if the feature remains optional UX sugar.

#### Verdict

This is the cleanest architecture, but it is hard to justify unless the feature becomes clearly
valuable.

### Option 3: Deterministically Derived Status Token

#### Idea

Do not store a token. Derive a read-only status token from server-held material, for example via an
HMAC over enrollment identity and a purpose string.

Illustrative shape only:

```text
statusToken = base64url(HMAC(serverSecret, "status" | enrollmentId | enrollmentProofTokenHash))
```

#### Why It Looks Appealing

- no separate token table or column,
- no explicit storage of the token value,
- can be regenerated deterministically.

#### Hidden Cost

- rotation becomes more subtle,
- revocation semantics are less obvious,
- the coupling to proof-token material still exists, just hidden,
- key-version management becomes necessary if the server secret rotates,
- harder to explain than either "reuse the proof token" or "store a dedicated token".

#### Verdict

Not recommended. It is the worst of both worlds for this use case: more abstraction, less clarity,
and not enough value.

## Recommendation On The Token

If this feature is ever prioritized, the recommended order is:

1. Start by reusing `enrollmentProofToken` on a separate read-only endpoint.
2. Ship the feature only if the product still thinks the UX gain is real after seeing it in
   practice.
3. Move to a dedicated stored status token only if the feature proves important enough to deserve
   cleaner capability separation.

That recommendation is intentionally pragmatic.

The feature does not currently justify a whole new token concept by default.

## Why Reusing `enrollmentProofToken` Can Be Acceptable Here

Normally, capability separation is cleaner. But Ezkey should also avoid accidental complexity.

For this specific read-only feature:

- the output is opaque and low sensitivity,
- the endpoint does not authorize an action,
- the endpoint does not change server state,
- the endpoint never approves or denies authentication,
- the real protocol remains the source of truth.

That makes reuse a defensible tradeoff if the team wants the smallest possible feature.

## Minimal Lifecycle If A Dedicated Token Is Ever Added

If the team later decides to add a dedicated status token, the lightest serious lifecycle would be:

1. Generate a random token when the enrollment is verified and usable.
2. Store only its hash.
3. Return it to the app once as part of an enrollment metadata refresh response.
4. Allow explicit rotation on enrollment reset or rebind.
5. Treat it as read-only and low-privilege in docs and implementation.

Anything more elaborate than that would likely be over-designed.

## Suggested Decision Rule

Build this feature only if at least one of these becomes true:

- support cost clearly shows users are confused by the lack of a status hint,
- demo and sales flows materially benefit from the badge,
- mobile UX becomes a stronger product priority,
- the team is comfortable with a read-only, stale-allowed signal.

Do not build it just because the badge looks elegant.

## Final Conclusion

An out-of-band availability API is viable.

The serious version of that idea is not "sign the status elsewhere". The serious version is:

- keep it out of the critical MFA protocol,
- accept staleness,
- keep it opaque,
- rate limit it,
- treat it as consultative UX only.

On the token question, the most pragmatic answer is also the least glamorous one:

- if the feature is low priority, do not invent a new token unless absolutely necessary,
- reuse `enrollmentProofToken` first,
- only introduce a dedicated status token if the feature later proves its worth.

That keeps the implementation aligned with Ezkey's stated values: simplicity, pragmatism, and
clarity of trust boundaries.
