#!/usr/bin/env bash
# Run Android build with JDK 17 (required for React Native 0.76).
# Use short path on Windows to avoid "ClassNotFoundException: Files.Android.Android" (spaces in path).
JBR_WIN_LONG="C:/Program Files/Android/Android Studio/jbr"
JBR_WIN_SHORT="C:/PROGRA~1/Android/ANDROI~1/jbr"
JBR_WIN_MSYS_SHORT="/c/PROGRA~1/Android/ANDROI~1/jbr"
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "msys2" ]]; then
  if [[ -d "$JBR_WIN_SHORT" ]]; then
    export JAVA_HOME="$JBR_WIN_SHORT"
  elif [[ -d "$JBR_WIN_MSYS_SHORT" ]]; then
    export JAVA_HOME="$JBR_WIN_MSYS_SHORT"
  elif [[ -d "$JBR_WIN_LONG" ]]; then
    export JAVA_HOME="$JBR_WIN_LONG"
  fi
elif [[ "$OSTYPE" == "darwin"* ]]; then
  JBR=$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")
  [[ -n "$JBR" ]] && export JAVA_HOME="$JBR"
fi
if [[ -z "$JAVA_HOME" ]]; then
  echo "Could not find JDK 17. Install Android Studio or set JAVA_HOME to JDK 17."
  exit 1
fi
# Force Gradle to use this JDK (no spaces in path for gradlew.bat)
export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME"
exec npx react-native run-android "$@"
