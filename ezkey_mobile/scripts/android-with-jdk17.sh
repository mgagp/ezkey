#!/usr/bin/env bash
# Run Android build with JDK 17 (required on this workstation for the current React Native 0.85.2 Android toolchain).
# Use this if you see "Error resolving plugin" or "Unsupported class file major version 69".
JBR_WIN="C:/Program Files/Android/Android Studio/jbr"
JBR_WIN_MSYS="/c/Program Files/Android/Android Studio/jbr"
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "msys2" ]]; then
  if [[ -d "$JBR_WIN" ]]; then
    export JAVA_HOME="$JBR_WIN"
  elif [[ -d "$JBR_WIN_MSYS" ]]; then
    export JAVA_HOME="$JBR_WIN_MSYS"
  fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
  JBR=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
  [[ -n "$JBR" ]] && export JAVA_HOME="$JBR"
fi
if [[ -z "$JAVA_HOME" ]]; then
  echo "Could not find JDK 17. Install Android Studio or set JAVA_HOME to JDK 17."
  exit 1
fi
exec npx react-native run-android "$@"
