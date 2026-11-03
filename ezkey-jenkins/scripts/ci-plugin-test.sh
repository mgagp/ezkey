#!/usr/bin/env bash
# CI entrypoint: build SDK + run ezkey-lab-auth mvn test in Docker (Temurin 21).
# Host needs Docker only. Path-filtered from .github/workflows/ci.yml (ezkey-jenkins/**).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${ROOT}/.." && pwd)"
IMAGE="${EZKEY_JENKINS_MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}"
VOLUME="${EZKEY_JENKINS_M2_VOLUME:-ezkey-jenkins-m2}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for ezkey-jenkins plugin tests." >&2
  exit 1
fi

docker volume create "${VOLUME}" >/dev/null
echo "ezkey-jenkins plugin tests with ${IMAGE} (volume ${VOLUME}, host network for JenkinsRule) ..."
# Host network: JenkinsRule Jetty bind is unreliable under Docker bridge port mapping.
docker run --rm \
  --network host \
  -v "${REPO_ROOT}:/src" \
  -v "${VOLUME}:/root/.m2" \
  -w /src \
  -e JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true" \
  "${IMAGE}" \
  bash -lc '
    set -euo pipefail
    mvn -pl ezkey-sdk/java -am package -DskipTests -q \
      -Dlicense.skipAggregateAddThirdParty=true \
      -Dlicense.skipAddThirdParty=true \
      -Dspotless.check.skip=true \
      -Dcheckstyle.skip=true
    test -f ezkey-sdk/java/target/ezkey-sdk-0.0.1-SNAPSHOT.jar
    mvn install:install-file \
      -DgroupId=org.ezkey \
      -DartifactId=ezkey-sdk \
      -Dversion=0.0.1-SNAPSHOT \
      -Dpackaging=jar \
      -Dfile=ezkey-sdk/java/target/ezkey-sdk-0.0.1-SNAPSHOT.jar \
      -DpomFile=ezkey-jenkins/maven/ezkey-sdk-standalone.pom \
      -q
    mvn -f ezkey-jenkins/ezkey-lab-auth/pom.xml -s ezkey-jenkins/maven-settings.xml test
  '
echo "ezkey-jenkins plugin tests OK"
