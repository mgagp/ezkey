# Directory connector sketch

**Status:** sketch, not a contract (step 2 of [#1206](https://github.com/mgagp/ezkey/issues/1206)).
**Its one job:** check whether directories could force a change to the
[delivery contract](../../../../ezkey-connectors/contracts/delivery-v0.openapi.yaml).
**Scale:** an SME or a business unit, with a handful of changes per run
([`product-intent.md`](../../product-intent.md) § *Scale and positioning compass*).

## Shape

The connector runs inside the company network and only opens outbound connections. Every few hours it reads the
configured subtree or group, then sends Ezkey one complete snapshot.

```text
POST <inbound surface>/v0/directory/reconcile
Authorization: Bearer <inbound token, stored hashed by Ezkey>

{ "runId": "...", "mode": "dry-run | apply",
  "entries": [ { "externalId": "...", "email": "...", "displayName": "...", "enabled": true } ] }

-> { "runId": "...", "result": "applied | previewed | refused",
     "counts": { "create": 1, "deactivate": 0, "reactivate": 0, "revoke": 0 } }
```

- **A complete snapshot, not a delta:** Ezkey computes the plan, and a missed run corrects itself at the next one.
- **Empty read or mass change:** refused, nothing applied, alert raised.
- **Silence:** a missing run raises a staleness alert.
- **Effects:** the mapping table and safety rails in the [decision pack](README.md#mapping-directory-state-to-the-enrollment-lifecycle) apply.

## Does it force a change to the delivery contract?

| Directory need | Effect on the delivery contract |
|----------------|---------------------------------|
| Recipient comes from the directory | None. It fills `contactEmail`, the contract's one source |
| Automatic invitation after sync | Adds one `trigger` value. The contract already tells connectors not to branch their security on it |
| No operator present to retry | None. A core-internal outbox calls the same contract ([options](README.md#dedicated-exchange-table-considered-not-retained-marc-2026-11-06)) |
| Retried sends | None. `sendId` stays for traceability, and a duplicate of the same QR is harmless |
| Language from the directory | None. It would feed the existing `locale` ([#1250](https://github.com/mgagp/ezkey/issues/1250)) |
| A batch of invitations | None. One call per person suits this scale, so no bulk route |
| Non-human actor in the audit | None. `*_by_admin_id` and the audit entry are core changes |
| Tenant and integration mapping | None. The job already carries `tenantRef`; the mapping is a directory-phase question |

**Conclusion: the directory case holds on paper.** Only one additive value is foreseen. The two families share
conventions (bearer token, versioned path, typed errors) but keep separate routes, tokens and payloads.

**What this sketch does not settle:** the
[open questions for the directory phase](README.md#open-questions-for-the-directory-phase), including where the inbound
surface lives and whether it aligns with SCIM. Automatic invitation also needs its own security review.
