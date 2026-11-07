# Tracer Bullet Brief — `TB-2026-11-06` Connector delivery contract v0

## Metadata

- **ID:** `TB-2026-11-06-connector-delivery-contract-v0`
- **Status:** `under-review`
- **Related vision:** [`V-2026-11-04-connector-spi`](../vision/V-2026-11-04-connector-spi.md)
- **Decision pack:** [`2026-11-04-connector-spi`](../decisions/2026-11-04-connector-spi/README.md)
- **GitHub issue:** `#1206` (step 2)
- **Created at:** `2026-11-06`
- **Updated at:** `2026-11-06`
- **Captured by:** Marc

## Objective

Define the delivery contract v0 in OpenAPI, and show on one page that the directory case holds on paper,
before any core or connector code is written.

- **Contract:** [`ezkey-connectors/contracts/delivery-v0.openapi.yaml`](../../../ezkey-connectors/contracts/delivery-v0.openapi.yaml)
- **Directory sketch:** [`directory-sketch.md`](../decisions/2026-11-04-connector-spi/directory-sketch.md)

## Decisions for the contract

Taken one at a time with Marc.

| # | Question | Decision | Date |
|---|----------|----------|------|
| 1 | Authentication between Ezkey and a delivery connector | Bearer token per connector, with the rules below | 2026-11-06 |
| 2 | QR format in the job | PNG in base64, rendered by the core, with the rules below | 2026-11-06 |
| 3 | Recipient | The enrollment's `contactEmail` only, present at send time, with the rules below | 2026-11-06 |
| 4 | What the audit records about the recipient | The full address, with the rules below | 2026-11-06 |
| 5 | Email wording, locale and template owner | The connector owns fixed templates, with the rules below | 2026-11-06 |
| 6 | What the health check returns | State and capabilities, with no provider call | 2026-11-06 |

### 1. Authentication

The job carries the QR, so its transport must keep it unreadable whatever the authentication is. A signature
would add only replay protection, and a duplicate of the same QR is harmless. mTLS is too heavy for an SME.

- **One token per connector,** long and random, compared in constant time.
- **Transport:** TLS, or a private Docker network declared explicitly. Ezkey refuses to call an `http://` URL
  without that declaration.
- **Rotation without downtime:** the connector accepts two tokens during the change.
- **Every route is protected,** the health check included.
- **No redirect is followed,** or the QR could leave for another address.

**Limit accepted:** a stolen token can send the fixed-template email with a QR of the attacker's choice until
rotation, and the core's daily caps do not apply since the call bypasses Ezkey. So the connector also enforces
a simple daily cap (step 5), which bounds the damage without removing the risk.

### 2. QR format

The Admin API already renders the PNG, so a connector needs no QR library and does not know the payload format.
Passing the payload would make every connector render a QR, and would leave the token in clear text if a log
captured it.

- **Typed and bounded field:** the connector refuses anything that is not a PNG or exceeds a maximum size. This
  also limits what a stolen token can push through.
- **The email attaches the image** inside the message instead of a `data:` URI, which several mail clients block.
  This belongs to the connector and is checked at step 6.
- **Invitation links** (`I-2026-11-03`) would add a field later. The contract stays `v0` until step 6.

### 3. Recipient

A typed address would turn the send button into a relay to any address. And if an email leaves for the wrong
address, the QR has left the perimeter: the right reaction is to revoke and recreate, not to correct the address
and send again.

`contactEmail` stays optional on the enrollment. It must be present when the operator sends, not at creation.

- **No `contactEmail`:** no send, and the Admin UI says why. The QR stays available on screen.
- **Validated at send:** an invalid format is refused with a clear error. Creation does not change, so existing
  integrations keep working.
- **The address is visible before sending:** the button names the recipient, so the operator can spot a typo.
- **One recipient field in the contract,** filled by the core from the enrollment, never by the Admin UI request.

**Editing `contactEmail` at `CREATED` (step 5).** Today only a `VERIFIED` enrollment can be edited
(`EnrollmentUpdateService`, Admin UI), so an enrollment without an address could not get one. Allow editing this
one field at `CREATED`; the other fields keep today's rule. The change is audited and reconciled with
`docs/LIFECYCLE_GOVERNANCE.md`. Correcting an address before a send is normal. After a send to a wrong address,
the answer stays revoke and recreate, and the screen can say so.

### 4. Audit of the recipient

An investigation must answer "where did this QR go?". The address can change at `CREATED` and the enrollment can
be deleted, so a reference is not enough. A masked address can leave the question unanswered, and a plain hash of
an email is easy to reverse by guessing. Editing an enrollment already writes `contactEmail` in clear to the audit
(`EnrollmentUpdateService`), so this adds no new kind of data.

- **The entry holds:** send id, enrollment, operator, connector, template and locale, recipient, outcome, typed
  error code and provider reference.
- **It never holds:** the QR, the token or raw provider text.
- **The address stays out of application logs,** on both sides. The canary test checks this with a recognizable
  recipient.

**Limit accepted:** the address stays in the tamper-evident chain, and erasure is not settled
([`I-2026-11-05`](ideas/I-2026-11-05-privacy-obligations-reminder.md)).

### 5. Template and language

The job names a template and a locale and carries a few data fields. If the core rendered the email, the contract
would carry free text and the connector would become a relay. An editor in the Admin UI would let anyone weaken
security wording. The cost: changing the text means redeploying the connector.

- **Language:** the operator's Admin UI language at send time, `en` or `fr`, shown next to the button.
- **Job data:** the organisation name, the expected server and the QR, nothing else. The name is bounded in length
  and escaped by the connector, since it is the only free text left.
