#!/usr/bin/env bash
#
# Export Ezkey backend images (docker save), copy to Lightsail (scp), docker load on the VM.
#
# SSH host: no silent default (#812). Pass --target community or set LIGHTSAIL_SSH_HOST.
#   --target community  →  ezkey-online  (sync uses compose bind basename, e.g. Caddyfile.ezkey-online)
#
# Prerequisites: built images (skipped in --dry-run); ssh/scp to the resolved host.
#
# With --clean-start, syncs lightsail operator files to the VM then runs clean-start.sh (destructive).
# With --sync-operator-files, copies docker-compose.yml, docker-compose.javamelody.yml (when present),
# the target-aware Caddyfile (remote name = compose ./…:/etc/caddy/Caddyfile bind — #863), and
# clean-start.sh to the VM only (no clean-start) so Caddy and Compose stay aligned with the repo;
# preserves volumes/DB. Also syncs docker/javamelody/ for the collector.
# With --remote-up, after load (and after --sync-operator-files if set), validates bind-mount sources
# on the VM then runs docker compose up -d so migration + backends + Caddy pick up the loaded images
# (no volume wipe). Do not combine --clean-start with --sync-operator-files or --remote-up.
# Override remote path with LIGHTSAIL_REMOTE_DIR if needed.
#
# Usage (from repository root):
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --dry-run
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --apis-only
#   ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community --sync-operator-files --remote-up
#   LIGHTSAIL_SSH_HOST=ezkey-online ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --dry-run
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=../../scripts/lightsail/common.sh
source "${REPO_ROOT}/scripts/lightsail/common.sh"

COMPOSE_FILE="${REPO_ROOT}/docker/docker-compose.yml"
EXPORT_DIR="${EXPORT_DIR:-${REPO_ROOT}/docker/export}"

LIGHTSAIL_REMOTE_DIR="${LIGHTSAIL_REMOTE_DIR:-~/ezkey/experimental-hybrid/lightsail}"
LIGHTSAIL_LOCAL_DIR="${REPO_ROOT}/experimental-hybrid/lightsail"
# Remote repository root that hosts the files the Lightsail compose bind-mounts with ../../ paths
# (docker/postgres/init, scripts/db, docker/generate-encryption-keys.sh). Derived from the remote
# lightsail dir so a custom LIGHTSAIL_REMOTE_DIR still resolves correctly.
REMOTE_REPO_ROOT="${LIGHTSAIL_REMOTE_DIR}/../.."

INCLUDE_MIGRATION="1"
INCLUDE_DEMO_ACME=""
DO_CLEAN_START=""
REMOVE_REMOTE_TARS=""
DRY_RUN=""
SYNC_OPERATOR_FILES=""
REMOTE_UP=""
DECLARED_TARGET=""

