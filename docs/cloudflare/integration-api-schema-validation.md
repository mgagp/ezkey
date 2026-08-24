# Integration API Cloudflare schema validation (EXP1)

Manual workflow for Cloudflare **API Shield schema validation** on the Integration API.
This is ingress *shape* enforcement (method, path, JSON body). It does **not** check
API-key validity, IP whitelist, or enrollment ownership.

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

2. Package the EXP1-localized artifact (separately runnable; does not change the canonical spec):

   ```bash
   ./scripts/package-integration-api-cloudflare-schema.sh
   ```

   Output: `specs/integration-api/deployments/exp1-cloudflare-openapi.json`

   That file must contain **exactly one** server:

   `https://exp1-integration-api.ezkey.org`

   Cloudflare API Shield accepts **OpenAPI 3.0 only**. The packaging step downlevels the
   canonical 3.1 spec (`type: ["string","null"]` → `type: string` + `nullable: true`) and sets
   `openapi` to `3.0.3`. Do not upload `specs/integration-api/openapi-spec.json` — that 3.1 file
   fails with Cloudflare error 50010.

3. Do **not** hand-edit the JSON. Regenerate it before each upload.

## Operations that must appear once

Cloudflare must list each of these **once**, under `exp1-integration-api.ezkey.org` only:

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
./scripts/cloudflare/upload-integration-api-schema-exp1.sh --list
./scripts/cloudflare/upload-integration-api-schema-exp1.sh --upload --package
./scripts/cloudflare/upload-integration-api-schema-exp1.sh --delete <schema-id>
```

`--list` verifies the zone-scoped token and prints uploaded schemas. `--upload` POSTs
`specs/integration-api/deployments/exp1-cloudflare-openapi.json` only. `--delete` removes one
schema UUID from `--list` (repeatable). The script refuses to delete the Auth schema
(`ezkey-auth-api-exp1`). The script never uses `CLOUDFLARE_API_TOKEN` (Pages) and does not
change Block / None mitigation.

Do not couple this upload to Lightsail deploy. Do not use Wrangler.

Dashboard remains valid for the first bind and for Set action. Labels move; look for
**API Shield** / **Schema validation** on the zone that fronts
`exp1-integration-api.ezkey.org`.

1. Confirm the three operations above appear once under **`exp1-integration-api.ezkey.org`**.
2. Confirm the Auth schema remains (`9d7d279b-9eff-4661-b536-8afb5a9b6dbf` at first Auth
   upload; re-check `--list` if Auth was re-uploaded).
3. Add the three operations to the Web Assets inventory if they have no `operation_id` yet,
   then set per-operation mitigation (same Auth pattern). Zone default stays **None**.
4. Set the **Set action** mitigation only after an explicit operator decision (see below).
   The upload script does **not** change Block / None.

### Set action (EXP1 operator decision, 2026-08-23)

**Product default** for any deployment that might see third-party traffic remains **None**
(observe / log). A false Block on create / wait / cancel would interrupt machine-to-machine
MFA for someone else.

**EXP1 exception:** the operator confirmed EXP1 has no external evaluators and that the
maintainer is the only tester — same reason as Auth. On `exp1-integration-api.ezkey.org`
only, **Block** is accepted so schema mismatches fail at the edge.

Uploaded schema: `ezkey-integration-api-exp1` (`bf5cab36-d09c-4b0e-b551-5c23bc17682f`).
Auth schema `9d7d279b-9eff-4661-b536-8afb5a9b6dbf` remains.

- Cloudflare labels: **Block** = reject non-compliant requests; **None** = allow them through;
  **Default** follows the zone default (today None).
- If a maintainer test locks you out of EXP1 Integration create/wait/cancel, switch the action
  back to **None**, inspect the schema-validation events, then return to Block only after the
  mismatch is understood.
- Do **not** copy EXP1 Block to a later public or multi-user install without a fresh observe
  period. Escape hatch remains **None**.

## Functional checks after upload

Exercise the live EXP1 Integration API **through** the Cloudflare hostname, not localhost.

1. **Bruno (optional)** — Select collection environment **`exp1`**
   (`bruno/environments/exp1.bru`). `base_url_integration_api` is already
   `https://exp1-integration-api.ezkey.org`. For a schema **mismatch**, use
   `cloudflare-schema/negative-create-wrong-enrollment-id-type`.
   `./scripts/bruno-health.sh` remains the **local** smoke — do not treat it as Cloudflare
   evidence, and do not add this probe to those suites.

2. **Negative probe** — `POST /api/v1/auth-attempts` with `enrollmentId` as a string. Cloudflare
   validates request shape only. Dummy HTTP Basic credentials are enough for the edge check.

   - If Set action is **Block**: expect HTTP **403** Error **1020** (`cloudflare_error`).
   - If Set action is **None**: the request reaches origin (typically 401 without a valid API
     key, or 400 after Jackson/Bean Validation). That is **not** schema-Block evidence.

3. **Positive path (when credentials exist)** — a schema-valid create that fails binding
   (unknown enrollment, wrong integration, inactive enrollment) should return origin
   ProblemDetail (`type` starts with `https://ezkey.io/problems/`), not 1020.

4. **Cloudflare logs** — look for schema-validation events on
   `exp1-integration-api.ezkey.org`. Record drops or mismatches in the TB close-out.

## Reading responses: origin vs Cloudflare

Two RFC 9457-shaped JSON bodies can come back from
`https://exp1-integration-api.ezkey.org`. They are **not** the same contract.

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
(flip EXP1 to **None**, fix the packaged spec). Do not retry 1020 (`retryable: false`).

Cloudflare format and `Accept` negotiation:
[Error responses](https://developers.cloudflare.com/fundamentals/reference/error-responses/).

## What this does not prove

- API-key authentication, IP whitelist, or enrollment ownership
- Admin API schema validation
- Automated schema deploy from Lightsail
- That **Block** is the product default (it is not)

## Related

- Packaging script: [`scripts/package-integration-api-cloudflare-schema.sh`](../../scripts/package-integration-api-cloudflare-schema.sh)
- Upload script: [`scripts/cloudflare/upload-integration-api-schema-exp1.sh`](../../scripts/cloudflare/upload-integration-api-schema-exp1.sh)
- Canonical refresh: [`scripts/update-specs.sh`](../../scripts/update-specs.sh)
- Auth sibling: [auth-api-schema-validation.md](auth-api-schema-validation.md)
- Cloudflare docs index: [README.md](README.md)
