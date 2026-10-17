# Integration API Cloudflare schema validation (community)

Manual workflow for Cloudflare **API Shield schema validation** on the Integration API
(`integration-api.ezkey.online`). This is ingress *shape* enforcement (method, path, JSON body).
It does **not** check API-key validity, IP whitelist, or enrollment ownership.

Authority: `TB-2026-08-23-integration-api-cloudflare-schema`,
`I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`.

Auth API already proved this recipe. Do not invent a third packaging stack. Auth runbook:
[auth-api-schema-validation.md](auth-api-schema-validation.md).

## What you upload

1. Refresh the host-neutral canonical spec from a running Integration API (after a
   clean-start when the contract changed):

   ```bash
   ./scripts/update-specs.sh --integration-only
   ```

2. Package a deployment-localized artifact (separately runnable; does not change the canonical
   spec). Default is the community host:

   ```bash
   ./scripts/package-integration-api-cloudflare-schema.sh
   # or: --community
   # or: --server https://integration-api.ezkey.online
   # or: EZKEY_INTEGRATION_CLOUDFLARE_SERVER_URL=https://integration-api.ezkey.online ./scripts/package-integration-api-cloudflare-schema.sh
   ```

   Output: `specs/integration-api/deployments/community-cloudflare-openapi.json` with **exactly one**
   server: `https://integration-api.ezkey.online`.

   Cloudflare API Shield accepts **OpenAPI 3.0 only**. The packaging step downlevels the
   canonical 3.1 spec (`type: ["string","null"]` → `type: string` + `nullable: true`) and sets
   `openapi` to `3.0.3`. Do not upload `specs/integration-api/openapi-spec.json` — that 3.1 file
   fails with Cloudflare error 50010.

3. Do **not** hand-edit the JSON. Regenerate it before each upload.

## Operations that must appear once

Cloudflare must list each of these **once**, under `integration-api.ezkey.online` only:

| Method | Path | Notes |
| ------ | ---- | ----- |
| POST | `/api/v1/auth-attempts` | Create MFA attempt; integration from API key |
| GET | `/api/v1/auth-attempts/{id}/wait` | Long-poll wait |
| POST | `/api/v1/auth-attempts/{id}/cancel` | Cancel |

If you see the same path twice (localhost plus a public host), the uploaded artifact is not the
localized package. Stop and regenerate.

## Cloudflare upload

Preferred path (Git Bash, repo-root `.env` with `CLOUDFLARE_API_SHIELD_TOKEN`):

```bash
./scripts/cloudflare/upload-integration-api-schema.sh --list
./scripts/cloudflare/upload-integration-api-schema.sh --upload --package
./scripts/cloudflare/upload-integration-api-schema.sh --delete <schema-id>
```

Thin alias (same defaults):

```bash
./scripts/cloudflare/upload-integration-api-schema-community.sh --list
./scripts/cloudflare/upload-integration-api-schema-community.sh --upload --package
```

`--list` verifies the zone-scoped token and prints uploaded schemas. `--upload` POSTs
`specs/integration-api/deployments/community-cloudflare-openapi.json` only. `--delete` removes one
schema UUID from `--list` (repeatable). The script refuses to delete the Auth schema
(`ezkey-auth-api-community`). The script never uses `CLOUDFLARE_API_TOKEN` (Pages) and does not
change Block / None mitigation.

Env overrides:
`EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA`, `EZKEY_INTEGRATION_CLOUDFLARE_SCHEMA_NAME`,
`EZKEY_INTEGRATION_CLOUDFLARE_SERVER_URL`, `CLOUDFLARE_ZONE_NAME`.

Do not couple this upload to Lightsail deploy. Do not use Wrangler.

Dashboard remains valid for the first bind and for Set action. Labels move; look for
**API Shield** / **Schema validation** on the zone that fronts `integration-api.ezkey.online`.

1. Confirm the three operations above appear once under the packaged hostname only.
2. Confirm the Auth schema remains (re-check `--list` if Auth was re-uploaded).
3. Add the three operations to the Web Assets inventory if they have no `operation_id` yet,
   then set per-operation mitigation (same Auth pattern). Zone default stays **None**.
4. Set the **Set action** mitigation only after an explicit operator decision (see below).
   The upload script does **not** change Block / None.