usage() {
  cat <<EOF
Export Ezkey backend images → Lightsail: docker save, scp, remote docker load.

Usage (from repository root):
  ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh --target community [options]
  LIGHTSAIL_SSH_HOST=ezkey-online ./experimental-hybrid/scripts/export-backend-images-to-lightsail.sh [options]

SSH target (required — no silent default; #812):
  --target community   SSH Host ${LIGHTSAIL_SSH_HOST_COMMUNITY}; sync Caddyfile basename from compose
  or set LIGHTSAIL_SSH_HOST explicitly (known aliases infer target for Caddyfile sync)

Options:
  --apis-only            Export only admin-api, auth-api, integration-api (no migration image).
  --include-demo-acme    Also export ezkey-demo-app-acme (after API tars; build image first).
  --sync-operator-files  After load, scp docker-compose.yml, docker-compose.javamelody.yml (if present),
                         target-aware Caddyfile (remote name = compose bind basename, #863),
                         clean-start.sh + host mounts including scripts/ops/ and docker/javamelody/.
                         No volume wipe. --dry-run reports sync/compose Caddyfile alignment.
  --remote-up            After load/sync, on the VM: bind-mount preflight then docker compose up -d
                         (rolling apply; DB preserved). Fails before up if a bind source is missing
                         or the wrong type (file vs directory) — #863.
                         With --sync-operator-files: sha256-verify the mounted Caddyfile + force-recreate
                         caddy (bind mount; plain up -d does not reload Caddy).
                         JavaMelody: EZKEY_ENABLE_JAVA_MELODY=true keeps/starts the collector overlay.
                         EZKEY_DISABLE_JAVA_MELODY=true intentionally removes it (--remove-orphans).
                         Default rolling up does NOT pass --remove-orphans (intentional — Edgar).
                         If the collector is already running and neither flag is set, the script refuses
                         so APIs are not recreated without the JM overlay while the collector stays
                         (see community-host.md).
  --clean-start          After load, sync and run clean-start.sh (DESTRUCTIVE). Community requires
                         EZKEY_CONFIRM_WIPE=ezkey-online (first commissioning only). Not with
                         --sync-operator-files or --remote-up.
  --remove-remote-tars   After successful load, remove tar files on the remote home directory.
  --dry-run              Print commands only (skips local docker image checks).
  -h, --help             Show this help.

Build demo image when using --include-demo-acme (from repository root):
  docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest .
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || lightsail_die "--target requires community"
      DECLARED_TARGET="$2"
      shift 2
      continue
      ;;
    --apis-only) INCLUDE_MIGRATION="" ;;
    --include-demo-acme) INCLUDE_DEMO_ACME="1" ;;
    --sync-operator-files) SYNC_OPERATOR_FILES="1" ;;
    --remote-up) REMOTE_UP="1" ;;
    --clean-start) DO_CLEAN_START="1" ;;
    --remove-remote-tars) REMOVE_REMOTE_TARS="1" ;;
    --dry-run) DRY_RUN="1" ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
  shift
done

lightsail_resolve_ssh_target "${DECLARED_TARGET}"
SSH_HOST="${LIGHTSAIL_SSH_HOST}"

if [[ -n "$DO_CLEAN_START" ]] && { [[ -n "$SYNC_OPERATOR_FILES" ]] || [[ -n "$REMOTE_UP" ]]; }; then
  echo "error: --clean-start cannot be combined with --sync-operator-files or --remote-up." >&2
  echo "  For a no-wipe upgrade (Caddy, Compose, images, migration), use --sync-operator-files and/or --remote-up only." >&2
  echo "  For a disposable full reset, use --clean-start alone." >&2
  exit 1
fi

# Community --clean-start wipes Postgres; require explicit confirmation (first commissioning only).
if [[ -n "$DO_CLEAN_START" ]]; then
  lightsail_require_community_wipe_confirm
fi

IMAGES=()
TARS=()
if [[ -n "$INCLUDE_MIGRATION" ]]; then
  IMAGES+=(ezkey-migration:latest)
  TARS+=(ezkey-migration.tar)
fi
IMAGES+=(ezkey-admin-api:latest ezkey-auth-api:latest ezkey-integration-api:latest)
TARS+=(ezkey-admin-api.tar ezkey-auth-api.tar ezkey-integration-api.tar)
if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
  IMAGES+=(ezkey-demo-app-acme:latest)
  TARS+=(ezkey-demo-app-acme.tar)
fi

run() {
  if [[ -n "$DRY_RUN" ]]; then
    printf '[dry-run]'
    printf ' %q' "$@"
    echo
    return 0
  fi
  "$@"
}

