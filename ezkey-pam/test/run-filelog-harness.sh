#!/usr/bin/env bash
# Build and run ezkey_filelog harness in a throwaway 0700 directory (#818 m2).
set -euo pipefail

CC="${1:?cc}"
CFLAGS="${2:?cflags}"
SRC_DIR="${3:?src}"
BUILD_DIR="${4:?build}"

tmpdir="$(mktemp -d /tmp/ezkey-filelog-XXXXXX)"
chmod 700 "${tmpdir}"
trap 'rm -rf "${tmpdir}"' EXIT

# shellcheck disable=SC2086
${CC} ${CFLAGS} -DEZKEY_FILELOG_TEST -DEZKEY_LOG_DIR=\"${tmpdir}\" \
  -I"${SRC_DIR}" \
  "${SRC_DIR}/ezkey_filelog.c" "$(dirname "$0")/test_filelog_harness.c" \
  -o "${BUILD_DIR}/test_filelog_harness"

"${BUILD_DIR}/test_filelog_harness"
