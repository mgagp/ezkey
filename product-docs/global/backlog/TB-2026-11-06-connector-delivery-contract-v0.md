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
| 3 | Recipient | Open | |
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

## Configuration guidance

Best practices for configuring a connector are collected in
[#1246](https://github.com/mgagp/ezkey/issues/1246) as the program learns them.
