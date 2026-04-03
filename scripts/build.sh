#!/bin/bash

set -euo pipefail
set -x

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

DIAGNOSE_ONLY="${1:-}"

if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in "/c/Tools/jdk25" "/mnt/c/Tools/jdk25"; do
    if [ -x "$candidate/bin/java" ] || [ -x "$candidate/bin/java.exe" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
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
    echo "Use Git Bash explicitly or run scripts/build-local.cmd from Windows."
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
mvn checkstyle:check
mvn clean
mvn install -DskipTests
mvn test -pl '!ezkey-tests'
