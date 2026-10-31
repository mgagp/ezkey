# Community Acme — access-code slot runbook (restart-based)

Operator procedure for adding or revoking an evaluator `/t/{code}` slot on the community Acme demo.
Config is already external: named volume `demo-app-acme-config` → `/app/config/application.properties`
via `SPRING_CONFIG_ADDITIONAL_LOCATION`. Slots bind as `ezkey.access-codes.<slot>.*` and are read
**at JVM start** — a container restart is required (and acceptable).

Hot-reload of a second mapping file is **deliberately not** implemented. Reopen that path only if
frequent slot additions make restarts painful (Related to #958; premise that mappings were “baked
in the image” was incorrect).

Do **not** put real secrets, IPs, or hostnames in tickets or chat. Feed secrets into the container
**only via stdin** (or a host temp file mode `0600` deleted afterward) — never on the `docker exec`
command line, never in an unquoted shell heredoc that expands into `sh -c '…'`.

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
   CODE="$(openssl rand -hex 16)"
   ```

2. **Read secrets into shell variables** (no echo; avoid pasting into chat history):

   ```bash
   SLOT='tester1'          # short id, no spaces
   LABEL='Tester Firstname'
   printf 'integration key: '
   read -rs IKEY
   printf '\nsecret key: '
   read -rs SKEY
   printf '\n'
   ```

3. **Backup** the live config as `spring` (uid 100), mode 0600.  
   `application.properties.bak` **contains secrets** until you delete it (step 7).

   ```bash
   docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
     set -e
     umask 077
     cp /app/config/application.properties /app/config/application.properties.bak
     chmod 600 /app/config/application.properties.bak
   '
   ```

4. **Append four lines** with an atomic write (temp + `mv`) as `spring`. Slot lines are fed on
   **host stdin** into `cat >> "$TMP"` so they never appear in `docker exec` argv / container
   `/proc/*/cmdline` / shell history of the remote `sh -c`:

   ```bash
   {
     printf '\n# access-code slot (evaluator) — restart required\n'
     printf 'ezkey.access-codes.%s.code=%s\n' "$SLOT" "$CODE"
     printf 'ezkey.access-codes.%s.integration-key=%s\n' "$SLOT" "$IKEY"
     printf 'ezkey.access-codes.%s.secret-key=%s\n' "$SLOT" "$SKEY"
     printf 'ezkey.access-codes.%s.label=%s\n' "$SLOT" "$LABEL"
   } | docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
     set -e
     umask 077
     FILE=/app/config/application.properties
     TMP=$(mktemp /app/config/.application.properties.XXXXXX)
     cat "$FILE" > "$TMP"
     cat >> "$TMP"
     mv "$TMP" "$FILE"
     chmod 600 "$FILE"
   '
   unset IKEY SKEY
   ```

   Alternative: write the four lines to a host file with `umask 077` / mode `0600`,
   `docker exec -i … sh -c '…; cat >> "$TMP"; …' < thatfile`, then `shred -u` / `rm` the host file.

5. **Restart only Acme** (not the whole stack):

   ```bash
   docker restart ezkey-exp-demo-app-acme
   ```

6. **Verify**

   - Logs show a line like `Access-code slot loaded` for the new label (never the code or secret).
   - `GET /t/<code>` for the new code reaches login (303 then `/login`, or equivalent browser flow).
   - An unknown code shows the **same** generic sign-in error (no oracle); link-lost pages must not
     mint a session cookie.
   - File mode remains `0600` (entrypoint fail-closes unless mode is 0600).

7. **Remove the backup** once verification succeeds (`.bak` still holds secrets until removed):

   ```bash
   docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
     set -e
     rm -f /app/config/application.properties.bak
   '
   ```

8. Send the tester **only** the `/t/<code>` URL and their username (`user_identifier`). Never put
   the secret key in email, QA Sphere, or chat.

## Revoke a slot

1. Backup again (same as add step 3). Remember `.bak` holds secrets until removed.
2. Edit `/app/config/application.properties` as `spring` (temp + `mv`): delete the four
   `ezkey.access-codes.<slot>.*` lines, or rotate `.code` to a new random value. Prefer stdin-fed
   edits over embedding secrets in `sh -c` argv.
3. `docker restart ezkey-exp-demo-app-acme`.
4. Confirm the old `/t/<old-code>` returns the generic error.
5. `rm -f` the `.bak` after success (same as add step 7).

## Rollback

If Acme fails to start (fail-fast on malformed or duplicate codes), restore from `.bak` **before**
deleting it:

```bash
docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
  set -e
  umask 077
  mv /app/config/application.properties.bak /app/config/application.properties
  chmod 600 /app/config/application.properties
'
docker restart ezkey-exp-demo-app-acme
```

After a successful rollback verification, create a fresh `.bak` only when you next edit — do not
leave stale secret-bearing backups around.

## Related docs

- Host overview: [`community-host.md`](community-host.md)
- Property table: [`ezkey-demo-app-acme/CONFIGURATION.md`](../../ezkey-demo-app-acme/CONFIGURATION.md)
  § `ezkey.access-codes`
- Tester checklist step 6: [`product-docs/global/tester-onboarding.md`](../../product-docs/global/tester-onboarding.md)
