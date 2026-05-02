# Run Android build with JDK 17 (required on this workstation for the current React Native 0.85.2 Android toolchain).
# Use this if you see "Error resolving plugin" or "Unsupported class file major version 69".
$jbr = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path $jbr)) {
  Write-Error "Android Studio JBR not found at $jbr. Install Android Studio or set JAVA_HOME manually to JDK 17."
  exit 1
}
$env:JAVA_HOME = $jbr
npx react-native run-android @args
