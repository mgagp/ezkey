# Ezkey API key CLI workflow for agents and QA

**Audience:** QA agents and operators who need Integration API credentials without putting the secret in chat, logs, or model context.

**Related:** [#1043](https://github.com/mgagp/ezkey/issues/1043), [#1015](https://github.com/mgagp/ezkey/issues/1015).

## Why the secret is shown once

Ezkey stores only a BCrypt hash of the API secret. The plaintext `secretKey` appears in the `POST /api/v1/api-keys` **201** response exactly once. There is no retrieve-secret API. That is intentional: lost secrets are rotated (create a new key, revoke the old one), never recovered.

## Create → use → revoke (CLI)

Placeholders only below — substitute your own integration id and a local path outside the repo.

### 1. Create (secret never on stdout)

```bash
ezkey admin api-key create \
  --integration-id <INTEGRATION_ID> \
  --description "qa-agent-placeholder" \
  --out-file ./api-key.env
```

Stdout shows only the integration key (ikey) and the file path. The file is created with mode `600` (owner read/write). Format is minimal `.env`:

```bash
EZKEY_INTEGRATION_KEY=ezkey_ikey_PLACEHOLDER
EZKEY_SECRET_KEY=ezkey_skey_PLACEHOLDER
```

If the path already exists, the command refuses to overwrite unless you pass `--force` (atomic replace).

### 2. Use

Source or load the file in the agent/runtime that calls Integration API (HTTP Basic: ikey as username, skey as password). Do not paste the secret into chat or commit the file.

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
