#!/usr/bin/env bash
# Keep the Android screen awake for the duration of a Maestro / campaign run, then restore.
# Sourced by run-mobile-real-device.sh and run-real-device-pilot-maestro.sh.
#
# stay_on_while_plugged_in bit mask (Settings.Global / BatteryManager):
#   1 = AC, 2 = USB, 4 = wireless charging. 3 = AC|USB (wired). 7 = AC|USB|wireless charging.
# Wi-Fi ADB is not a plug source, so this file also raises screen_off_timeout for the run.
# shellcheck shell=bash

EZKEY_STAY_ON_PREV=""
EZKEY_SCREEN_OFF_PREV=""
EZKEY_STAY_AWAKE_APPLIED=false

ezkey_adb_settings_get() {
  adb shell settings get "$1" "$2" 2>/dev/null | tr -d '\r' | tail -n1
}

ezkey_android_stay_awake_begin() {
  if [[ "${EZKEY_ANDROID_STAY_AWAKE_OWNED:-}" == "1" ]]; then
    echo "  stay-awake: parent campaign already owns screen-on settings"
    return 0
  fi
  if [[ "${EZKEY_ANDROID_STAY_AWAKE:-1}" != "1" ]]; then
    echo "  stay-awake: skipped (--no-stay-awake or EZKEY_ANDROID_STAY_AWAKE=0)"
    return 0
  fi

  EZKEY_STAY_ON_PREV="$(ezkey_adb_settings_get global stay_on_while_plugged_in)"
  EZKEY_SCREEN_OFF_PREV="$(ezkey_adb_settings_get system screen_off_timeout)"

  # Bitwise OR so we never strip dock/wireless bits already set (this Pixel was 15 = AC|USB|wireless|dock).
  # Requested default 3 = USB + AC. Override with EZKEY_ANDROID_STAY_ON_WHILE_PLUGGED_IN (e.g. 7 or 15).
  local requested="${EZKEY_ANDROID_STAY_ON_WHILE_PLUGGED_IN:-3}"
  local prev_num="${EZKEY_STAY_ON_PREV:-0}"
  if [[ ! "${prev_num}" =~ ^[0-9]+$ ]]; then
    prev_num=0
  fi
  if [[ ! "${requested}" =~ ^[0-9]+$ ]]; then
    requested=3
  fi
  local mask=$((prev_num | requested))
  adb shell settings put global stay_on_while_plugged_in "${mask}" >/dev/null
  # Default 30 minutes so JUnit gaps and Maestro waits do not hit a 30s display timeout.
  local timeout_ms="${EZKEY_ANDROID_SCREEN_OFF_TIMEOUT_MS:-1800000}"
  adb shell settings put system screen_off_timeout "${timeout_ms}" >/dev/null

  EZKEY_STAY_AWAKE_APPLIED=true
  export EZKEY_ANDROID_STAY_AWAKE_OWNED=1

  local stay_now off_now
  stay_now="$(ezkey_adb_settings_get global stay_on_while_plugged_in)"
  off_now="$(ezkey_adb_settings_get system screen_off_timeout)"
  echo "  stay_on_while_plugged_in: ${EZKEY_STAY_ON_PREV:-unset} -> ${stay_now} (mask ${mask}; restore on exit)"
  echo "  screen_off_timeout_ms: ${EZKEY_SCREEN_OFF_PREV:-unset} -> ${off_now} (Wi-Fi ADB is not USB/AC)"
}

ezkey_android_stay_awake_restore() {
  if [[ "${EZKEY_STAY_AWAKE_APPLIED}" != true ]]; then
    return 0
  fi
  EZKEY_STAY_AWAKE_APPLIED=false
  unset EZKEY_ANDROID_STAY_AWAKE_OWNED

  local stay="${EZKEY_STAY_ON_PREV:-0}"
  local off="${EZKEY_SCREEN_OFF_PREV:-30000}"
  if [[ -z "${stay}" || "${stay}" == "null" ]]; then
    stay=0
  fi
  if [[ -z "${off}" || "${off}" == "null" ]]; then
    off=30000
  fi
  adb shell settings put global stay_on_while_plugged_in "${stay}" >/dev/null 2>&1 || true
  adb shell settings put system screen_off_timeout "${off}" >/dev/null 2>&1 || true
  echo "  stay-awake restored: stay_on_while_plugged_in=${stay} screen_off_timeout_ms=${off}"
}
