# Ezkey API key CLI workflow for agents and QA

**Audience:** QA agents and operators who need Integration API credentials without putting the secret in chat, logs, or model context.

**Related:** [#1043](https://github.com/mgagp/ezkey/issues/1043), [#1015](https://github.com/mgagp/ezkey/issues/1015).

## Why the secret is shown once

Ezkey stores only a BCrypt hash of the API secret. The plaintext `secretKey` appears in the `POST /api/v1/api-keys` **201** response exactly once. There is no retrieve-secret API. That is intentional: lost secrets are rotated (create a new key, revoke the old one), never recovered.

## Hand-off model

The **operator** runs `ezkey admin api-key create … --out-file …` on the agent’s host using the operator’s own admin credentials. The agent must **not** hold those admin credentials. After create, the agent loads the credentials file into a **subprocess environment** (for Integration API calls) and must **never** print the secret: no `cat`, no shell `set -x` / command trace that echoes the value, and no tool output that would put the secret into the model context.

Mode `600` is a **POSIX best-effort**. On Windows the file inherits folder ACLs; treat the destination directory as the access boundary.

## Create → use → revoke (CLI)

Placeholders only below — use a path **outside the repository** (never commit credentials; root `.gitignore` also ignores `api-key*.env`).

### 1. Create (secret never on stdout)

```bash
mkdir -p ~/.config/ezkey-qa
chmod 700 ~/.config/ezkey-qa

ezkey admin api-key create \
  --integration-id <INTEGRATION_ID> \
  --description "qa-agent-placeholder" \
  --expires-at "<EXPIRY_ISO8601>" \
  --ip-whitelist "<CAMPAIGN_CIDR>" \
  --out-file ~/.config/ezkey-qa/api-key.env
```

Prefer `--expires-at` and `--ip-whitelist` for a campaign key so blast radius stays bounded.

Stdout shows the **API Key ID** (non-secret, needed for revoke), the integration key (ikey), and the file path. The file is created with mode `600` when POSIX permissions are available. Format is minimal `.env`:

```bash
EZKEY_INTEGRATION_KEY=ezkey_ikey_PLACEHOLDER
EZKEY_SECRET_KEY=ezkey_skey_PLACEHOLDER
```

If the path already exists, the command refuses to overwrite unless you pass `--force` (atomic replace of a non-symlink file). Symbolic links are always refused. The out-file slot is reserved **before** the create POST so a local path failure cannot leave an orphan key with a lost secret.

### 2. Use

Load the file into the agent/runtime that calls Integration API (HTTP Basic: ikey as username, skey as password) without printing it. Do not paste the secret into chat or commit the file.

Optional: also persist into the CLI global config with `--save-key` on create (same secret, never printed when `--out-file` is set). Prefer a dedicated out-file for agent handoff.

### 3. Revoke

When the campaign ends or the secret may have leaked into a transcript:

```bash
ezkey admin api-key revoke --id <API_KEY_ID>
```

Then delete the local credentials file. Create a new key for the next campaign rather than reusing a revoked one.

## Guardrails

- Never put real secrets, tokens, hostnames, or internal paths in issues, PRs, chat, or repo docs.
- Do not use Admin UI file download for this workflow (CLI-only scope for #1043).
- Creation rate limits and the per-integration active-key cap are unchanged.
