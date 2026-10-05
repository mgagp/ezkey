#!/usr/bin/env bash
# Gate: Android 16 KB page-size ELF / ZIP alignment for release APK or AAB.
#
# Usage:
#   ./scripts/check-16kb-alignment.sh <path-to.apk|path-to.aab>
#   ./scripts/check-16kb-alignment.sh --help
#
# Checks (arm64-v8a and x86_64 .so only):
#   1. Every PT_LOAD p_align >= 0x4000
#   2. GNU_RELRO end (VirtAddr + MemSiz) % 0x4000 == 0 (when RELRO present)
#   3. For APK only: zipalign -c -P 16 -v 4
#
# Residual policy (Decision A / #659): React Native 0.87.1 Maven prebuilts may still
# fail GNU_RELRO. Those basenames are allowlisted with an explicit upstream link.
# Any other failing .so (including locally built libs) fails the gate hard.
# Do NOT use packagingOptions.jniLibs.useLegacyPackaging as a substitute.
#
# Official reference:
#   https://developer.android.com/guide/practices/page-sizes
#
# set -euo pipefail intentionally after arg parse so --help can exit 0 cleanly.

set -uo pipefail

PAGE_SIZE=$((0x4000))
SCRIPT_NAME="$(basename "$0")"

# --- Allowlist A: RN 0.87.1 Maven prebuilts (Decision A / #659) ---
# react-android / hermes-android: PT_LOAD often OK, GNU_RELRO end still misaligned.
# Upstream: https://developer.android.com/guide/practices/page-sizes
# Related:  https://github.com/facebook/react-native/issues/52594
# Do not bump RN in this change; reopen only if Play rejects AAB on this residual alone.
RN_PREBUILT_RELRO_ALLOWLIST=(
  libc++_shared.so
  libfbjni.so
  libhermes.so
  libhermestooling.so
  libhermesvm.so
  libjsi.so
  libreactnative.so
)

# --- Allowlist B: third-party Maven AAR prebuilts (not rebuilt by our NDK) ---
# Measured FAIL on current dependency AARs; cannot fix without vendor rebuild / RN bump
# (Fresco is pulled by react-android). Keep this list tiny and documented.
# - libimagepipeline.so / libnative-imagetranscoder.so / libnative-filters.so: Fresco
#   (RN 0.87.1 pins fresco 3.7.0; 3.8.0 still RELRO-FAIL)
# - libsurface_util_jni.so: AndroidX Camera (VisionCamera); 1.4–1.7-alpha still FAIL
# - libbarhopper_v3.so: ML Kit barcode-scanning (arm64 OK; x86_64 FAIL on 17.3.0)
THIRD_PARTY_PREBUILT_RELRO_ALLOWLIST=(
  libbarhopper_v3.so
  libimagepipeline.so
  libnative-filters.so
  libnative-imagetranscoder.so
  libsurface_util_jni.so
)

