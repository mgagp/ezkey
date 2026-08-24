# Auth API Cloudflare schema validation (EXP1)

Manual first-slice workflow for Cloudflare **API Shield schema validation** on the Auth API.
This is ingress *shape* enforcement (method, path, request body). It does **not** verify
Ed25519 signatures. Cryptographic authority for installation branding stays on the enrolled
client (`POST /api/v1/enrollments/instance-info`).

Authority: `TB-2026-06-02-auth-api-cloudflare-schema-first-slice`,
`I-2026-06-02-openapi-spec-lifecycle-and-cloudflare-validation`.

## What you upload

1. Refresh the host-neutral canonical spec from a running Auth API (after a clean-start when the
   contract changed):

   ```bash
   ./scripts/update-specs.sh --auth-only
   ```

2. Package the EXP1-localized artifact (separately runnable; does not change the canonical spec):

   ```bash
   ./scripts/package-auth-api-cloudflare-schema.sh
   ```

   Output: `specs/auth-api/deployments/exp1-cloudflare-openapi.json`

   That file must contain **exactly one** server:

   `https://exp1-auth-api.ezkey.org`

   Cloudflare API Shield accepts **OpenAPI 3.0 only**. The packaging step downlevels the
   canonical 3.1 spec (`type: ["string","null"]` → `type: string` + `nullable: true`) and sets
   `openapi` to `3.0.3`. Do not upload `specs/auth-api/openapi-spec.json` — that 3.1 file fails
   with Cloudflare error 50010.

3. Do **not** hand-edit the JSON. Regenerate it before each upload.

## Operations that must appear once

Cloudflare must list each of these **once**, under `exp1-auth-api.ezkey.org` only:

| Method | Path | Notes |
| ------ | ---- | ----- |
| GET | `/api/v1/public/instance-info` | Unsigned, display-only |
| POST | `/api/v1/enrollments/bind` | Enrollment bind |
| POST | `/api/v1/enrollments/verify` | Enrollment verify |
| POST | `/api/v1/enrollments/instance-info` | Integration-signed installation trust surface |
| POST | `/api/v1/auth-attempts/pending` | Device poll |
| POST | `/api/v1/auth-attempts/respond` | Device approve/deny |

If you see the same path twice (localhost plus a public host, or `auth-api.ezkey.org` plus EXP1),
the uploaded artifact is not the localized package. Stop and regenerate.

## Cloudflare upload

Preferred path (Git Bash, repo-root `.env` with `CLOUDFLARE_API_SHIELD_TOKEN`):

```bash
./scripts/cloudflare/upload-auth-api-schema-exp1.sh --list
./scripts/cloudflare/upload-auth-api-schema-exp1.sh --upload --package
./scripts/cloudflare/upload-auth-api-schema-exp1.sh --delete <schema-id>
```

`--list` verifies the zone-scoped token and prints uploaded schemas. `--upload` POSTs
`specs/auth-api/deployments/exp1-cloudflare-openapi.json` only. `--delete` removes one
schema UUID from `--list` (repeatable). The script never uses `CLOUDFLARE_API_TOKEN`
(Pages) and does not change Block / None mitigation.

Dashboard remains valid for the first bind and for Set action. Labels move; look for
**API Shield** / **Schema validation** on the zone that fronts `exp1-auth-api.ezkey.org`.

1. Confirm the six operations above appear once under **`exp1-auth-api.ezkey.org`**.
2. Set the **Set action** mitigation (see below).

### Set action (EXP1 operator decision, 2026-08-23)

**Product default** for any deployment that might see third-party traffic remains **None**
(observe / log). A false Block on bind / verify / enrolled instance-info / pending / respond
would interrupt the trust-refresh or MFA path for someone else.

**EXP1 exception:** the operator confirmed EXP1 has no external evaluators in practice and that
the only tester is the maintainer. On that hostname only, **Block** is accepted so schema
mismatches fail at the edge and give immediate feedback.

- Cloudflare labels: **Block** = reject non-compliant requests; **None** = allow them through;
  **Default** follows the zone default (today None).
- If a maintainer test locks you out of EXP1 enroll or MFA, switch the action back to **None**,
  inspect the schema-validation events, then return to **Block** after the mismatch is understood.
- Do **not** copy EXP1 Block to a later public or multi-user install without a fresh observe
  period.

## Functional checks after upload

Exercise the live EXP1 Auth API **through** the Cloudflare hostname, not localhost. With EXP1
on **Block**, a schema mismatch should fail at Cloudflare (typically 4xx) even if Auth API
behind it is healthy.

