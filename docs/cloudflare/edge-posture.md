# Cloudflare edge posture (free plan)

The single page for **what Cloudflare does for Ezkey, who owns each response header, which tokens
exist, and how to review it**. Agents and the operator use the same page and the same read-only
script ([`audit-readonly.sh`](../../scripts/cloudflare/audit-readonly.sh)). Last full review:
**2026-11-26** (two read-only passes from Rootbeer; umbrella issue #1553).

Scope: zones **ezkey.org** and **ezkey.online**, both on the **Free** plan, one Cloudflare account.
The origin IPv4 is never written here (see [`community-host.md`](../lightsail/community-host.md)
§ static IP).

## Header ownership

**Rule (Marc, 2026-11-26):** one owner per header and per hostname. The repo owns application
headers (Pages `_headers`, or Caddy for the APIs). The zone owns only HSTS, because the zone
setting replaces any origin or `_headers` value anyway. The managed transform **Add security
headers** stays off: it adds `X-Frame-Options: SAMEORIGIN`, `Referrer-Policy: same-origin` and two
obsolete headers (`X-XSS-Protection`, `Expect-CT`) on top of what the repo declares.

| Hostname | Served by | Application headers owner | HSTS owner | State on 2026-11-26 |
|---|---|---|---|---|
| `ezkey.org`, `www` (301) | Pages `ezkey-org` | [`sites/ezkey-org/_headers`](../../sites/ezkey-org/_headers) | zone ezkey.org | **Drift:** zone HSTS `max-age=0`; managed transform on (XFO `SAMEORIGIN` beside `DENY`, `same-origin`, X-XSS, Expect-CT). `ezkey-teaser.pages.dev` serves the repo values. |
| `methodology.ezkey.org` | Pages `methodology-ezkey-org` | none yet (no `_headers`) | zone ezkey.org | **Drift:** same zone HSTS and managed transform; no CSP. |
| `admin-ui.ezkey.online` | Pages `admin-ui-ezkey-online` | **today:** zone Transform Rule (CSP, XFO `DENY`, Permissions-Policy). **Target:** a `_headers` file in the build | zone ezkey.online | **Drift:** not versioned; the rule description says "Report-Only" but the CSP is enforced; `admin-ui-ezkey-online.pages.dev` gets none of these headers. |
| `auth-api`, `admin-api`, `integration-api` `.ezkey.online` | Caddy → Spring Boot on Lightsail | [`Caddyfile.ezkey-online`](../../experimental-hybrid/lightsail/Caddyfile.ezkey-online) (`header { defer … }` replaces Spring's own copies) | zone ezkey.online | **Drift until the next Caddyfile sync:** `X-Frame-Options: DENY` sent twice (Caddy added a copy to Spring's; fixed in the repo with `defer`). |
| `demo-login.ezkey.online` | Caddy → Demo Login | the Demo Login app | zone ezkey.online | OK (CSP, XFO once, `no-referrer`). |
| `exp1-*.ezkey.org` | exp1 leftovers (Pages `ezkey-admin-ui`, four proxied A records) | — | zone ezkey.org | **Cleanup** (lot D). |

Zone HSTS today: **ezkey.org** enabled with max-age 0 (target: 31536000, no `includeSubDomains`, no
preload; recorded in [`ezkey-org-site.md`](ezkey-org-site.md), #1453). **ezkey.online** max-age
7776000 with `includeSubDomains` **and preload**: decision pending (lot A).

## Free plan inventory

Status: **Used**, **Not yet** (worth doing, open lot), **No** (decided against, with reason),
**Decide** (needs Marc). Free-plan quotas are as of 2026-11; check the dashboard before relying on one.

| Capability | ezkey.org | ezkey.online | Note |
|---|---|---|---|
| Proxied DNS, Universal SSL, SSL mode Full (strict) | Used | Used | Origin CA on Caddy for ezkey.online. |
| Always Use HTTPS, HTTP/3, ECH; 0-RTT off | Used | Used | 0-RTT stays off (replay risk on auth POSTs). |
| Minimum TLS at the edge | 1.3 | 1.2 | **Decide** (lot A): 1.3 on ezkey.online matches Caddy (1.3 only). |
| HSTS (zone) | Drift (max-age 0) | Used (preload) | Lot A. |
| DNSSEC | Not yet | Used | Lot D (needs the DS record at the registrar). |
| DDoS L7, Managed Free Ruleset, normalization | Used | Used | Always on. |
| Custom WAF rules (5 on Free) | 1 (country block KP, RU, SA) | 0 | Lot B: rules for the API hosts on ezkey.online. |
| Rate limiting rule (1 on Free, per IP, 10 s period) | 0 | 0 | Lot B: the one rule goes to the auth endpoints; complements application limits ([`OPERATIONAL.md`](../OPERATIONAL.md)). |
| Bot Fight Mode | Used | **No** | Cannot be scoped on Free; it would challenge the mobile app and integrations on the API hosts. |
| API Shield schema validation | exp1 leftovers (mitigation none) | Used (2 schemas, **block**) | [`auth-api-schema-validation.md`](auth-api-schema-validation.md), [`integration-api-schema-validation.md`](integration-api-schema-validation.md). |
| Transform Rules (response headers) | 0 | 1 (Admin UI) | Lot A moves the Admin UI headers into the repo. |
| Managed transforms | Add security headers **on** (drift), remove X-Powered-By on | all off | Lot A turns Add security headers off. |
| Redirect rules | `www` → apex | `www.ezkey.online` → `https://ezkey.org` | Used. |
| Origin lockdown: Lightsail firewall limited to Cloudflare IPv4 | — | Used | Verified 2026-11-26: the origin does not answer a direct connection. |
| Authenticated Origin Pulls (zone-level, free) | Off | Off | Lot C: second layer behind the firewall. |
| Page Shield (script monitor) | Off | Off | Not yet; low value while the Admin UI loads same-origin scripts only. |
| Pages (Direct Upload with Wrangler) | Used (2 projects + exp1) | Used (Admin UI) | No Git integration, by choice: deploys stay explicit. |
| Email Routing | Used (`security@`) | Off | — |
| Cloudflare Tunnel | 1 CNAME (`mac-auth`) | — | Lab use, outside the community stack. |
| Workers | — | — | None. |

## Tokens

Least privilege, one token per job. Values live only in the gitignored repo-root `.env` (and, for
cloud agents, in the cloud environment secrets). Never paste a token in a chat, an issue or a log.

| Variable | Kind | Can | Used by | Expires |
|---|---|---|---|---|
| `CLOUDFLARE_API_TOKEN` | user | Pages edit (account) | `deploy-*.sh`, `cleanup-*.sh` (Wrangler) | 2027-06-01 |
| `CLOUDFLARE_API_SHIELD_TOKEN` | user | API Gateway edit, zone read | `upload-*-schema*.sh` | 2027-06-01 |
| `CLOUDFLARE_AUDIT_TOKEN` | user | **read only**: both zones (settings, DNS, rules, managed headers, SSL, AOP, API Gateway, Page Shield) and account Pages / rulesets; cannot list tokens | `audit-readonly.sh` | 2027-02-25 |
| `RULES_READ_TOKEN` | account | ezkey.online settings and rules, read | nothing in the repo | 2027-11-23 — superseded by the audit token; retire (lot D) |

The token used through Grokbot is not inventoried here yet (lot D). No token can edit DNS: DNS changes
stay manual in the dashboard ([`community-host.md`](../lightsail/community-host.md)).

## Review

Run from the repo root (Git Bash, macOS or Linux; needs `curl` and `jq`):

```bash
./scripts/cloudflare/audit-readonly.sh                 # live headers + API (CLOUDFLARE_AUDIT_TOKEN)
./scripts/cloudflare/audit-readonly.sh --headers-only  # live headers only, no token
```

Each line is **PASS**, **DRIFT** (differs from a target decided on this page), **INFO** (observed,
no target: a pending decision or inventory) or **SKIP** (not readable). Raw responses go to
`~/.ezkey-tmp/cloudflare-audit-<timestamp>/`, outside the repo. Exit code 1 when there is a DRIFT.
It is a review tool, not a CI gate. Claude Code cloud containers cannot reach `api.cloudflare.com`
or the Ezkey hostnames unless the environment allows them; run it on Rootbeer or the Mac otherwise.

When to run it: before and after any dashboard change, at each community upgrade (with the Cloudflare
IP range recheck in the Caddyfile), and when a header looks wrong.

After a dashboard change: one change at a time with the operator's go, rerun the script, then update
the table above and the "Last full review" line in the same PR as any repo change. A new decided
target goes into the script (`expect_header` or the zone checks) in the same PR.

## Tooling: Wrangler and `cf`

- **Today:** Wrangler (`npx wrangler`, 4.x) only for `pages deploy` / deployment cleanup in the four
  `deploy-*` and the `cleanup-*` scripts. Everything else (API Shield, this audit) calls the REST API
  with `curl`, so it does not depend on a CLI.
- **`cf`**, Cloudflare's new CLI generated from the API schema: open beta since 2026-09-28. It does
  not cover Pages direct upload. Wrangler stays maintained for 18 months after the beta ends.
- **Decision (2026-11-26):** no migration now. Re-evaluate at `cf` general availability: the impact
  is limited to the Wrangler calls in `scripts/cloudflare/deploy-*.sh` and `cleanup-*`, and to the
  Pages → Workers static assets question that `cf` raises.

## Open work

Umbrella issue [#1553](https://github.com/mgagp/ezkey/issues/1553): lots A to E. A: headers and HSTS on both zones, Admin UI `_headers`,
minimum TLS. B: custom WAF rules and the rate limiting rule on ezkey.online. C: Authenticated Origin
Pulls. D: exp1 cleanup, DNSSEC on ezkey.org, token inventory and retirement. E: `cf` at GA.