usage() {
  cat <<EOF
Usage: ${SCRIPT_NAME} <apk-or-aab>

Extracts arm64-v8a and x86_64 .so files, fails if any PT_LOAD p_align < 0x4000,
fails if GNU_RELRO end % 0x4000 != 0 (unless basename is a documented RN 0.87.1
prebuilt residual), and for APK runs zipalign -c -P 16 -v 4.

Exit 0 when only allowlisted RN prebuilt residuals (if any) fail RELRO.
Exit 1 on any other failure.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 2
fi

ARTIFACT="$1"
if [[ ! -f "$ARTIFACT" ]]; then
  echo "error: artifact not found: ${ARTIFACT}" >&2
  exit 2
fi

set -e

ARTIFACT_ABS="$(cd "$(dirname "$ARTIFACT")" && pwd)/$(basename "$ARTIFACT")"
EXT="${ARTIFACT_ABS##*.}"
EXT_LOWER="$(printf '%s' "$EXT" | tr '[:upper:]' '[:lower:]')"

is_allowlisted() {
  local base="$1"
  local name
  for name in "${RN_PREBUILT_RELRO_ALLOWLIST[@]}" "${THIRD_PARTY_PREBUILT_RELRO_ALLOWLIST[@]}"; do
    if [[ "$base" == "$name" ]]; then
      return 0
    fi
  done
  return 1
}

allowlist_reason() {
  local base="$1"
  local name
  for name in "${RN_PREBUILT_RELRO_ALLOWLIST[@]}"; do
    if [[ "$base" == "$name" ]]; then
      printf '%s' "RN 0.87.1 prebuilt residual; Decision A / #659"
      return 0
    fi
  done
  for name in "${THIRD_PARTY_PREBUILT_RELRO_ALLOWLIST[@]}"; do
    if [[ "$base" == "$name" ]]; then
      printf '%s' "third-party Maven prebuilt residual; #659"
      return 0
    fi
  done
  printf '%s' "allowlisted"
}

find_readelf() {
  if command -v llvm-readelf >/dev/null 2>&1; then
    command -v llvm-readelf
    return 0
  fi
  if command -v readelf >/dev/null 2>&1; then
    command -v readelf
    return 0
  fi
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  local ndk_root=""
  if [[ -n "$sdk_root" && -d "${sdk_root}/ndk" ]]; then
    ndk_root="$(find "${sdk_root}/ndk" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
  fi
  if [[ -n "$ndk_root" ]]; then
    local candidate
    candidate="$(find "$ndk_root/toolchains/llvm/prebuilt" -type f -name llvm-readelf 2>/dev/null | head -1 || true)"
    if [[ -n "$candidate" && -x "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi
  return 1
}

find_zipalign() {
  if command -v zipalign >/dev/null 2>&1; then
    command -v zipalign
    return 0
  fi
  local sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk_root" || ! -d "${sdk_root}/build-tools" ]]; then
    return 1
  fi
  local candidate
  candidate="$(find "${sdk_root}/build-tools" -type f -name zipalign 2>/dev/null | sort -V | tail -1 || true)"
  if [[ -n "$candidate" && -x "$candidate" ]]; then
    printf '%s\n' "$candidate"
    return 0
  fi
  return 1
}

READELF_BIN="$(find_readelf)" || {
  echo "error: llvm-readelf or readelf required" >&2
  exit 2
}

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/ezkey-16kb.XXXXXX")"
cleanup() {
  rm -rf "$WORKDIR"
}
trap cleanup EXIT

echo "==> 16 KB alignment gate: ${ARTIFACT_ABS}"
echo "    readelf: ${READELF_BIN}"
echo "    page size: 0x$(printf '%x' "$PAGE_SIZE")"

EXTRACT_DIR="${WORKDIR}/extract"
mkdir -p "$EXTRACT_DIR"
unzip -q -o "$ARTIFACT_ABS" -d "$EXTRACT_DIR"

# APK: lib/<abi>/*.so ; AAB: base/lib/<abi>/*.so (and optional feature modules */lib/<abi>)
mapfile -t SO_FILES < <(
  find "$EXTRACT_DIR" \( -path '*/lib/arm64-v8a/*.so' -o -path '*/lib/x86_64/*.so' \) -type f | sort
)

if [[ ${#SO_FILES[@]} -eq 0 ]]; then
  echo "error: no arm64-v8a or x86_64 .so files found in artifact" >&2
  exit 1
fi

echo "    scanned .so count: ${#SO_FILES[@]}"

HARD_FAIL=0
ALLOWLISTED_RELRO=0
PASS_COUNT=0

# Parse one PT_LOAD / GNU_RELRO program-header line from `readelf -lW` / `llvm-readelf -lW`.
# Columns after Type: Offset VirtAddr PhysAddr FileSiz MemSiz Flg Align (Flg may be multi-token).
parse_phdr_numbers() {
  local line="$1"
  # Drop leading type token (LOAD / GNU_RELRO / etc.)
  local rest
  rest="$(printf '%s' "$line" | sed -E 's/^[[:space:]]*[A-Za-z0-9_]+[[:space:]]+//')"
  # Collect hex/decimal tokens; last is Align, MemSiz is 5th numeric, VirtAddr is 2nd.
  local -a nums=()
  local tok
  for tok in $rest; do
    if [[ "$tok" =~ ^0[xX][0-9A-Fa-f]+$ || "$tok" =~ ^[0-9]+$ ]]; then
      nums+=("$tok")
    fi
  done
  if [[ ${#nums[@]} -lt 6 ]]; then
    return 1
  fi
  PHDR_VADDR="${nums[1]}"
  PHDR_MEMSIZ="${nums[4]}"
  PHDR_ALIGN="${nums[${#nums[@]}-1]}"
  return 0
}

to_dec() {
  local v="$1"
  if [[ "$v" =~ ^0[xX] ]]; then
    printf '%d' "$v"
  else
    printf '%d' "$v"
  fi
}

for so in "${SO_FILES[@]}"; do
  rel="${so#"$EXTRACT_DIR"/}"
  base="$(basename "$so")"
  headers="$("$READELF_BIN" -lW "$so" 2>/dev/null || true)"
  if [[ -z "$headers" ]]; then
    echo "FAIL  ${rel}: could not read ELF program headers"
    HARD_FAIL=$((HARD_FAIL + 1))
    continue
  fi

  load_ok=1
  while IFS= read -r line; do
    [[ "$line" =~ LOAD ]] || continue
    PHDR_VADDR=""
    PHDR_MEMSIZ=""
    PHDR_ALIGN=""
    if ! parse_phdr_numbers "$line"; then
      echo "FAIL  ${rel}: unparseable LOAD line: ${line}"
      load_ok=0
      HARD_FAIL=$((HARD_FAIL + 1))
      break
    fi
    align_dec="$(to_dec "$PHDR_ALIGN")"
    if (( align_dec < PAGE_SIZE )); then
      echo "FAIL  ${rel}: PT_LOAD p_align=${PHDR_ALIGN} (< 0x4000)"
      load_ok=0
      HARD_FAIL=$((HARD_FAIL + 1))
      break
    fi
  done < <(printf '%s\n' "$headers" | grep -E '[[:space:]]LOAD[[:space:]]' || true)

  if [[ "$load_ok" -ne 1 ]]; then
    continue
  fi

  relro_line="$(printf '%s\n' "$headers" | grep -E 'GNU_RELRO' | head -1 || true)"
  if [[ -z "$relro_line" ]]; then
    echo "PASS  ${rel}: PT_LOAD OK, no GNU_RELRO"
    PASS_COUNT=$((PASS_COUNT + 1))
    continue
  fi

  PHDR_VADDR=""
  PHDR_MEMSIZ=""
  PHDR_ALIGN=""
  if ! parse_phdr_numbers "$relro_line"; then
    echo "FAIL  ${rel}: unparseable GNU_RELRO line: ${relro_line}"
    HARD_FAIL=$((HARD_FAIL + 1))
    continue
  fi
  vaddr_dec="$(to_dec "$PHDR_VADDR")"
  memsiz_dec="$(to_dec "$PHDR_MEMSIZ")"
  end=$((vaddr_dec + memsiz_dec))
  rem=$((end % PAGE_SIZE))
  if (( rem == 0 )); then
    echo "PASS  ${rel}: PT_LOAD OK, GNU_RELRO end aligned"
    PASS_COUNT=$((PASS_COUNT + 1))
    continue
  fi

  if is_allowlisted "$base"; then
    echo "ALLOW ${rel}: GNU_RELRO end % 0x4000 = 0x$(printf '%x' "$rem") ($(allowlist_reason "$base"))"
    ALLOWLISTED_RELRO=$((ALLOWLISTED_RELRO + 1))
  else
    echo "FAIL  ${rel}: GNU_RELRO end % 0x4000 = 0x$(printf '%x' "$rem") (VirtAddr=${PHDR_VADDR} MemSiz=${PHDR_MEMSIZ})"
    HARD_FAIL=$((HARD_FAIL + 1))
  fi
done

ZIPALIGN_STATUS="skipped (AAB)"
if [[ "$EXT_LOWER" == "apk" ]]; then
  ZIPALIGN_BIN="$(find_zipalign)" || {
    echo "error: zipalign required for APK check (install Android build-tools 35+)" >&2
    exit 2
  }
  echo "==> zipalign -c -P 16 -v 4"
  if "$ZIPALIGN_BIN" -c -P 16 -v 4 "$ARTIFACT_ABS"; then
    ZIPALIGN_STATUS="PASS"
  else
    ZIPALIGN_STATUS="FAIL"
    HARD_FAIL=$((HARD_FAIL + 1))
  fi
elif [[ "$EXT_LOWER" == "aab" ]]; then
  echo "==> zipalign skipped for AAB (ELF checks only; Play packs from AAB)"
else
  echo "error: unsupported artifact extension .${EXT_LOWER} (expected apk or aab)" >&2
  exit 2
fi

echo
echo "==> Summary"
echo "    PASS:              ${PASS_COUNT}"
echo "    ALLOW (RN residual): ${ALLOWLISTED_RELRO}"
echo "    FAIL (hard):       ${HARD_FAIL}"
echo "    zipalign:          ${ZIPALIGN_STATUS}"

if [[ "$ALLOWLISTED_RELRO" -gt 0 ]]; then
  echo
  echo "Note: allowlisted RELRO residuals remain (#659)."
  echo "      RN 0.87.1 Decision A + third-party Maven AAR prebuilts (Fresco/CameraX/ML Kit)."
  echo "      Upstream: https://developer.android.com/guide/practices/page-sizes"
  echo "      Related:  https://github.com/facebook/react-native/issues/52594"
  echo "      Locally built .so must NOT appear here — raise common-page-size / rebuild."
fi

if [[ "$HARD_FAIL" -gt 0 ]]; then
  echo
  echo "error: 16 KB alignment gate failed (${HARD_FAIL} hard failure(s))" >&2
  exit 1
fi

echo
echo "OK: 16 KB alignment gate passed."
exit 0
