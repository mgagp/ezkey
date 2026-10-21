#!/usr/bin/env bash
# Build ezkey-lab-hello.hpi (JDK 21 + Maven 3.9+).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_DIR="${ROOT}/ezkey-lab-hello"
SETTINGS="${ROOT}/maven-settings.xml"

# Prefer JDK 21 (Jenkins plugin tutorial baseline). Fall back to JAVA_HOME if already 21.
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  for candidate in \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/temurin-21-jdk-amd64 \
    /c/Tools/jdk-21*; do
    if [[ -x "${candidate}/bin/java" ]]; then
      export JAVA_HOME="${candidate}"
      break
    fi
  done
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME with JDK 21 required to build the plugin." >&2
  exit 1
fi

# System Maven 3.8.x reports "Unknown packaging: hpi". Prefer 3.9+.
MVN_BIN="${EZKEY_JENKINS_MVN:-}"
if [[ -z "${MVN_BIN}" ]]; then
  if command -v mvn >/dev/null 2>&1; then
    ver="$(mvn -version 2>/dev/null | head -1 || true)"
    if printf '%s' "$ver" | grep -Eq 'Apache Maven 3\.(9|[0-9]{2,})\.'; then
      MVN_BIN="$(command -v mvn)"
    fi
  fi
fi
if [[ -z "${MVN_BIN}" ]]; then
  if [[ -x /tmp/apache-maven-3.9.11/bin/mvn ]]; then
    MVN_BIN=/tmp/apache-maven-3.9.11/bin/mvn
  else
    echo "Maven 3.9+ required (got system Maven too old for packaging=hpi)." >&2
    echo "Install Maven 3.9+ or set EZKEY_JENKINS_MVN to its mvn binary." >&2
    exit 1
  fi
fi

export PATH="${JAVA_HOME}/bin:${PATH}"
echo "JAVA_HOME=${JAVA_HOME}"
"${JAVA_HOME}/bin/java" -version
"${MVN_BIN}" -version | head -3

cd "${PLUGIN_DIR}"
"${MVN_BIN}" -s "${SETTINGS}" -DskipTests package

HPI="${PLUGIN_DIR}/target/ezkey-lab-hello.hpi"
test -f "${HPI}"
echo "Built: ${HPI}"
ls -la "${HPI}"