### Set action (community / ezkey.online)

**Mitigation stays None.** Community hosts third-party / M2M traffic; observe / log only until
an explicit operator decision says otherwise. A false Block on create / wait / cancel would
interrupt machine-to-machine MFA for someone else.

- Cloudflare labels: **Block** = reject non-compliant requests; **None** = allow them through;
  **Default** follows the zone default (today None).
- Do **not** set **Block** on a multi-user community install without a fresh observe period.
  Escape hatch remains **None**.

## Functional checks after upload

Exercise the live community Integration API **through** the Cloudflare hostname, not localhost.

1. **Bruno (optional)** — Select collection environment **`community`**
   (`bruno/environments/community.bru`). `base_url_integration_api` is already
   `https://integration-api.ezkey.online`. For a schema **mismatch**, use
   `cloudflare-schema/negative-create-wrong-enrollment-id-type`.
   `./scripts/bruno-health.sh` remains the **local** smoke — do not treat it as Cloudflare
   evidence, and do not add this probe to those suites.

2. **Negative probe** — `POST /api/v1/auth-attempts` with `enrollmentId` as a string. Cloudflare
   validates request shape only. Dummy HTTP Basic credentials are enough for the edge check.

   - If Set action is **None** (community default): the request reaches origin (typically 401
     without a valid API key, or 400 after Jackson/Bean Validation). That is **not**
     schema-Block evidence.
   - If Set action were **Block**: expect HTTP **403** Error **1020** (`cloudflare_error`).

3. **Positive path (when credentials exist)** — a schema-valid create that fails binding
   (unknown enrollment, wrong integration, inactive enrollment) should return origin
   ProblemDetail (`type` starts with `https://ezkey.io/problems/`), not 1020.

4. **Cloudflare logs** — look for schema-validation events on
   `integration-api.ezkey.online`. Record drops or mismatches in the TB close-out.

## Reading responses: origin vs Cloudflare

Two RFC 9457-shaped JSON bodies can come back from
`https://integration-api.ezkey.online`. They are **not** the same contract.

**Origin (Integration API)** — the request reached Spring. `type` is under
`https://ezkey.io/problems/`. Clients should show origin `detail` only when `type` starts with
that prefix.

**Cloudflare edge** — API Shield / WAF blocked before origin. Schema **Block** on a type
mismatch returns HTTP **403** Error **1020**. Clients that send `Accept: application/json` or
`application/problem+json` get Cloudflare's structured error (not the HTML 1020 page). Markers:
`cloudflare_error: true`, `error_code: 1020`, `error_name: "firewall_rule_blocked"`, `ray_id`,
and `type` under `https://developers.cloudflare.com/…`.

How to tell them apart: Ezkey `type` prefix vs `cloudflare_error` / Cloudflare `type` URL /
HTTP 403 1020. A Spring ProblemDetail means the request **reached origin** — not schema-Block
evidence.

SDK / Demo App ACME / other Integration clients must **not** special-case 1020 copy,
`what_you_should_do`, or `ray_id` as Integration API domain errors. A well-formed client must
not produce a schema violation; if one appears it is schema drift or an edge false positive
(keep mitigation **None**, fix the packaged spec). Do not retry 1020 (`retryable: false`).

Cloudflare format and `Accept` negotiation:
[Error responses](https://developers.cloudflare.com/fundamentals/reference/error-responses/).

## What this does not prove

- API-key authentication, IP whitelist, or enrollment ownership
- Admin API schema validation
- Automated schema deploy from Lightsail
- That **Block** is the product default (it is not; community stays **None**)

## Related

- Packaging script: [`scripts/package-integration-api-cloudflare-schema.sh`](../../scripts/package-integration-api-cloudflare-schema.sh)
- Upload: [`scripts/cloudflare/upload-integration-api-schema.sh`](../../scripts/cloudflare/upload-integration-api-schema.sh)
- Upload alias: [`scripts/cloudflare/upload-integration-api-schema-community.sh`](../../scripts/cloudflare/upload-integration-api-schema-community.sh)
- Canonical refresh: [`scripts/update-specs.sh`](../../scripts/update-specs.sh)
- Auth sibling: [auth-api-schema-validation.md](auth-api-schema-validation.md)
- Cloudflare docs index: [README.md](README.md)
