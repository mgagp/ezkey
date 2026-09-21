#!/bin/bash

set -euo pipefail
set -x

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

DIAGNOSE_ONLY="${1:-}"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "/c/Tools/jdk-25.0.3+9" \
    "/mnt/c/Tools/jdk-25.0.3+9" \
    "/c/Tools/jdk25" \
    "/mnt/c/Tools/jdk25" \
    "${HOME}/.sdkman/candidates/java/current"; do
    if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  if jdk25="$(/usr/libexec/java_home -v 25 2>/dev/null)"; then
    export JAVA_HOME="$jdk25"
  fi
fi

if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/bin" ]; then
  case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
  esac
fi

echo "Build environment diagnostics"
echo "  repo_root=$REPO_ROOT"
echo "  shell=${SHELL:-<unset>}"
echo "  java_home=${JAVA_HOME:-<unset>}"
echo "  bash=$(command -v bash || true)"
echo "  java=$(command -v java || true)"
echo "  java_exe=$(command -v java.exe || true)"
echo "  javac=$(command -v javac || true)"
echo "  javac_exe=$(command -v javac.exe || true)"
echo "  mvn=$(command -v mvn || true)"

if ! command -v java >/dev/null 2>&1; then
  if command -v java.exe >/dev/null 2>&1; then
    echo "Detected Windows JDK commands as .exe only. This usually means WSL bash was launched instead of Git Bash."
    echo "Use Git Bash explicitly: \"C:\\Program Files\\Git\\bin\\bash.exe\" -lc './scripts/build.sh'"
  else
    echo "No Java runtime found on PATH."
  fi
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven was not found on PATH."
  exit 1
fi

java -version
javac -version
mvn -version

if [ "$DIAGNOSE_ONLY" = "--diagnose-only" ]; then
  exit 0
fi

mvn spotless:apply

# Bootstrap the reactor-internal Checkstyle ruleset module into the local Maven
# repository BEFORE `checkstyle:check`. The maven-checkstyle-plugin declares
# `org.ezkey:checkstyle-config` as a plugin dependency, so on a fresh ~/.m2 (a new
# machine, CI runner, or cloud cold start) `checkstyle:check` fails trying to fetch
# that never-published artifact from remote. Installing it first is cheap and
# idempotent, and makes this script self-bootstrapping regardless of ~/.m2 state.
mvn -pl checkstyle-config install -DskipTests

mvn checkstyle:check
mvn clean
mvn install -DskipTests
mvn test -pl '!ezkey-tests'