# Sync the host-side files that the Lightsail docker-compose.yml bind-mounts through ../../ paths.
# These are NOT the three operator files (docker-compose.yml, Caddyfile, clean-start.sh); they are
# the PostgreSQL role-init script, the DML grants applied by the db-grants service, the master
# key generator used by clean-start, and host ops scripts (log hygiene). Without the DB pieces, a
# fresh postgres init has no application roles and Flyway fails with "password authentication
# failed for user ezkey_migrate". See the postgresql-application-role-split work (TB-2026-07-16).
sync_host_mounts() {
  echo ""
  echo "→ sync host-mounted files (postgres init, db grants, keygen, scripts/ops, javamelody) → ${SSH_HOST}:${REMOTE_REPO_ROOT}"
  run ssh "${SSH_HOST}" "mkdir -p ${REMOTE_REPO_ROOT}/docker/postgres/init ${REMOTE_REPO_ROOT}/scripts/db ${REMOTE_REPO_ROOT}/scripts/ops ${REMOTE_REPO_ROOT}/docker/javamelody"
  run scp "${REPO_ROOT}/docker/postgres/init/"*.sh "${SSH_HOST}:${REMOTE_REPO_ROOT}/docker/postgres/init/"
  run scp "${REPO_ROOT}/docker/generate-encryption-keys.sh" "${SSH_HOST}:${REMOTE_REPO_ROOT}/docker/"
  run scp "${REPO_ROOT}/scripts/db/"* "${SSH_HOST}:${REMOTE_REPO_ROOT}/scripts/db/"
  run scp "${REPO_ROOT}/scripts/ops/"* "${SSH_HOST}:${REMOTE_REPO_ROOT}/scripts/ops/"
  # Collector image build context + applications.properties for the Lightsail overlay (#846).
  run scp "${REPO_ROOT}/docker/javamelody/"* "${SSH_HOST}:${REMOTE_REPO_ROOT}/docker/javamelody/"
}

# Sync docker-compose.yml, optional javamelody overlay, target-aware Caddyfile (remote basename =
# compose ./…:/etc/caddy/Caddyfile bind — #863), and clean-start.sh. Community (#812).
sync_operator_files() {
  local label="$1"
  local caddy_src remote_caddy_basename compose_local
  compose_local="${LIGHTSAIL_LOCAL_DIR}/docker-compose.yml"
  caddy_src="$(lightsail_caddyfile_source_for_sync "${LIGHTSAIL_LOCAL_DIR}" "${compose_local}")"
  remote_caddy_basename="$(basename "${caddy_src}")"
  echo ""
  echo "→ ${label} → ${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}"
  echo "  target=${LIGHTSAIL_TARGET:-custom} Caddyfile source=${remote_caddy_basename} → remote ${remote_caddy_basename}"
  lightsail_assert_caddyfile_sync_matches_compose "${compose_local}" "${remote_caddy_basename}"
  run ssh "${SSH_HOST}" "mkdir -p ${LIGHTSAIL_REMOTE_DIR}"
  for f in docker-compose.yml clean-start.sh docker-compose.javamelody.yml; do
    src="${LIGHTSAIL_LOCAL_DIR}/${f}"
    if [[ "$f" == "docker-compose.javamelody.yml" && ! -f "$src" ]]; then
      continue
    fi
    if [[ ! -f "$src" ]]; then
      echo "error: missing ${src}" >&2
      exit 1
    fi
    run scp "$src" "${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}/${f}"
  done
  if [[ ! -f "${caddy_src}" ]]; then
    echo "error: missing ${caddy_src}" >&2
    exit 1
  fi
  # Remote name must match the compose bind mount host path (#863). Do not rename to Caddyfile.
  run scp "${caddy_src}" "${SSH_HOST}:${LIGHTSAIL_REMOTE_DIR}/${remote_caddy_basename}"
  # Transition: after #859 both Caddyfile and Caddyfile.ezkey-online may exist on the VM.
  # Remove a Docker-created empty directory named Caddyfile (the #863 failure mode); leave a
  # legacy regular-file Caddyfile in place (harmless; compose no longer mounts it).
  run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR}; if [[ -d Caddyfile ]]; then echo 'removing stale Caddyfile directory (Docker bind-mount artifact from name mismatch)'; rmdir Caddyfile; elif [[ -f Caddyfile && '${remote_caddy_basename}' != 'Caddyfile' ]]; then echo 'note: legacy remote Caddyfile file left in place (compose mounts ${remote_caddy_basename})'; fi"
  sync_host_mounts
}