1. **Public unsigned metadata**

   ```bash
   curl -sS -D - -o /tmp/exp1-instance-info.json \
     https://exp1-auth-api.ezkey.org/api/v1/public/instance-info
   ```

   Expect HTTP 200 and the usual branding JSON. This path has no request body; it mainly confirms
   the hostname and GET operation are recognized.

2. **Bruno (optional)** — Select collection environment **`exp1`**
   (`bruno/environments/exp1.bru`). Replay `public-auth/get-public-instance-info` for the
   positive path. For a schema **mismatch** (the mobile app cannot send one), use
   `cloudflare-schema/negative-bind-wrong-enrollment-id-type`. `./scripts/bruno-health.sh --suite g0`
   remains the **local** public-surface smoke — do not treat it as Cloudflare evidence.

3. **Enrolled trust surface (when you have a live enrollment)** — `POST /api/v1/enrollments/instance-info`
   with a real `enrollmentProofToken` from Demo Device or mobile. Expect HTTP 200 and
   `instanceInfoPayloadSignedByIntegration`. Then confirm the mobile/Demo Device client still
   verifies the signature. Cloudflare passing the request is not that verification.

4. **MFA write path (when convenient)** — one bind or one pending/respond on EXP1 is enough.
   On EXP1 Block, an unexpected 4xx at the edge is the signal; flip to None if you need the
   API itself to stay reachable while you diagnose.

5. **Cloudflare logs** — look for schema-validation events on `exp1-auth-api.ezkey.org`. Note
   any drop or mismatch (extra property, type, missing required field). Record them in the TB
   close-out.

## Reading responses: origin vs Cloudflare

Two RFC 9457-shaped JSON bodies can come back from `https://exp1-auth-api.ezkey.org`. They are
**not** the same contract.

**Origin (Auth API)** — the request reached Spring. `type` is under `https://ezkey.io/problems/`
(Auth catalog: `https://ezkey.io/problems/auth/…`, plus
`https://ezkey.io/problems/system/audit-chain-heartbeat-degraded`). Example from a schema-valid
but unbound pending probe (HTTP 400):

```json
{
  "type": "https://ezkey.io/problems/auth/auth-attempt-binding-failed",
  "title": "Request not acceptable",
  "status": 400,
  "detail": "The authentication request could not be processed.",
  "path": "/api/v1/auth-attempts/pending"
}
```

**Cloudflare edge** — API Shield / WAF blocked before origin. Schema **Block** on a type mismatch
returns HTTP **403** Error **1020**. Clients that send `Accept: application/json` or
`application/problem+json` get Cloudflare's structured error (not the HTML 1020 page). Markers:
`cloudflare_error: true`, `error_code: 1020`, `error_name: "firewall_rule_blocked"`, `ray_id`,
and `type` under `https://developers.cloudflare.com/…`. Example (HTTP 403):

```json
{
  "type": "https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1020/",
  "title": "Error 1020: Access denied",
  "status": 403,
  "detail": "The request was blocked by a Cloudflare firewall rule configured by the site owner.",
  "cloudflare_error": true,
  "error_code": 1020,
  "retryable": false,
  "ray_id": "a2fe09d80bbda2ae"
}
```

How to tell them apart: Ezkey `type` prefix vs `cloudflare_error` / Cloudflare `type` URL / HTTP
403 1020. A Spring ProblemDetail means the request **reached origin** — not schema-Block evidence.

Operator / Bruno: this distinction is the check. End-user apps (mobile, Demo Device lab UI) should
**not** special-case 1020 copy, `what_you_should_do`, or `ray_id` as Auth API domain errors. A
well-formed client must not produce a schema violation; if one appears in production it is schema
drift or an edge false positive (flip EXP1 to **None**, fix the packaged spec). Client-facing
attribution for later mobile work: show Auth `detail` only when `type` starts with
`https://ezkey.io/problems/`; otherwise a generic unreachable / request-failed string. Do not
retry 1020 (`retryable: false`).

Cloudflare format and `Accept` negotiation:
[Error responses](https://developers.cloudflare.com/fundamentals/reference/error-responses/).

## What this does not prove

- Ed25519 verification of `instanceInfoPayloadSignedByIntegration`
- Admin API or Integration API schema validation
- Automated schema deploy from Lightsail
- That **Block** is the product default outside this EXP1 maintainer-only exception

## Related

- Packaging script: [`scripts/package-auth-api-cloudflare-schema.sh`](../../scripts/package-auth-api-cloudflare-schema.sh)
- Canonical refresh: [`scripts/update-specs.sh`](../../scripts/update-specs.sh)
- Cloudflare docs index: [README.md](README.md)
