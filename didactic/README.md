# Didactic protocol lab

Reproducible **enrollment → verify → authentication attempt** flows against a local Ezkey Docker stack (clean-start). The tooling mirrors the numbered **Bruno** requests in `bruno/enrollments-auth/` and `bruno/auth-attempts-auth/`, using the **Crypto API** as the cryptography oracle (same contract as demos).

## Prerequisites

- Clean-start Docker stack running: **Admin API**, **Auth API**, and **Crypto API** reachable from the host.
- An admin **Bearer** token (`Authorization: Bearer …`) obtained passwordless/Docker-admin flow (outside this tool unless you paste the token).
- A known **`integrationId`** for enrollment creation.

## Quick start

```bash
cd didactic
python3 -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r protocol_lab/requirements.txt

cp scenarios/cleanstart-example/run.example.yaml scenarios/cleanstart-example/run.yaml
# Edit run.yaml: integration_id, URLs, enrollment name.

export EZKEY_ADMIN_TOKEN='your-bearer-token'
python -m protocol_lab run --config scenarios/cleanstart-example/run.yaml --artifacts-dir scenarios/cleanstart-example/artifacts
python -m protocol_lab render --template templates/article-full.md --artifacts-dir scenarios/cleanstart-example/artifacts \
  --output scenarios/cleanstart-example/artifacts/article.generated.md --full-transcript
```

Use **`--full-transcript`** (alias for **`--publish-secrets`**) for **local lab articles** so signatures, tokens, and keys appear **in full** in the Markdown—appropriate only for **throwaway clean-start** output under `artifacts/` (gitignored). For snippets shared outside the machine, omit `--full-transcript` or run **`protocol_lab redact`** afterward.

Templates substitute `<<<ARTIFACT step_id request|response>>>` and `<<<SUMMARY_TTL>>>` (TTL block from `summary.json`). See [`templates/article-full.md`](templates/article-full.md) and [`templates/article-thin.md`](templates/article-thin.md).

### Environment variable overrides

| Variable | Overrides YAML key |
|----------|-------------------|
| `EZKEY_ADMIN_TOKEN` | `admin_bearer_token` |
| `EZKEY_ADMIN_BASE_URL` | `base_urls.admin` |
| `EZKEY_AUTH_BASE_URL` | `base_urls.auth` |
| `EZKEY_CRYPTO_BASE_URL` | `base_urls.crypto` |

## Scenario file (`run.yaml`)

| Field | Description |
|-------|--------------|
| `base_urls.admin` | Admin API origin (no trailing slash) |
| `base_urls.auth` | Auth API origin |
| `base_urls.crypto` | Crypto API origin (typically `http://localhost:9090`) |
| `integration_id` | Numeric integration FK for enrollment create |
| `admin_bearer_token` | Bearer string **without** `Bearer ` prefix; prefer env override |
| `enrollment.name` | Display name for the new enrollment |
| `enrollment.auth_attempt_challenge_required` | Stored on enrollment; keep `false` for simple demo |
| `scenario.respond_accepted` | `true` = approve MFA; `false` = deny |
| `scenario.auth_challenge_requested` | `challengeRequested` on auth attempt create (use `false` unless testing 2-digit challenge) |
| `wait.timeout_seconds` / `wait.poll_seconds` | Admin `wait` query params |

## Outputs (under `--artifacts-dir`)

| File | Purpose |
|------|---------|
| `state.json` | Rolling variables (IDs, keys, truncated metadata) |
| `steps.jsonl` | One JSON object per executed step |
| `summary.json` | High-level outcome and TTL hints |

## Commands

```
python -m protocol_lab run --config PATH [--artifacts-dir DIR] [--dry-run] [--skip-admin-wait]
python -m protocol_lab render --template PATH --artifacts-dir DIR --output PATH [--publish-secrets] [--full-transcript]
python -m protocol_lab redact --artifacts-dir DIR [--fields field1,field2]
```

- **`--publish-secrets` / `--full-transcript`:** emit complete bearer strings, private keys, and signatures in rendered Markdown (unsafe outside a **local lab**).
- **`redact`:** replaces known secret keys recursively in copied JSON (for sharing summaries).

### Integration record (lab)

`protocol_lab` needs an existing **`integration_id`**. Create the integration once via Admin API (`POST /api/v1/integrations`) with **operator-realistic** `name` / `description`. For **`description` text**, prefer the **astronomy / space-exploration** metaphor layer described in [`AGENTS.md`](AGENTS.md) (plain IT tone, no slogans). Use the returned **`id`** in `run.yaml`.

## Parity checklist

See [`BRUNO_PARITY.md`](BRUNO_PARITY.md) for numbered Bruno alignment.

## Validation checklist (agent / CI smoke)

Run without a stack (prints planned steps, writes `state.json`):

```powershell
cd didactic
python -m pip install -r protocol_lab/requirements.txt
python -m protocol_lab run --config scenarios/cleanstart-example/run.example.yaml `
  --artifacts-dir scenarios/cleanstart-example/artifacts_validate --dry-run
python -m compileall protocol_lab
```

With a healthy clean-start stack, **Admin** `GET /actuator/health`, **Auth** `GET /actuator/health`, and **Crypto** `GET /actuator/health`; then run the same command **without** `--dry-run`, with `EZKEY_ADMIN_TOKEN` set.

## Related site content

This folder is deliberately separate from [`sites/ezkey-org/`](../sites/ezkey-org/). Articles generated here can be summarized or ported to ezkey.org in a distinct editorial step.
