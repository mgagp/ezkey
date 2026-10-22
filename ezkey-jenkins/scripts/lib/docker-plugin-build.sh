#!/usr/bin/env bash
# Shared Docker builder for lab .hpi plugins (JDK 21 image).
# Prefers BuildKit cache mount (id=ezkey-jenkins-m2); falls back to a named volume
# when buildx is unavailable — host still needs only Docker (no local JDK).
# Never writes a root-of-repo .dockerignore (use --ignorefile or docker run).
# shellcheck shell=bash

ezkey_jenkins_docker_build_plugin() {
  local root="$1"
  local module="$2"
  local repo_root
  repo_root="$(cd "${root}/.." && pwd)"
  local dockerfile="${root}/docker/Dockerfile.plugin-build"
  local ignorefile="${root}/docker/plugin-build.dockerignore"
  local out_dir="${root}/${module}/target"
  local image="${EZKEY_JENKINS_MAVEN_IMAGE:-maven:3.9-eclipse-temurin-21}"
  local volume="${EZKEY_JENKINS_M2_VOLUME:-ezkey-jenkins-m2}"

  if ! command -v docker >/dev/null 2>&1; then
    echo "Docker is required to build ${module} (no local JDK needed)." >&2
    exit 1
  fi

  mkdir -p "${out_dir}"
  rm -f "${out_dir}/${module}.hpi"

  # BuildKit path only when buildx AND --ignorefile are available (no root .dockerignore).
  if _ezkey_jenkins_buildx_ok && _ezkey_jenkins_ignorefile_ok && [[ -f "${dockerfile}" ]]; then
    echo "Building ${module}.hpi with ${image} (BuildKit cache id=ezkey-jenkins-m2) ..."
    DOCKER_BUILDKIT=1 docker build \
      -f "${dockerfile}" \
      --ignorefile "${ignorefile}" \
      --build-arg "MAVEN_IMAGE=${image}" \
      --build-arg "PLUGIN_MODULE=${module}" \
      --target export \
      -o "type=local,dest=${out_dir}" \
      "${repo_root}"
  else
    echo "Building ${module}.hpi with ${image} (docker run + volume ${volume}) ..."
    docker volume create "${volume}" >/dev/null
    docker run --rm \
      -v "${repo_root}:/src" \
      -v "${volume}:/root/.m2" \
      -w /src \
      -e "PLUGIN_MODULE=${module}" \
      "${image}" \
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
        if [ "${PLUGIN_MODULE}" = "ezkey-lab-auth" ]; then
          mvn -f ezkey-jenkins/ezkey-lab-auth/pom.xml -s ezkey-jenkins/maven-settings.xml clean test package
        else
          mvn -f ezkey-jenkins/ezkey-lab-hello/pom.xml -s ezkey-jenkins/maven-settings.xml clean -DskipTests package
        fi
        test -f "ezkey-jenkins/${PLUGIN_MODULE}/target/${PLUGIN_MODULE}.hpi"
      '
  fi

  local hpi="${out_dir}/${module}.hpi"
  if [[ ! -f "${hpi}" ]]; then
    echo "Docker build did not produce ${hpi}" >&2
    exit 1
  fi
  echo "Built: ${hpi}"
  ls -la "${hpi}"
}

_ezkey_jenkins_buildx_ok() {
  if ! docker buildx version >/dev/null 2>&1; then
    return 1
  fi
  return 0
}

_ezkey_jenkins_ignorefile_ok() {
  docker build --help 2>&1 | grep -q -- '--ignorefile'
}