# Resolve a compose bind source path relative to the remote lightsail dir / repo root.
# Prints an absolute-looking remote path suitable for test -e / test -f / test -d on the VM.
lightsail_remote_path_for_bind_source() {
  local src="$1"
  case "${src}" in
    ./*)
      echo "${LIGHTSAIL_REMOTE_DIR}/${src#./}"
      ;;
    ../*)
      # Compose lives under experimental-hybrid/lightsail; ../.. is repo root on the VM layout.
      echo "${REMOTE_REPO_ROOT}/${src#../../}"
      ;;
    /*)
      echo "${src}"
      ;;
    *)
      echo "${LIGHTSAIL_REMOTE_DIR}/${src}"
      ;;
  esac
}

# Before remote compose up: every bind-mount source must exist and be the expected type (#863).
# Uses the local compose file (already synced, or about to be used) — same contract as
# `docker compose config` bind sources without requiring docker on the workstation for dry-run.
# On a real run, checks run on the VM via ssh. Dry-run prints planned checks and still fails
# locally if the Caddyfile sync name would not match the compose bind.
verify_remote_bind_mounts() {
  local compose_local="${LIGHTSAIL_LOCAL_DIR}/docker-compose.yml"
  local kind src remote_path checks="" line
  echo ""
  echo "→ bind-mount preflight on ${SSH_HOST} (fail before compose up if missing/wrong type)"
  # Re-check Caddyfile name alignment even when --sync-operator-files was omitted.
  local caddy_bind
  caddy_bind="$(lightsail_caddyfile_remote_basename_from_compose "${compose_local}")"
  lightsail_assert_caddyfile_sync_matches_compose "${compose_local}" "${caddy_bind}"
  while IFS=$'\t' read -r kind src; do
    [[ -n "${src}" ]] || continue
    remote_path="$(lightsail_remote_path_for_bind_source "${src}")"
    echo "  expect ${kind}: ${src} → ${remote_path}"
    if [[ "${kind}" == "file" ]]; then
      checks+="if [[ ! -e ${remote_path} ]]; then echo \"error: bind source missing: ${remote_path} (compose ${src})\" >&2; exit 1; fi; "
      checks+="if [[ -d ${remote_path} ]]; then echo \"error: bind source is a directory but compose expects a file: ${remote_path} (compose ${src}) — often Docker created an empty dir when the path was missing (#863)\" >&2; exit 1; fi; "
      checks+="if [[ ! -f ${remote_path} ]]; then echo \"error: bind source is not a regular file: ${remote_path} (compose ${src})\" >&2; exit 1; fi; "
    else
      checks+="if [[ ! -e ${remote_path} ]]; then echo \"error: bind source missing: ${remote_path} (compose ${src})\" >&2; exit 1; fi; "
      checks+="if [[ ! -d ${remote_path} ]]; then echo \"error: bind source is not a directory: ${remote_path} (compose ${src})\" >&2; exit 1; fi; "
    fi
  done < <(lightsail_list_compose_bind_mounts "${compose_local}")
  if [[ -z "${checks}" ]]; then
    echo "error: no bind mounts parsed from ${compose_local}" >&2
    exit 1
  fi
  run ssh "${SSH_HOST}" "set -euo pipefail; ${checks} echo 'bind-mount preflight OK'"
}

echo "=========================================="
echo "  Export backend images → Lightsail"
echo "  SSH: ${SSH_HOST}"
echo "  target: ${LIGHTSAIL_TARGET:-custom (explicit host)}"
if [[ -n "$INCLUDE_DEMO_ACME" ]]; then
  echo "  (including demo-app-acme)"
fi
echo "=========================================="
echo ""

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "error: compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

mkdir -p "$EXPORT_DIR"

for i in "${!IMAGES[@]}"; do
  img="${IMAGES[$i]}"
  tar_name="${TARS[$i]}"
  out="${EXPORT_DIR}/${tar_name}"
  echo "→ docker save ${img} → ${out}"
  if [[ -z "$DRY_RUN" ]]; then
    if ! docker image inspect "$img" >/dev/null 2>&1; then
      echo "error: image not found locally: ${img}" >&2
      if [[ "$img" == "ezkey-demo-app-acme:latest" ]]; then
        echo "  Build first, e.g. from repo root:" >&2
        echo "    docker build -f docker/Dockerfile --target demo-app-acme -t ezkey-demo-app-acme:latest ." >&2
      else
        echo "  Build first, e.g. from repo root:" >&2
        echo "    docker compose -f docker/docker-compose.yml build migration admin-api auth-api integration-api" >&2
      fi
      exit 1
    fi
  fi
  run docker save -o "$out" "$img"
done

echo ""
echo "→ scp → ${SSH_HOST}:~/"
for tar_name in "${TARS[@]}"; do
  run scp "${EXPORT_DIR}/${tar_name}" "${SSH_HOST}:~/"
done

echo ""
echo "→ docker load on ${SSH_HOST}"
for tar_name in "${TARS[@]}"; do
  run ssh "${SSH_HOST}" "docker load -i ~/${tar_name}"
done

if [[ -n "$REMOVE_REMOTE_TARS" ]]; then
  echo ""
  echo "→ remove remote tars in ~/"
  remote_paths=""
  for tar_name in "${TARS[@]}"; do
    remote_paths+=" ~/${tar_name}"
  done
  run ssh "${SSH_HOST}" "rm -f${remote_paths}"
fi

CADDY_SRC_FOR_VERIFY=""
if [[ -n "$SYNC_OPERATOR_FILES" && -z "$DO_CLEAN_START" ]]; then
  sync_operator_files "sync operator files (no clean-start)"
  CADDY_SRC_FOR_VERIFY="$(lightsail_caddyfile_source_for_sync "${LIGHTSAIL_LOCAL_DIR}")"
fi

if [[ -n "$REMOTE_UP" && -z "$DO_CLEAN_START" ]]; then
  echo ""
  echo "→ remote rolling stack (docker compose up -d) on ${SSH_HOST}"
  # Recreate containers so :latest and updated Compose take effect; Postgres keeps postgres-data.
  # Caddyfile is a bind mount — up -d alone does not reload caddy when only the file changed.
  #
  # Bind-mount preflight (#863): fail before any restart if a host path is missing or the wrong
  # type (Docker would otherwise create an empty directory for a missing file bind).
  verify_remote_bind_mounts
  #
  # JavaMelody (#846 / Edgar): default rolling up does NOT pass --remove-orphans.
  # Guard so a plain base-only up cannot leave APIs without JM env while the collector
  # container still runs (or accidentally tear it down if --remove-orphans is restored):
  #   EZKEY_ENABLE_JAVA_MELODY=true  → apply overlay (keep / start collector)
  #   EZKEY_DISABLE_JAVA_MELODY=true → intentional remove (base + --remove-orphans)
  #   neither, but collector present → refuse (must choose enable or disable)
  jm_enable=""
  jm_disable=""
  if [[ "${EZKEY_ENABLE_JAVA_MELODY:-}" == "1" || "${EZKEY_ENABLE_JAVA_MELODY:-}" == "true" ]]; then
    jm_enable="1"
  fi
  if [[ "${EZKEY_DISABLE_JAVA_MELODY:-}" == "1" || "${EZKEY_DISABLE_JAVA_MELODY:-}" == "true" ]]; then
    jm_disable="1"
  fi
  if [[ -n "$jm_enable" && -n "$jm_disable" ]]; then
    echo "error: set only one of EZKEY_ENABLE_JAVA_MELODY or EZKEY_DISABLE_JAVA_MELODY" >&2
    exit 1
  fi
  remote_compose_cmd='docker compose -f docker-compose.yml'
  remove_orphans=""
  if [[ -n "$jm_enable" ]]; then
    remote_compose_cmd='docker compose -f docker-compose.yml -f docker-compose.javamelody.yml'
    echo "  (EZKEY_ENABLE_JAVA_MELODY set — applying docker-compose.javamelody.yml)"
  elif [[ -n "$jm_disable" ]]; then
    remove_orphans="--remove-orphans"
    echo "  (EZKEY_DISABLE_JAVA_MELODY set — base compose + --remove-orphans; collector will be removed)"
  else
    # Detect a live collector so a plain rolling up cannot orphan-remove it silently.
    collector_present=""
    if [[ -z "$DRY_RUN" ]]; then
      if ssh "${SSH_HOST}" "docker inspect ezkey-exp-javamelody-collector >/dev/null 2>&1"; then
        collector_present="1"
      fi
    fi
    if [[ -n "$collector_present" ]]; then
      echo "error: JavaMelody collector is running on ${SSH_HOST}." >&2
      echo "  A rolling up without the overlay would recreate APIs without JAVAMELODY_*" >&2
      echo "  while leaving the collector container (default up does not pass" >&2
      echo "  --remove-orphans). Re-run with EZKEY_ENABLE_JAVA_MELODY=true to keep it," >&2
      echo "  or EZKEY_DISABLE_JAVA_MELODY=true to remove it intentionally" >&2
      echo "  (that path alone passes --remove-orphans)." >&2
      exit 1
    fi
  fi
  run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR} && ${remote_compose_cmd} up -d ${remove_orphans}"
  if [[ -n "$CADDY_SRC_FOR_VERIFY" ]]; then
    local_sha="$(sha256sum "${CADDY_SRC_FOR_VERIFY}" | awk '{print $1}')"
    remote_caddy_basename="$(basename "${CADDY_SRC_FOR_VERIFY}")"
    echo ""
    echo "→ verify remote ${remote_caddy_basename} sha256=${local_sha} and force-recreate caddy (bind-mount)"
    run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR}; if [[ -d ${remote_caddy_basename} ]]; then echo \"error: ${remote_caddy_basename} is a directory (Docker empty-dir artifact); expected a file (#863)\" >&2; exit 1; fi; remote_sha=\$(sha256sum ${remote_caddy_basename} | awk '{print \$1}'); if [[ \"\${remote_sha}\" != '${local_sha}' ]]; then echo \"error: remote ${remote_caddy_basename} sha256=\${remote_sha} != local ${local_sha}\" >&2; exit 1; fi; ${remote_compose_cmd} up -d --no-deps --force-recreate caddy"
  fi
fi

if [[ -n "$DO_CLEAN_START" ]]; then
  # Let the remote login shell expand ~ (do not wrap the path in single quotes on the remote).
  sync_operator_files "sync lightsail operator files (before clean-start)"
  echo ""
  echo "→ remote clean-start (destructive: docker compose down -v, keygen, up)"
  run ssh "${SSH_HOST}" "set -euo pipefail; cd ${LIGHTSAIL_REMOTE_DIR} && bash clean-start.sh"
fi

echo ""
echo "Done."
if [[ -n "$DO_CLEAN_START" ]]; then
  :
elif [[ -n "$REMOTE_UP" ]]; then
  echo "Rolling steps finished: operator files are synced (if --sync-operator-files) and the stack was restarted. Postgres and named app volumes are unchanged; the migration service applies Flyway from the new image on up."
else
  echo "Tip: pre-upgrade log scan — --sync-operator-files (no --remote-up), then on the VM:"
  echo "     bash scripts/ops/docker-log-hygiene.sh --project ezkey-experimental-lightsail --scan"
  echo "     then re-run with --remote-up (or full-exp-environment-upgrade.sh rolling --target …)."
  echo "     See also experimental-hybrid/BACKEND_ROLLING_UPDATE.md"
fi
