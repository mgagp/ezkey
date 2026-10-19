# Host OS campaign — YYYY-MM-DD H{1|2|3}

## Metadata

- **Date:** YYYY-MM-DD
- **Tier:** H1 / H2 / H3
- **Instance:** `ezkey-online` (community)
- **Operator / agent:** _(rootbeer-local; name)_
- **Marc go:** not required / obtained YYYY-MM-DD _(reboot or H2/H3)_
- **Issue:** #909
- **Branch / PR:** _(fill when docs or follow-up code changed)_

## Signal (before)

| Field | Value |
|-------|-------|
| `/etc/system-release` | |
| Locked `releasever` (before) | |
| `dnf check-release-update` | none / `2023.x.YYYYMMDD` |
| Critical / Important (count + packages of interest) | |
| Kernel (`uname -r`) before | |
| `needs-restarting -r` before | exit 0 = reboot recommended / non-zero = not |
| `docker ps` summary | |

Command: `./scripts/lightsail/os-patch-check.sh --target community`

## Pre-intervention

| Field | Value |
|-------|-------|
| Snapshot name | `ezkey-online-pre-h{1\|2}-YYYYMMDD` |
| Snapshot state | available |
| Snapshot taken via | AWS CLI profile `ezkey-lightsail` (rootbeer) |
| Older snapshots deleted | list names / none |
| pg_dump path (H2/H3 only; on rootbeer, never in git) | N/A / `…` |

## Apply

- Commands run (no secrets):
- Target `releasever` after (H2) or “unchanged” (H1):
- Reboot performed: yes / no
- Reboot Marc go timestamp (if yes):

## Signal (after)

| Field | Value |
|-------|-------|
| Locked `releasever` (after) | |
| Kernel (`uname -r`) after | |
| `needs-restarting -r` after | |
| Critical / Important remaining | |
| `docker ps` summary | |

## Post-checks

- [ ] `os-patch-check.sh --target community` clean enough to close
- [ ] [`docs/OPERATIONAL.md`](../../../../../docs/OPERATIONAL.md) § Post-upgrade checks (m6)
- [ ] Public hostname smoke (admin-api / auth-api / integration-api / demo-acme)
- [ ] [`DEPLOYED.md`](../../../../../docs/lightsail/community/DEPLOYED.md) Host OS fields updated

## Outcome

- **Result:** success / rolled back / aborted
- **Rollback used:** none / snapshot→new instance + static IP / pg_dump restore
- **Notes:** _(short; link Christophe follow-ups if any)_
