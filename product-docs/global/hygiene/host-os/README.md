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
- **AL2023 deterministic upgrades** — `dnf check-release-update`, then
  `dnf upgrade --releasever=2023.x.YYYYMMDD` (never `latest`) so the host does not float.
  Plain `dnf update` stays on the locked repo and is a no-op for fixes that shipped in a
  newer release (the 2026-11-02 H1 practice; #909).
- **dnf-automatic not adopted** — conflicts with deterministic `releasever` upgrades and would
  apply patches without the snapshot / Marc-go gates; no swap for auto-apply. The 2026-11-22
  detection change does not enable it (#909).
- **Locked-releasever blind spot (found 2026-11-22)** — `dnf updateinfo` on the installed
  releasever reported no advisories while newer AL2023 releases carried Important fixes,
  because each release repo is immutable; Marc chose option 2: weekly read-only detection
  against the latest dated release, and apply stays a separate H1/H2 window (Edgar,
  snapshot, reboot or docker restart, Marc go; no dnf-automatic) (#909).
- **Execution boundary** — the weekly read-only check and H1/H2 apply run from rootbeer
  (SSH + AWS profile). Fred owns the weekly check; Edgar owns the apply window. Cloud
  agents document and open PRs only (#909).

---

## Tiers

Detection is not a tier. Fred runs `os-patch-check.sh --target community` weekly (read-only,
rootbeer) and reports only when a newer dated release has Important or Critical advisories,
or when the probe is `RELEASE_UPDATE=unknown`. `yes` with both counts at 0 stays quiet.
`-` is unknown, not zero. Applying is a separate H1 or H2 window.

| Tier | What | Cadence | Human gate | Snapshot | pg_dump | Who executes |
|------|------|---------|------------|----------|---------|--------------|
| **H1** | Dated release that has Important or Critical advisories (`dnf upgrade --releasever=<dated release>`, the 2026-11-02 practice). Plain `dnf update` is a no-op on the locked repo. | When the weekly check reports | **Marc go** | Yes (before apply) | No | Edgar (rootbeer-local) |
| **H2** | Quarterly dated release (`dnf upgrade --releasever=<dated>`) + reboot, including weeks the check stayed quiet | Quarterly Jan / Apr / Jul / Oct with major-upgrades walk | **Marc go** | Yes | Yes (on rootbeer) | Edgar (rootbeer-local) |
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
- `dnf check-release-update`, parsed into installed vs latest dated release and how many
  releases behind (no newer release is a clean `RELEASE_UPDATE=no`, not an error)
- cumulative advisories **toward that latest release** (`dnf --releasever=<latest> updateinfo summary`
  and `updateinfo list` for Critical and Important, one `--sec-severity` per call). The locked
  repo is immutable, so updateinfo without `--releasever` stays empty while newer releases exist.
- `needs-restarting -r` (exit **1** = reboot required; exit **0** = no reboot)
- `uname -r`
- `docker ps` (stack still up)
- one decision line, exit status still 0 when the probe finishes:

```text
RELEASE_UPDATE=yes|no|unknown INSTALLED=<ver|-> LATEST=<ver|-> BEHIND=<n|-> CRITICAL=<n|-> IMPORTANT=<n|->
```

**Weekly report (Fred):** report when `RELEASE_UPDATE=yes` and `CRITICAL` or `IMPORTANT` is
greater than 0, and when the line is `unknown` or `yes` with `-` counts (failed probe — not
all-clear). Stay quiet on `no`, and on `yes` with both counts at 0. If the Important/Critical
list names **kernel**, **openssh**, **openssl**, or **glibc**, say so in the report so the H1
window can be pulled forward. The check does not apply anything.

Live ledger fields: [`docs/lightsail/community/DEPLOYED.md`](../../../../docs/lightsail/community/DEPLOYED.md)
§ *Host OS*.

---

## H1 — dated release with Important or Critical advisories

Apply is not the weekly check. Fred's check only reports. Edgar runs this window after Marc go.

### Pre

1. Weekly signal shows `RELEASE_UPDATE=yes` with `CRITICAL` or `IMPORTANT` greater than 0.
   Copy the check output into the campaign note.
2. **Marc go.**
3. Create Lightsail instance snapshot (AWS CLI profile `ezkey-lightsail`, region `ca-central-1`),
   name e.g. `ezkey-online-pre-h1-YYYYMMDD`. Wait until snapshot state is available.
4. Confirm Docker stack healthy (`docker ps` via the check script).

### Apply (Edgar, rootbeer)

```bash
# Pin the dated release from the check. Never `latest`. Never plain `dnf update`
# (that stays on the locked repo and installs nothing).
ssh ezkey-online 'sudo dnf upgrade -y --releasever=2023.x.YYYYMMDD'
```

### Reboot or docker restart

- If `needs-restarting -r` exits **1** (reboot required), or `dnf` reports a reboot-required
  kernel / glibc change: `sudo reboot` under the same Marc go. `unless-stopped` containers
  come back.
- If the host stays up, restart the Docker stack so processes pick up upgraded libraries.
- Do not leave a kernel or glibc upgrade running on the old userspace.

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

## H2 — quarterly dated release (+ reboot)

Same apply command as H1 (`dnf upgrade --releasever=<dated release>`). This window is the
quarterly walk, including when the weekly check stayed quiet (no Important/Critical).
Edgar executes after Marc go. pg_dump stays on H2/H3 only.

### Pre

1. Signal script + confirm the dated `releasever` from `dnf check-release-update` (never `latest`).
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
