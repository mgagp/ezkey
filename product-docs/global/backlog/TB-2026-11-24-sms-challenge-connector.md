# Tracer Bullet Brief — `TB-2026-11-24-sms-challenge-connector` Send the enrollment challenge by SMS

## Metadata

- **ID:** `TB-2026-11-24-sms-challenge-connector`
- **Status:** `archived` (parked by Marc, 2026-11-25; see *Decision* below). Re-check every external fact on reopening
- **Related idea:** [`I-2026-0024`](ideas/I-2026-0024-sms-channel-r1-http-adapter-spi.md)
- **Related vision:** [`V-2026-0007`](../vision/V-2026-0007-sms-integration-spi.md), [`V-2026-11-04-connector-spi`](../vision/V-2026-11-04-connector-spi.md), ADR-0018
- **GitHub issue:** `#1206` (program; a dedicated issue is opened on GO)
- **Created at:** `2026-11-24`
- **Updated at:** `2026-11-25`
- **Captured by:** Marc

## Decision (Marc, 2026-11-25): parked

Not built now. The design below is kept so a later session does not start over.

- **Value.** A QR cannot be dictated; six digits can. Email removed the real friction. SMS spares the operator one
  short conversation per person and still needs their click. A call also lets them recognize the person.
- **Lived signal (Duo, Marc's workplace).** What hurt was making sure people who leave lose access. On the way in,
  the problem was whether the person understands the procedure and uses the code correctly; SMS does not address it.
- **Nearer gap.** A QR received on the phone that must enroll cannot be scanned. Copy and paste of the enrollment
  code (#1185; Admin UI part shipped, #1494) covers it.
- **Posture.** No adopter and no promotion. The aim is a small backlog and a credible, current community instance,
  with patches and pentests kept up.

**Reopen on:** an adopter asks; remote enrollment of people the operator cannot reach becomes common; or automatic
sending is decided. **Not chosen:** a second email with the code (same mailbox holds both factors). **Noted only:**
the integrating application shows the code to its signed-in user, which needs a machine surface that does not exist.

**Next in this program:** the server host at the challenge step and the pasted code (#1185), then directory work,
read-only first, under its own decision.

## Objective

An operator sends the 6-digit enrollment challenge to the enrollment's `contactPhoneNumber` by SMS, through a
separate connector. Reference provider: Twilio. Ezkey stays complete without it.

With the SMTP connector, this is the first time Ezkey can deliver **both** items without the operator passing
anything on. The QR and the code leave in two messages, through two connectors, on two operator clicks.

## What the corpus already settles

| Lock | Source | This brief |
|------|--------|------------|
| Separate process, versioned HTTP, provider secrets in the connector | ADR-0018 (1, 4) | Kept |
| One message never carries both factors | ADR-0018 (5) | Strengthened, see D1 |
| Never an admin activation code or admin enrollment | ADR-0018 (5), #1313 | Kept; refused in the core |
| Operator-triggered, synchronous, no stored job | Decision pack, invariant 3 | Kept |
| Challenge code only: no link, no QR in the SMS | `I-2026-0024`, D5-9 | Kept |
| Global or Tenant Admin sends, where they can operate the enrollment | D5-8 | Kept |
| Connectors are never synchronized with each other | ADR-0018 (8) | Kept; no ordering between email and SMS |
| One active adapter URL | D5-7 (2026-05-19) | **Outdated:** email now holds it. Becomes one per channel, D2 |
| SMS decided after step 7 | #1206 | Open: email is live (2026-11-15) but the step 7 box is unticked |

## Design decisions to confirm

### D1. A separate SMS contract, not a `v2` of the email contract

`delivery-v1.openapi.yaml` cannot carry this: `qrPngBase64` is required, unknown fields are refused, and the
health `channel` allows only `email`. Its text says it has no field for the challenge, and v1 is frozen.

- **Recommended:** `challenge-delivery-v1.openapi.yaml`, with its own path `/v1/challenge-deliveries`. It has no
  QR field; the email contract has no challenge field. **Neither connector can receive both factors**, so the rule
  is structural. The live SMTP connector does not change or redeploy. It shares health, auth, outcomes and errors.
- **Set aside:** a `v2` with a template union. One schema could express both factors, and the SMTP connector must move.

Job: `sendId`, `trigger`, `tenantRef`, `template` (`enrollment-challenge`), `locale`, `recipient` (E.164),
`organisationName`, `challenge` (`^[0-9]{6}$`), `expiresAt`, `timeZone`. No `expectedServer`: phones turn a hostname
into a link, and the lock is "no link".

### D2. One connector per channel in the core

`DeliveryConnectorProperties`, `DeliveryCapabilityService` and `HttpDeliveryConnector` are singletons tied to email
(`"email".equals(channel)`, one URL, QR-shaped job). Extract the shared HTTP call, health read and outcome mapping;
keep two small services. Email settings keep their names, so community configuration is untouched. SMS uses
`ezkey.admin.connector.sms.*`, with its own URL, token and caps. Each connector gets its own token.

### D3. Recipient: `contactPhoneNumber` only

It already exists (E.164, CHECK constraint V5), so **no migration**. It is not editable at `CREATED` today, so
`EnrollmentUpdateService` must allow it, as step 5 did for the email. An admin enrollment keeps no phone send.
The button names the recipient.

### D4. When it can be sent: `CREATED` and `BOUND`

The email QR stops at `CREATED`; the challenge is readable until verify. A person who scanned first and lacks the
code is waiting on the challenge screen. **Recommended: allow both,** with a warning at `BOUND`: a device has
already scanned, confirm it is this person's. Alternative: `CREATED` only, which is simpler and leaves that person
to a phone call.

### D5. Audit of the number

Today phone edits are **masked** in the audit (`maskedPhoneChangeEntry`); the email send records the **full**
address, so that an investigation can answer "where did this go?". **Recommended: full number in the send entry,**
same reasoning, never in application logs. This is a conscious exception to the masking convention.
The challenge is never in the audit.

### D6. Outcome wording

Twilio answers `queued` or `accepted` at once. Filtering, opt-out and an unreachable handset come later, through a
status callback that would need a public inbound route. **Recommended for R1:** no callback. `accepted` means
"Twilio took it"; the UI says so, and the code stays on screen. The Twilio message SID is the provider reference,
so an operator with console access can look it up. **The SMS gate below therefore checks real reception.**

### D7. HTTP without the Twilio SDK

One form POST. Use the JDK client: no vendor dependency, simpler licence tracking. Twilio credentials: a restricted
API key instead of the account token. That a key can be limited to message creation alone is **not confirmed**.

## Blind spots found

| # | Blind spot | Treatment |
|---|------------|-----------|
| 1 | **The Twilio trial does not send custom text**: only Twilio templates, to 5 verified numbers. It cannot carry our code | A **paid account is needed at the real trial**. Everything before it runs on a fake Twilio server in CI |
| 2 | **Canadian filtering.** Long codes are supported, but Twilio recommends verified toll-free numbers or short codes | Start with one long code for the lab. Toll-free if messages are filtered; its verification asks for business information |
| 3 | **A wrong code is final**: verify marks the enrollment `INVALID` on first mismatch (`EnrollmentVerifyService`). After delete and recreate, the old SMS is still on the phone | The text says it replaces any earlier code. Duplicate sends of the same enrollment carry the same code, so they are harmless |
| 4 | **Code lifetime.** The challenge is fixed at creation and lives up to 7 days. NIST SP 800-63A reportedly limits a confirmation code by SMS to 10 minutes (read from a summary, to verify) | Document, no claim. The code alone does nothing without the QR and a bind. Rotating it would change the protocol: out of scope |
| 5 | **SIM swap, number porting, lock-screen preview** | Document in `SECURITY_POSTURE.md`: SMS is the weaker separated channel, not a second proof of identity. The number is typed by an operator, never verified |
| 6 | **Separation is thinner than it looks**: email and SMS often reach the same phone | Say so. The gain is against a compromised mailbox, not a compromised phone |
| 7 | **Toll fraud and cost.** `+1` also covers Caribbean countries, so a `+1` prefix check is not enough | Twilio geo permissions (Canada only, console-only setting), core and connector caps, low prepaid balance with auto-recharge off |
| 8 | **Harassment of one number** by repeated clicks | Small per-enrollment daily cap (proposed: 3), on top of tenant and instance caps |
| 9 | **Encoding.** `ê`, `î`, `ô`, lowercase `ç` are outside GSM-7: one such letter drops a segment from 160 to 70 characters and can double the cost | Templates written in GSM-7; the connector folds the organisation name and caps its length |
| 10 | **Text injection through `organisationName`**: a tenant named `evil.com` becomes a tappable link | The connector neutralizes link-like text and control characters; unit-tested. Code placed first |
| 11 | **Canary by shape.** A 6-digit code is too short to search safely in logs, and SMS has no PNG signature | Search the exact challenge near the send id, the recipient, request bodies, and the Basic auth header; controlled value to limit false positives |
| 12 | **Twilio keeps message bodies**; its redaction needs a paid Editions plan. Default retention not confirmed | State it: the provider holds the code, the operator's processor question already in the corpus. Code is useless after verify |
| 13 | **VM memory.** Documented ceilings already reach ~3904m on a ~3839 MB host; a 256m connector makes ~4160m. SMTP measured ~111 MiB idle | **Blocks the community step, not the code.** Measure and lower both ceilings, or resize. Confirm the swap |
| 14 | **Compose overlay drift**: recreating the Admin API without an overlay silently drops a channel. A fourth file makes this worse | One combined connectors overlay, or a documented `COMPOSE_FILE` on the VM |
| 15 | **STOP / opt-out** comes back after acceptance (21610), and the operator never sees it | Documented. The code stays on screen |
| 16 | **Consent and privacy**: CASL, Law 25 (processor outside Quebec) | Operator's duty, stated in #1246; not a legal opinion. `I-2026-11-05` stays the owner |
| 17 | **Language** is the operator's, not the person's (#1250) | Same limit as email, both channels together later |
| 18 | **Two channels on the record page**: the screen must not read as one flow in order | Two lines, one per channel, each with its state. #1308 (field weight) can now be decided for both contact fields |
| 19 | **Open-in-view** is off only for the `qr-delivery` pattern; the new send route needs the same exclusion or a slow Twilio holds a database connection | Extend the pattern and the pool test |

## Alternatives considered

- **Do nothing.** The operator phones or texts the code. It costs nothing and stays valid. The connector is worth it for
  remote enrollment at some volume, and as the second real proof of the SPI.
- **Twilio Verify.** It is built around its own codes; ours is fixed by the protocol. Rejected.
- **AWS End User Messaging (SNS).** The AWS account exists, so it could save a vendor. Not priced or checked here. The
  contract names no vendor, so this stays open as a second connector.

## Facts used

| Fact | Status |
|------|--------|
| Outbound Canada $0.0083 per segment plus carrier fee $0.0064 to $0.0087 (USD) | [Twilio pricing](https://www.twilio.com/en-us/sms/pricing/ca), read 2026-11-24 |
| Canadian long code $1.15/month; toll-free $2.15/month | Same page |
| Trial: no custom body, 5 verified recipients, 30 days, 100 messages | [Trials](https://www.twilio.com/docs/usage/trials), [SMS trial](https://www.twilio.com/docs/usage/trials/try-out-sms) |
| No alphanumeric sender in Canada; toll-free requires verification | [Canada guidelines](https://www.twilio.com/guidelines/ca/sms) |
| Geo permissions are set in the console only | [Geo permissions](https://www.twilio.com/docs/messaging/guides/sms-geo-permissions) |
| Test credentials send nothing and charge nothing | [Test credentials](https://www.twilio.com/docs/iam/test-credentials) |
| Minimum top-up, tax, default body retention, send-only restricted key | **Not confirmed.** Check at step S4 |

## Steps and budget

Gates held by Marc, as in #1206. Sizes are estimates read from the SMTP steps (#1254, #1280: 6,600 and 1,900 lines added,
the first including generated clients), not commitments.

| Step | Content | Size | Gate |
|------|---------|------|------|
| **S0** | This brief, critical review on a different model family, corpus alignment (`I-2026-0024`, `V-2026-0007`, ADR-0018 note, D5 amendment), dedicated issue | 1 short session, docs PR | Findings settled |
| **S1** | SMS contract; core refactor per channel; send endpoint; phone editable at `CREATED`; audit event; per-enrollment cap; mock plays SMS; OpenAPI, Bruno, `ENDPOINT.md`, `CONFIGURATION.md` | 1 to 2 sessions, ~2,000 lines plus generated | Email scenarios unchanged; all outcomes and refusals tested |
| **S2** | `ezkey-connector-twilio` against a fake Twilio server; CI scenario, canary; Dependabot, licences | 1 session, ~1,200 lines | Replayable without a human or a Twilio account |
| **S3** | Admin UI second channel, en/fr, Playwright; `SECURITY_POSTURE.md` | 1 session, ~600 lines | Two channels readable in 3 seconds |
| **S4** | Real trial from the Mac with a paid Twilio account, one long code; Bell, Rogers and Telus phones if available | Half a session plus Marc | **Marc receives the code and enrolls a phone** |
| **S5** | Community package, VM memory, runbook | Separate GO, after the SMTP window | End to end on `ezkey.online` |

**Money (S4), USD, before tax:** $1.15 for the number for a month; about $0.017 per one-segment SMS (up to $0.034
for two). 50 trial messages cost about $0.85 to $1.70. **Under $3 of usage**; the top-up minimum is not confirmed.
Running a year at 50 SMS per month: about $24 to $34.

## Rollback

With no SMS URL set, the core shows no SMS button. Nothing is stored and there is no migration, so reverting is a
configuration change. Email delivery is independent.

## Evidence plan

- Unit: contract strictness, GSM-7 folding, link neutralization, typed errors, caps.
- Functional: each outcome and refusal, a slow connector with the pool test, email non-regression, log canary.
- Playwright: send, refusal, missing phone, test mode.
- Real: S4. Manual note: Twilio log shows the message; the enrollment reaches `VERIFIED`.

## Exit criteria

A person enrolls with the QR received by email and the code received by SMS; with both connectors off, the same
enrollment still works on screen.
