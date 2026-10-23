# Edgar runbook — Acme tenant-alias mapping (community)

Short ops procedure for the community Lightsail host when Marc creates an evaluator tenant and needs
a `/t/{alias}` link on `demo-acme.ezkey.online`. Implements
[#958](https://github.com/mgagp/ezkey/issues/958).

**Do not deploy from this document alone** — apply only after the image that includes #958 is live
(see [`DEPLOYED.md`](DEPLOYED.md)). Never paste real integration secrets into chat, tickets, or git.

## Preconditions

- SSH Host alias for the community VM (e.g. `ezkey-online`). Public address:
  `<static-ip ezkey-online-community>` (do not hard-code digits in docs or tickets).
- Container `ezkey-exp-demo-app-acme` healthy; volume `demo-app-acme-config` mounted at `/app/config`.
- File `/app/config/tenant-aliases.properties` exists (entrypoint creates it empty), mode **0600**,
  owner **spring** (uid **100** / gid **101**). A root `chown` on the volume is drift — fix by
  rewriting as `spring` inside the container.

## Generate alias

Community alias = UUID v4 hex truncated to **16** lowercase characters (64 bits):

```bash
python3 -c "import uuid; print(uuid.uuid4().hex[:16])"
```

Marc supplies the Integration API **integration key**, **secret key**, and a short **label** (e.g.
tester first name). Do not echo the secret in shell history if you can avoid it (paste into the
temp file only).

## Append a line (atomic)

Format (one mapping per line):

```text
<alias>|<integration-key>|<secret-key>|<label>
```

As **spring** inside the Acme container (never edit `_data` as root on the host):

```bash
ssh ezkey-online
docker exec -u spring -it ezkey-exp-demo-app-acme sh -c '
  set -e
  umask 077
  FILE=/app/config/tenant-aliases.properties
  TMP=$(mktemp /app/config/.tenant-aliases.XXXXXX)
  cp "$FILE" "$TMP"
  # Paste the single new line below (alias|ikey|skey|label), then save:
  # printf "%s\n" "ALIAS|ezkey_ikey_…|ezkey_skey_…|Label" >> "$TMP"
  # Prefer an editor or a here-doc you typed carefully; do not `echo` secrets into the transcript.
  mv "$TMP" "$FILE"
  chmod 600 "$FILE"
'
```

Practical pattern: build the line in a local scratch note, then:

```bash
docker exec -u spring -i ezkey-exp-demo-app-acme sh -c '
  umask 077
  FILE=/app/config/tenant-aliases.properties
  TMP=$(mktemp /app/config/.tenant-aliases.XXXXXX)
  cp "$FILE" "$TMP"
  cat >> "$TMP"
  mv "$TMP" "$FILE"
  chmod 600 "$FILE"
' <<'LINE'
<alias>|<integration-key>|<secret-key>|<label>
LINE
```

If an alias appears twice, **last line wins**. The app ignores an incomplete trailing line (no final
newline) so a mid-write append without `mv` does not empty the map — still prefer temp + `mv`.

## Verify ownership / mode

```bash
docker exec ezkey-exp-demo-app-acme ls -l /app/config/tenant-aliases.properties
# Expect: -rw------- 1 spring spring … tenant-aliases.properties
```

## Test the URL

No restart. After the throttle window (default 5 s), open:

```text
https://demo-acme.ezkey.online/t/<alias>
```

Expect: 303 to login for a valid alias, or the **same** generic sign-in error page for unknown and
malformed aliases (no distinct oracle). Per-IP rate limit is active on these routes.
`Referrer-Policy: no-referrer` is set by the app and Caddy.

Do **not** put the alias in tickets or public chat if avoidable; treat it as a capability URL.

## Revoke

Remove the line with the same temp + `mv` pattern (or replace the file with a copy that omits that
alias). Next URL access after the throttle drops the mapping. Active browser sessions that already
bound the slot may keep credentials until logout/session end — tell Marc if immediate session kill
matters for that evaluator.

## Security reminders (Christophe / #958)

- The alias is a **capability**: anyone with the URL can drive the demo against that tenant.
- Never log the full alias, integration key, or secret (app WARN/INFO must not contain them).
- Secrets must not appear in Actuator, error pages, or deploy transcripts.
- Demo integration secrets on this volume are **plaintext** — no at-rest encryption claim (see
  [`docs/SECURITY_POSTURE.md`](../../SECURITY_POSTURE.md)).

## Related

- Vision lock: [`V-2026-10-07-multi-tenant-closed-testing-demo-app`](../../../product-docs/global/vision/V-2026-10-07-multi-tenant-closed-testing-demo-app.md) § 9a
- Module config: [`ezkey-demo-app-acme/CONFIGURATION.md`](../../../ezkey-demo-app-acme/CONFIGURATION.md)
- Host runbook: [`../community-host.md`](../community-host.md)
