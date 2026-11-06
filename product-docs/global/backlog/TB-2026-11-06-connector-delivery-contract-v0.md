# Tracer Bullet Brief — `TB-2026-11-06` Connector delivery contract v0

## Metadata

- **ID:** `TB-2026-11-06-connector-delivery-contract-v0`
- **Status:** `draft`
- **Related vision:** [`V-2026-11-04-connector-spi`](../vision/V-2026-11-04-connector-spi.md)
- **Decision pack:** [`2026-11-04-connector-spi`](../decisions/2026-11-04-connector-spi/README.md)
- **GitHub issue:** `#1206` (step 2)
- **Created at:** `2026-11-06`
- **Updated at:** `2026-11-06`
- **Captured by:** Marc

## Objective

Define the delivery contract v0 in OpenAPI, and show on one page that the directory case holds on paper,
before any core or connector code is written.

## Decisions for the contract

Taken one at a time with Marc.

| # | Question | Decision | Date |
|---|----------|----------|------|
| 1 | Authentication between Ezkey and a delivery connector | Bearer token per connector, with the rules below | 2026-11-06 |
| 2 | QR format in the job | PNG in base64, rendered by the core, with the rules below | 2026-11-06 |
| 3 | Recipient | The enrollment's `contactEmail` only, present at send time, with the rules below | 2026-11-06 |
| 4 | What the audit records about the recipient | Open | |
| 5 | Email wording, locale and template owner | Open | |
| 6 | What the health check returns | Open | |

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

## Configuration guidance

Best practices for configuring a connector are collected in
[#1246](https://github.com/mgagp/ezkey/issues/1246) as the program learns them.
