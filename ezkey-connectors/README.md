# Ezkey connectors

Optional external services connected to an Ezkey base by configuration. Ezkey stays complete with none.
Lab posture applies to this folder.

- **Contract:** [`contracts/delivery-v0.openapi.yaml`](contracts/delivery-v0.openapi.yaml). It stays `v0` until
  the real trial at step 6.
- **Decisions:** [ADR-0018](../product-docs/global/architecture-decisions.md#adr-0018--connector-spi-for-external-services),
  [decision pack](../product-docs/global/decisions/2026-11-04-connector-spi/README.md) and
  [`TB-2026-11-06`](../product-docs/global/backlog/TB-2026-11-06-connector-delivery-contract-v0.md).
- **Progress:** [#1206](https://github.com/mgagp/ezkey/issues/1206). Configuration notes:
  [#1246](https://github.com/mgagp/ezkey/issues/1246).

## Mock connector

[`ezkey-connector-mock`](ezkey-connector-mock/) is the test double for the contract, outside the root Maven reactor. It
delivers nothing, identifies itself with `testMode: true`, and refuses to start outside an explicit test mode.

```bash
cd ezkey-connector-mock
mvn test
mvn -DskipTests package
EZKEY_CONNECTOR_MOCK_TEST_MODE=true EZKEY_CONNECTOR_MOCK_TOKEN=<token> java -jar target/ezkey-connector-mock-0.1.0.jar
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

## Replaying the scenarios

`scripts/ci-connector-scenarios.sh` does this with no human, and the `connectors` CI job runs it:

1. **No connector:** Ezkey works as before.
2. **Rejected connector credential:** the connector counts as unavailable.
3. **Mock connector** on the private Docker network (`docker/docker-compose.connector-mock.yml`): every outcome, refusals,
   15 slow sends past the database pool size, the daily cap and the audit.
4. **Log canary:** logs of every container hold no recipient, token, PNG signature, request body or long base64 block.

It runs a clean start, which wipes the volumes of the Docker Compose project in use, and tears the stack down unless
given `--keep-stack`. Container names are fixed, so stop any other Ezkey stack first. Set `COMPOSE_PROJECT_NAME` to keep
the default project's volumes.
