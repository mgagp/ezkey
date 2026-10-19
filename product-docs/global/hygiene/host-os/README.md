# Host OS hygiene — community Lightsail (`ezkey-online`)

Punctual hygiene lane for the **community** Amazon Lightsail VM (`ezkey-online`,
Amazon Linux 2023). App deploys and Compose rolling upgrades stay in
[`docs/lightsail/community-host.md`](../../../../docs/lightsail/community-host.md) and
[`experimental-hybrid/`](../../../../experimental-hybrid/); this lane owns **host OS**
signal, patching, minor release upgrades, and eventual major / blue-green host moves.

**Not** a CI gate. **Not** a product vision / ADR / backlog program. Campaign notes under
[`campaigns/`](campaigns/) record what ran (releasever, kernel, reboot, snapshot, checks).

**Executable by a rootbeer-local agent, not cloud agents.** Local agents on Marc's Windows
workstation (rootbeer) have direct SSH via the private key in rootbeer's SSH config. Cloud
agents have no SSH route to the instance and must not invent one. Snapshot and `pg_dump`
steps also assume the `ezkey-lightsail` AWS CLI profile and local storage on rootbeer.

GitHub visibility: [#909](https://github.com/mgagp/ezkey/issues/909).

---

## Decisions (Marc 2026-10-19) — repo-is-memory

One line each (reason + issue):

- **IAM snapshot APIs on EzkeyLightsailOperator** — Marc added `lightsail:CreateInstanceSnapshot`,
  `GetInstanceSnapshot` / `GetInstanceSnapshots`, `DeleteInstanceSnapshot` (Resource `*`) so
  H1/H2 can take a pre-intervention snapshot from rootbeer; read verified 0 snapshots and
  Lightsail automatic snapshots not enabled (#909).
- **Snapshot before every H1/H2** — rootbeer agent creates the snapshot via AWS CLI profile
  `ezkey-lightsail` before applying OS changes so rollback has a known disk image (#909).
- **Snapshot retention** — keep the latest pre-intervention snapshot; delete older ones to
  bound Lightsail snapshot cost and operator clutter (#909).
- **Create permission not yet exercised** — first H1 run validates `CreateInstanceSnapshot`;
  scoping Resource to the instance ARN is a Christophe follow-up (#909).
- **pg_dump before H2 and H3 only** — logical DB backup on rootbeer complements the disk
  snapshot when reboot or host move can leave Postgres mid-flight; H1 security patches do not
  require it (#909).
- **AL2023 deterministic upgrades** — `dnf update` stays on the locked `releasever`; use
  `dnf check-release-update` then `dnf upgrade --releasever=2023.x.YYYYMMDD` (never `latest`)
  so the community host does not silently float (#909).
- **dnf-automatic not adopted** — conflicts with deterministic `releasever` upgrades and would
  apply patches without the snapshot / Marc-go gates; no swap for auto-apply (#909).
- **Execution boundary** — H1/H2 run from a rootbeer-local agent (SSH + AWS profile); cloud
  agents document and open PRs only (#909).

---

## Tiers

| Tier | What | Cadence | Human gate | Snapshot | pg_dump | Who executes |
|------|------|---------|------------|----------|---------|--------------|
| **H1** | Security patch within current `releasever` (`dnf update`) | Monthly Monday, **or** ≤7 days when Critical/Important hits kernel / openssh / openssl / glibc | Autonomous after snapshot; **Marc go only if reboot needed** | Yes (before apply) | No | rootbeer-local agent |
| **H2** | AL2023 minor release (`dnf upgrade --releasever=…`) + reboot | Quarterly Jan / Apr / Jul / Oct with major-upgrades walk | **Marc go** | Yes | Yes (on rootbeer) | rootbeer-local agent |
| **H3** | Major OS / new instance (blue/green) | Ad hoc; **plan by 2028** (AL2023 EOL 2029-06-30) | **Marc go**; Edgar executes; Christophe posture | Via new instance path | Yes (restore into new host) | Edgar (+ Christophe posture) |

AL2023 support notes: quarterly minors until **2027-06-30**; EOL **2029-06-30** (endoflife.date
product `amazon-linux`, cycle `2023`). Register entry:
[`major-upgrades/registry.yaml`](../major-upgrades/registry.yaml) id `amazon-linux-community`.

---

## Signals

Read-only probe (rootbeer only):

```bash
./scripts/lightsail/os-patch-check.sh --target community
```

Reports (no mutations):

- `/etc/system-release` and locked `releasever`
- `dnf check-release-update` (pending AL2023 minor)
- `dnf updateinfo list` for Critical / Important
- `needs-restarting -r` (reboot recommended?)
- `uname -r`
- `docker ps` (stack still up)

**H1 urgency:** if Critical/Important advisories touch **kernel**, **openssh**, **openssl**, or
**glibc**, treat as ≤7-day SLA rather than waiting for the monthly Monday slot.

Live ledger fields: [`docs/lightsail/community/DEPLOYED.md`](../../../../docs/lightsail/community/DEPLOYED.md)
§ *Host OS*.

---

## H1 — security patch (within releasever)

### Pre

1. Run `./scripts/lightsail/os-patch-check.sh --target community`; copy signal into the campaign note.
2. Create Lightsail instance snapshot (AWS CLI profile `ezkey-lightsail`, region `ca-central-1`),
   name e.g. `ezkey-online-pre-h1-YYYYMMDD`. Wait until snapshot state is available.
3. Confirm Docker stack healthy (`docker ps` via the check script).

### Apply (rootbeer agent)

```bash
ssh ezkey-online 'sudo dnf update -y'
# Do NOT pass --releasever=latest. Stay on the locked releasever.
```

### Reboot gate

- If `needs-restarting -r` was already true, or `dnf` reports a reboot-required kernel /
  glibc change: **stop** and get **Marc go** before `sudo reboot`.
- Otherwise finish without reboot; re-run the check script and post-checks.

### Post

1. `./scripts/lightsail/os-patch-check.sh --target community`
2. Post-upgrade checklist from [`docs/OPERATIONAL.md`](../../../../docs/OPERATIONAL.md)
   § *Post-upgrade checks (m6)* (log hygiene, Caddyfile identity, bootstrap-credentials mode —
   never `cat` secrets).
3. Public smoke: Admin API / Auth API / Integration API / demo-acme `/` expectations per
   [`community-host.md`](../../../../docs/lightsail/community-host.md) hostname table.
4. Update `DEPLOYED.md` Host OS fields; fill a campaign note from
   [`campaigns/TEMPLATE.md`](campaigns/TEMPLATE.md).
5. Snapshot rotation: keep this pre-H1 snapshot; delete older pre-intervention snapshots.

---

## H2 — AL2023 minor release (+ reboot)

### Pre

1. Signal script + confirm a new `releasever` from `dnf check-release-update`.
2. **Marc go** (quarterly walk or out-of-band).
3. Lightsail instance snapshot (`ezkey-online-pre-h2-YYYYMMDD`).
4. **pg_dump** of community Postgres to rootbeer (tunnel via `ezkey-online-db` / LocalForward
   `127.0.0.1:6432` — see community-host SSH aliases). Store under an operator-local path
   outside the repo; never commit dumps.

### Apply

```bash
# Example — pin the exact releasever string from check-release-update:
ssh ezkey-online 'sudo dnf upgrade -y --releasever=2023.x.YYYYMMDD'
# Marc go already obtained → reboot
ssh ezkey-online 'sudo reboot'
```

### Post

Same as H1 post-checks after the host returns (SSH, Docker `unless-stopped` services, m6,
public smoke). Update `DEPLOYED.md` + campaign note. Rotate snapshots (keep latest pre-H2).

---

## H3 — major / new instance (blue/green)

Ad hoc. **Plan by 2028** before AL2023 EOL (2029-06-30).

Outline (Edgar executes after Marc go; Christophe posture):

1. `pg_dump` on rootbeer (source of truth for DB move).
2. `./scripts/lightsail/create-instance.sh` (new name) → ports (Cloudflare 443 posture) →
   `./scripts/lightsail/bootstrap-host.sh --target …` (or explicit host).
3. Origin CA / `.env` / image export / restore dump — **not** `--clean-start` against the
   live community DB name without wipe confirm; commission the **new** instance only.
4. Move static IP `ezkey-online-community` to the new instance; update Cloudflare **A** records
   and SSH `HostName` immediately (see community-host § Deploy pitfalls).
5. Decommission old instance only after smoke + Marc confirmation. Never release the static IP.

---

## Rollback

| Asset | Use when | How |
|-------|----------|-----|
| **Lightsail instance snapshot** | H1/H2 OS change leaves the VM unusable | Create a **new** instance from the snapshot (Lightsail console / API), re-attach static IP `ezkey-online-community`, fix DNS/SSH HostName, verify firewall (Cloudflare 443). Snapshot restore is not an in-place undo on the same instance name. |
| **pg_dump (H2/H3)** | DB corruption or failed H3 cutover | Restore dump into Postgres on the replacement host; never commit dump files. |

Fail-closed on unknown host health: do not delete the latest pre-intervention snapshot until
the replacement path is verified.

---

## Explicit non-goals

- **dnf-automatic** / unattended-upgrades on the community host
- Cloud-agent SSH or AWS mutation of the live host
- Changing app Compose / Caddy / image export in this lane (separate deploy path)
- Enabling Lightsail **automatic** snapshots (manual pre-intervention snapshots only, for now)
- IAM Resource ARN scoping (Christophe follow-up tracked on #909)

---

## Related

- Runbook: [`docs/lightsail/community-host.md`](../../../../docs/lightsail/community-host.md)
- Live ledger: [`docs/lightsail/community/DEPLOYED.md`](../../../../docs/lightsail/community/DEPLOYED.md)
- Post-upgrade: [`docs/OPERATIONAL.md`](../../../../docs/OPERATIONAL.md) § Post-upgrade checks (m6)
- Bootstrap: [`scripts/lightsail/bootstrap-host.sh`](../../../../scripts/lightsail/bootstrap-host.sh)
- Signal script: [`scripts/lightsail/os-patch-check.sh`](../../../../scripts/lightsail/os-patch-check.sh)
- Cadence index: [`../README.md`](../README.md)
- Platform register: [`../major-upgrades/registry.yaml`](../major-upgrades/registry.yaml)
- Issue: [#909](https://github.com/mgagp/ezkey/issues/909)
