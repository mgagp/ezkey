#!/usr/bin/env bash
# Build ezkey-lab-auth.hpi (JDK 21 + Maven 3.9+).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_DIR="${ROOT}/ezkey-lab-auth"
SETTINGS="${ROOT}/maven-settings.xml"

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
  echo "JAVA_HOME with JDK 21 required." >&2
  exit 1
fi

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
    echo "Maven 3.9+ required. Set EZKEY_JENKINS_MVN." >&2
    exit 1
  fi
fi

export PATH="${JAVA_HOME}/bin:${PATH}"

# Plugin depends on org.ezkey:ezkey-sdk. Install a standalone POM+jar into ~/.m2 so the
# Jenkins-plugin Maven reactor (outside Ezkey parent) can resolve it without CI-friendly
# ${revision} placeholders left in the reactor-published POM.
REPO_ROOT="$(cd "${ROOT}/.." && pwd)"
SDK_JAR="${REPO_ROOT}/ezkey-sdk/java/target/ezkey-sdk-0.0.1-SNAPSHOT.jar"
STANDALONE_POM="${ROOT}/maven/ezkey-sdk-standalone.pom"
echo "Building/installing org.ezkey:ezkey-sdk for plugin packaging ..."
(cd "${REPO_ROOT}" && "${MVN_BIN}" -pl ezkey-sdk/java -am package -DskipTests -q)
test -f "${SDK_JAR}"
"${MVN_BIN}" install:install-file \
  -DgroupId=org.ezkey \
  -DartifactId=ezkey-sdk \
  -Dversion=0.0.1-SNAPSHOT \
  -Dpackaging=jar \
  -Dfile="${SDK_JAR}" \
  -DpomFile="${STANDALONE_POM}" \
  -q

cd "${PLUGIN_DIR}"
"${MVN_BIN}" -s "${SETTINGS}" test package
HPI="${PLUGIN_DIR}/target/ezkey-lab-auth.hpi"
test -f "${HPI}"
echo "Built: ${HPI}"
ls -la "${HPI}"
