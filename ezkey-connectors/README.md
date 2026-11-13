# Ezkey connectors

Optional external services connected to an Ezkey base by configuration. Ezkey stays complete with none.
Lab posture applies to this folder.

- **Contract:** [`contracts/delivery-v1.openapi.yaml`](contracts/delivery-v1.openapi.yaml), frozen as `v1`
  after the real Gmail trial at step 6 (2026-11-09). Any shape change makes a `v2`.
- **Decisions:** [ADR-0018](../product-docs/global/architecture-decisions.md#adr-0018--connector-spi-for-external-services),
  [decision pack](../product-docs/global/decisions/2026-11-04-connector-spi/README.md) and
  [`TB-2026-11-06`](../product-docs/global/backlog/TB-2026-11-06-connector-delivery-contract-v0.md).
- **Progress:** [#1206](https://github.com/mgagp/ezkey/issues/1206). Configuration notes:
  [#1246](https://github.com/mgagp/ezkey/issues/1246).

## Build

The connectors are modules of the root Maven reactor (#1278): they share its versions, Spotless, Checkstyle and
`THIRD-PARTY.txt`, and the `backend` CI job runs their unit tests. Their only link to Ezkey is the HTTP contract: an
enforcer rule in [`pom.xml`](pom.xml) fails the build if a connector depends on any `org.ezkey` module. Each connector
has its own Dockerfile, built from the repository root.

## Trial with a real provider

[`docker/docker-compose.connector-smtp-gmail.yml`](../docker/docker-compose.connector-smtp-gmail.yml) replaces the
Mailpit overlay and sends through Gmail with an app password (#1206 step 6). Copy
[`docker/connector-smtp.local.env.example`](../docker/connector-smtp.local.env.example) to the gitignored
`docker/connector-smtp.local.env`, and set `EZKEY_QR_AUTH_BASE_URL` to a public HTTPS URL of the Auth API, since the
email names that server and the phone must reach it.

On the community host the connector sends through Amazon SES with its own Lightsail overlay
([`experimental-hybrid/lightsail/docker-compose.connector-smtp.yml`](../experimental-hybrid/lightsail/docker-compose.connector-smtp.yml));
runbook in [`docs/lightsail/community-host.md`](../docs/lightsail/community-host.md) § *Email delivery connector* (#1206 step 7).

```bash
# from the repository root
mvn -pl ezkey-connectors/ezkey-connector-mock,ezkey-connectors/ezkey-connector-smtp -am verify
docker build -f ezkey-connectors/ezkey-connector-smtp/Dockerfile .
```

## Mock connector

[`ezkey-connector-mock`](ezkey-connector-mock/) is the test double for the contract. It delivers nothing, identifies
itself with `testMode: true`, and refuses to start outside an explicit test mode.

```bash
EZKEY_CONNECTOR_MOCK_TEST_MODE=true EZKEY_CONNECTOR_MOCK_TOKEN=<token> \
  java -jar ezkey-connectors/ezkey-connector-mock/target/ezkey-connector-mock-*.jar
```

| Setting | Default | Meaning |
|---------|---------|---------|
| `EZKEY_CONNECTOR_MOCK_TEST_MODE` | `false` | Must be `true`, or it refuses to start |
| `EZKEY_CONNECTOR_MOCK_TOKEN` | *(none)* | Required bearer token |
| `EZKEY_CONNECTOR_MOCK_NEXT_TOKEN` | *(none)* | Second token accepted during a rotation |
| `EZKEY_CONNECTOR_MOCK_PORT` | `8090` | Port |
| `EZKEY_CONNECTOR_MOCK_DEGRADED` | `false` | Health answers `degraded` |
| `EZKEY_CONNECTOR_MOCK_SLOW_SECONDS` | `5` | Wait in the `mock-slow` scenario |

The recipient's local part picks the scenario, so the mock keeps no state:

| Recipient | Answer |
|-----------|--------|
| Any other address | `200 accepted` |
| `mock-provider-rejected@…`, `mock-recipient-refused@…`, `mock-provider-unavailable@…`, `mock-cap-reached@…` | `422` with that code |
| `mock-provider-timeout@…` | `504 provider-timeout` |
| `mock-server-error@…` | `500` |
| `mock-redirect@…` | `302` |
| `mock-unreadable@…` | `200` outside the contract |
| `mock-slow@…` | Waits, then `200 accepted` |

## SMTP connector

[`ezkey-connector-smtp`](ezkey-connector-smtp/) sends the enrollment QR by email (#1206 step 5). It owns fixed templates
in English and French, attaches the QR inline (`cid:`), and never sends the challenge. Its unit tests use an in-JVM
SMTP server (GreenMail, test scope only).

| Setting | Default | Meaning |
|---------|---------|---------|
| `EZKEY_CONNECTOR_SMTP_TOKEN` | *(none)* | Required bearer token |
| `EZKEY_CONNECTOR_SMTP_NEXT_TOKEN` | *(none)* | Second token accepted during a rotation |
| `EZKEY_CONNECTOR_SMTP_PORT` | `8091` | HTTP port |
| `EZKEY_CONNECTOR_SMTP_HOST` | *(none)* | SMTP host. Without it, or without a sender, health says `degraded` |
| `EZKEY_CONNECTOR_SMTP_SMTP_PORT` | `587` | SMTP port |
| `EZKEY_CONNECTOR_SMTP_USERNAME`, `EZKEY_CONNECTOR_SMTP_PASSWORD` | *(none)* | SMTP credentials; no authentication when the user is empty |
| `EZKEY_CONNECTOR_SMTP_FROM_ADDRESS`, `EZKEY_CONNECTOR_SMTP_FROM_NAME` | *(none)*, `Ezkey` | Sender |
| `EZKEY_CONNECTOR_SMTP_SECURITY` | `starttls` | `starttls` (required), `tls` (implicit), or `plain` |
| `EZKEY_CONNECTOR_SMTP_PLAIN_PRIVATE_NETWORK` | `false` | Must be `true` for `plain`, or the connector refuses to start |
| `EZKEY_CONNECTOR_SMTP_PROVIDER_TIMEOUT_SECONDS` | `15` | Connect, read and write timeout towards the provider |
| `EZKEY_CONNECTOR_SMTP_DAILY_CAP` | `100` | Sends per UTC day that may reach the provider |

- **Daily cap:** counts sends that reached the provider, including unknown outcomes. It lives in memory and restarts at
  zero with the connector: a bound against a stolen token, not the main protection, which stays the core's caps.
- **Outcomes:** a refused connection or an unknown host is `provider-unavailable` (the email did not leave), a refused
  recipient is `recipient-refused`, a read timeout is `provider-timeout`, and anything else is an unknown outcome
  (`500`), since the email may have left.

### Trying it locally with Mailpit

```bash
cd docker
docker compose -f docker-compose.yml -f docker-compose.docker-dev.yml -f docker-compose.with-proxy.yml \
  -f docker-compose.connector-smtp.yml up -d --build --no-deps --wait mailpit connector-smtp admin-api
```

Run it after a clean start. Every email lands in Mailpit, at <http://localhost:8025>. The QR and the email name
`localhost:8080`, which suits an app on the host such as the iOS simulator. The Demo Device runs inside Docker and
cannot reach that address: after importing the QR from the email, clear the Auth API it proposes so it keeps its
configured one. The Admin UI then offers "Send by
email" on an enrollment at `CREATED` that has a contact email.

## Replaying the scenarios

`scripts/ci-connector-scenarios.sh` does this with no human, and the `connectors` CI job runs it:

1. **No connector:** Ezkey works as before.
2. **Rejected connector credential:** the connector counts as unavailable.
3. **Mock connector** on the private Docker network (`docker/docker-compose.connector-mock.yml`): every outcome, refusals,
   15 slow sends past the database pool size, the daily cap and the audit.
4. **Log canary:** logs of every container hold no recipient, token, PNG signature, request body or long base64 block.
5. **SMTP connector** with Mailpit (`docker/docker-compose.connector-smtp.yml`): a real send, read back from Mailpit's
   API. The French wording, the expected server, no challenge, and the inline PNG equal to the on-screen QR.
6. **Mail provider down:** Mailpit stopped, the send is `rejected` with `provider-unavailable`, and the QR stays on
   screen.
7. **Log canary** again, with the SMTP connector. Mailpit is left out: it plays the provider, which sees the recipient
   by design.

`--smtp-only` with `--skip-start` replays scenarios 5 to 7 on a running stack.

It runs a clean start, which wipes the volumes of the Docker Compose project in use, and tears the stack down unless
given `--keep-stack`. Container names are fixed, so stop any other Ezkey stack first. Set `COMPOSE_PROJECT_NAME` to keep
the default project's volumes.