- **Expected server required:** if `ezkey.qr.auth-base-url` is empty, the send is refused with a clear error. The
  email must name the server so the person has something to compare.
- **Required email content:** who invites, have your code in hand before scanning, which server the app should
  show, and what to do if you did not expect this email. Exact wording comes at step 5 and is tried at step 6.

**Limit accepted:** no language is stored for the person, so an operator can send the wrong language, and no other
language is available. Tracked separately in [#1250](https://github.com/mgagp/ezkey/issues/1250); a better source
of language should not change the contract's shape.

### 6. Health check

The send already gives an honest answer, so the health check does not try to predict it. Testing the provider on
each call would be slow, repeat SMTP connections and risk provider limits.

- **Returns:** state, connector name and version, contract version, channel, templates and locales, `testMode`.
- **Never returns:** a credential, a provider host, the sender address or provider error detail.
- **Core behavior:**
  - Connector absent or unreachable: integrated delivery, with the QR on screen.
  - Connector `degraded`: treated the same way, since it cannot send.
  - Different contract version: send refused.
  - Template or locale not offered: send refused with a clear error.
  - `testMode` true: the Admin UI says so next to the button.
- **Light call:** read when the screen opens, with a short timeout and a cache of a few seconds, never in a loop.

**Limit accepted:** the button can show and the click still fail if the provider is down. The error is clear and
the QR stays on screen.

## How the core reads a call

| What the core sees | Outcome | Why |
|--------------------|---------|-----|
| `200` | `accepted` | The provider took the message |
| `400`, `401`, `413`, `422` | `rejected` | The connector says it did not leave |
| Connection refused, before anything was sent | `rejected` | The job never reached the connector |
| `504` | `unknown` | The provider did not answer in time |
| Read timeout, connection lost, other `5xx`, redirect, unreadable body | `unknown` | The email may have left. Redirects are never followed |

**Refused in the core, with no call:** caller's role or enrollment not `CREATED`; no connector or a `degraded` one;
missing or invalid `contactEmail`; empty `ezkey.qr.auth-base-url`; daily cap reached; evaluator temporary session;
self-registered tenant on the community instance; contract version, template or locale mismatch.

**Starting timeouts, to adjust at step 3:** connect 3 s and read 20 s for a send, with the connector's own provider
timeout at 15 s so it can answer `504` first. Health: 2 s, cached 10 s. All sit well under Cloudflare Free's 100 s.

## Pre-existing conditions touched

Nothing runs yet, so these bind step 3:

- **First outbound HTTP client** in the Ezkey APIs. Only `ezkey-demo-device` has one today.
- **`open-in-view` stays on** in the Admin API, so the call runs outside any transaction, and a slow-mock test checks
  the database pool.
- **`EvaluatorTempAccessFilter` is a deny-list,** so the new send route is open to those sessions until listed.
- **Create does not validate `contactEmail`;** edit does (`EnrollmentUpdateService`).
- **The QR renderer** is `QrCodeGeneratorService`, 300 × 300 in `EnrollmentController`.

## Boundaries in scope

- Admin API to delivery connector, in the contract above.

## Out of scope

- Any code, and the Admin API send endpoint (step 3).
- The directory contract: this brief holds a sketch only.
- SMS, automatic sending, bulk sending and an asynchronous result.
- The exact email wording (step 5).

## First executable slice

Step 3 implements this contract with the delivery port and the mock connector.

## Rollback or fallback posture

Documentation only. The contract can still change without compatibility work until step 6.

## Critical flows

- **Nominal:** an operator sends, the provider accepts, the audit records it, the UI says what comes next.
- **Exception:** the connector rejects or does not answer, the audit records it, the QR stays on screen.

## Evidence plan

- **Checked on 2026-11-06 with `openapi-spec-validator` and `jsonschema`:** the contract is valid OpenAPI 3.1 and
  its example satisfies the schema. The schema refuses a challenge field, an activation code, a free subject or body,
  a non-PNG, an oversized QR, an unknown locale, a provider host in the health response, and error message text.
- Tests come at step 3, against this contract.

## Quality gates

Marc's gate for this step: the directory case holds on paper.

## Exit criteria

The contract and sketch are merged, and the vision note can be promoted.

## What step 3 added (2026-11-06)

Choices made while building the delivery port, within the decisions above:

- **Also refused in the core:** an admin MFA enrollment (system integration), an expired invitation, and a retired
  integration or inactive tenant, following the eligibility chain in `docs/LIFECYCLE_GOVERNANCE.md`.
- **Daily caps** default to 50 per tenant and 200 per instance per UTC day. They count `accepted` and `unknown` from
  the audit, since a `rejected` email did not leave, so no new table is needed. Concurrent sends can overshoot slightly.
- **Open-in-view:** a session under open-in-view holds its database connection until the response, whatever the
  transaction boundaries. So `spring.jpa.open-in-view=false` turns off Spring Boot's interceptor, and
  `AdminOpenInViewConfig` registers the same interceptor on every route except the send.
  `AdminOpenInViewConfigPoolTest` proves both sides with a pool of one connection.
- **Left for step 7:** the self-registered tenant refusal, which needs a new marker on the tenant. No community
  deployment has a connector before that step.

**Checked on the real stack (2026-11-06):** a clean start with and without the mock. Every outcome, the refusals and
the daily cap behaved as written. With 15 slow sends in flight, past the default pool size, another admin read took
0.05 s, and each send became `unknown` at 20 s. Logs of all containers and the mock held no recipient, token, PNG
signature or long base64 block. The refusal for evaluator sessions is covered by a unit test only.

## Configuration guidance

Best practices for configuring a connector are collected in
[#1246](https://github.com/mgagp/ezkey/issues/1246) as the program learns them.
