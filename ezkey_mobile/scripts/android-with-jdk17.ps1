# Run Android build with JDK 17/21 (required for RN 0.85 Android toolchain).
# Prefer Git Bash: ./scripts/android-with-jdk17.sh
$Candidates = @(
  "C:\Program Files\Android\Android Studio\jbr",
  "C:\Program Files\Android\Android Studio1\jbr",
  "C:\Tools\jdk17",
  "C:\Tools\jdk-17.0.18+8",
  "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
)
if ($env:EZKEY_ANDROID_JAVA_HOME -and (Test-Path "$env:EZKEY_ANDROID_JAVA_HOME\bin\java.exe")) {
  $env:JAVA_HOME = $env:EZKEY_ANDROID_JAVA_HOME
} else {
  $found = $false
  foreach ($jbr in $Candidates) {
    if (Test-Path "$jbr\bin\java.exe") {
      $env:JAVA_HOME = $jbr
      $found = $true
      break
    }
  }
  if (-not $found) {
    Write-Error "No JDK 17/21 found. Set EZKEY_ANDROID_JAVA_HOME or install Android Studio JBR / JDK 17. Do not use JDK 25 for mobile Android."
    exit 1
  }
}
Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
npx react-native run-android @args
