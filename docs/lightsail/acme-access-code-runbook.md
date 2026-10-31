# Community Acme — access-code slot runbook (restart-based)

Operator procedure for adding or revoking an evaluator `/t/{code}` slot on the community Acme demo.
Config is already external: named volume `demo-app-acme-config` → `/app/config/application.properties`
via `SPRING_CONFIG_ADDITIONAL_LOCATION`. Slots bind as `ezkey.access-codes.<slot>.*` and are read
**at JVM start** — a container restart is required (and acceptable).

Hot-reload of a second mapping file is **deliberately not** implemented. Reopen that path only if
frequent slot additions make restarts painful (Related to #958; premise that mappings were “baked
in the image” was incorrect).

Do **not** put real secrets, IPs, or hostnames in tickets or chat. Paste secrets only into the
container file via `docker exec`, never into this repo.

## Prerequisites

- Community stack up; compose service `demo-app-acme` (container name
  `ezkey-exp-demo-app-acme`) healthy.
- You have the tenant’s **integration key** and **secret key** (from Admin API for that tenant’s
  integration). There is **no** tenant UUID on the slot — only those two keys plus a random code and
  a short label.
- Shell with Docker access to the community host (use the SSH Host alias already configured for
  ops). Prefer Git Bash on Windows (`MSYS_NO_PATHCONV=1` if path conversion interferes).

## Add a slot

1. **Generate a code** (32 lowercase hex — fail-fast validation is `^[0-9a-f]{32,64}$`):

   ```bash
   openssl rand -hex 16
   ```

2. **Backup** the live config as `spring` (uid 100), mode 0600:

   ```bash
   docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
     set -e
     umask 077
     cp /app/config/application.properties /app/config/application.properties.bak
     chmod 600 /app/config/application.properties.bak
   '
   ```

3. **Append four lines** with an atomic write (temp + `mv`) as `spring`. Replace placeholders;
   never echo the secret to the terminal history if you can avoid it (stdin heredoc is fine):

   ```bash
   docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
     set -e
     umask 077
     FILE=/app/config/application.properties
     TMP=$(mktemp /app/config/.application.properties.XXXXXX)
     cat "$FILE" > "$TMP"
     cat >> "$TMP" <<EOF

   # access-code slot (evaluator) — restart required
   ezkey.access-codes.<slot>.code=<32-hex-from-openssl>
   ezkey.access-codes.<slot>.integration-key=<tenant-integration-key>
   ezkey.access-codes.<slot>.secret-key=<tenant-secret-key>
   ezkey.access-codes.<slot>.label=<short-label>
   EOF
     mv "$TMP" "$FILE"
     chmod 600 "$FILE"
   '
   ```

4. **Restart only Acme** (not the whole stack):

   ```bash
   docker restart ezkey-exp-demo-app-acme
   ```

5. **Verify**

   - Logs show a line like `Access-code slot loaded` for the new label (never the code or secret).
   - `GET /t/<code>` for the new code reaches login (303 then `/login`, or equivalent browser flow).
   - An unknown code shows the **same** generic sign-in error (no oracle); link-lost pages must not
     mint a session cookie.
   - File mode remains `0600` (entrypoint re-applies `chmod 600` on start).

6. Send the tester **only** the `/t/<code>` URL and their username (`user_identifier`). Never put
   the secret key in email, QA Sphere, or chat.

## Revoke a slot

1. Backup again (same step as add).
2. Edit `/app/config/application.properties` as `spring` (temp + `mv`): delete the four
   `ezkey.access-codes.<slot>.*` lines, or rotate `.code` to a new random value.
3. `docker restart ezkey-exp-demo-app-acme`.
4. Confirm the old `/t/<old-code>` returns the generic error.

## Rollback

If Acme fails to start (fail-fast on malformed or duplicate codes):

```bash
docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
  set -e
  umask 077
  mv /app/config/application.properties.bak /app/config/application.properties
  chmod 600 /app/config/application.properties
'
docker restart ezkey-exp-demo-app-acme
```

## Related docs

- Host overview: [`community-host.md`](community-host.md)
- Property table: [`ezkey-demo-app-acme/CONFIGURATION.md`](../../ezkey-demo-app-acme/CONFIGURATION.md)
  § `ezkey.access-codes`
- Tester checklist step 6: [`product-docs/global/tester-onboarding.md`](../../product-docs/global/tester-onboarding.md)
