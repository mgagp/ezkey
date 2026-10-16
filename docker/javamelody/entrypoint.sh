#!/bin/sh
# JavaMelody collector entrypoint.
# Security flags are always on (lab + community). No secrets in -D arguments (#846).
set -e
STORAGE="${JAVAMELODY_STORAGE_DIRECTORY:-/tmp/javamelody}"
mkdir -p "${STORAGE}"
if [ -f /opt/javamelody/applications.properties ]; then
  cp /opt/javamelody/applications.properties "${STORAGE}/applications.properties"
fi
XMX="${JAVAMELODY_XMX:-256m}"
# Optional extra JVM flags from the operator (must not carry secrets — never put
# authorized-users / passwords here; they appear in the JVM report).
# shellcheck disable=SC2086
exec java -Xmx"${XMX}" \
  -Djavamelody.system-actions-enabled=false \
  -Djavamelody.csrf-protection-enabled=true \
  -Djavamelody.update-check-disabled=true \
  -Djavamelody.obsolete-graphs-days="${JAVAMELODY_OBSOLETE_GRAPHS_DAYS:-30}" \
  -Djavamelody.obsolete-stats-days="${JAVAMELODY_OBSOLETE_STATS_DAYS:-30}" \
  -Djavamelody.log=false \
  ${JAVAMELODY_JAVA_OPTS:-} \
  -jar /opt/javamelody/collector.war
