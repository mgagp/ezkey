# Run Android build with JDK 17 (required for React Native 0.76).
# Use this if you see "Error resolving plugin" or "Unsupported class file major version 69".
$jbr = "C:\Program Files\Android\Android Studio\jbr"
if (-not (Test-Path $jbr)) {
  Write-Error "Android Studio JBR not found at $jbr. Install Android Studio or set JAVA_HOME manually to JDK 17."
  exit 1
}
$env:JAVA_HOME = $jbr
npx react-native run-android @args
