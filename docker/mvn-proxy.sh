#!/bin/sh
# Runs Maven, through an HTTPS proxy when the standard HTTPS_PROXY build arg is set.
#
# Used in Maven build stages only (never copied into runtime images). apk and npm honour
# HTTPS_PROXY by themselves; Maven ignores it, and ignores -Dhttps.proxyHost too (resolver
# transport), so this writes a throwaway settings.xml with a <proxy> entry and passes it with -s.
# Proxy credentials (user:pass@) are not forwarded: an authenticating proxy is out of scope.
# Without HTTPS_PROXY it is plain `mvn "$@"`. Typical callers: Claude Code cloud sessions and
# hosts behind a corporate proxy (docker/README.md § Building behind an HTTPS proxy).
set -eu

if [ -n "${HTTPS_PROXY:-}" ]; then
  hostport="${HTTPS_PROXY#*://}"
  hostport="${hostport%%/*}"
  hostport="${hostport##*@}"
  settings=/tmp/mvn-proxy-settings.xml
  printf '<settings><proxies><proxy><id>build</id><active>true</active><protocol>https</protocol><host>%s</host><port>%s</port></proxy></proxies></settings>\n' \
    "${hostport%:*}" "${hostport##*:}" > "$settings"
  exec mvn -s "$settings" "$@"
fi
exec mvn "$@"
