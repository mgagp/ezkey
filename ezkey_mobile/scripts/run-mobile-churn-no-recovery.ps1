param(
  [int]$Iterations = 5,
  [int]$EnrollmentId = 2,
  [string]$AdminApiBaseUrl = "http://localhost:9080",
  [string]$AdminToken,
  [string]$ReportDir = "maestro/reports/churn-no-recovery",
  [switch]$ChallengeRequested
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Join-ApiUrl {
  param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Path
  )
  return ($BaseUrl.TrimEnd('/') + '/' + $Path.TrimStart('/'))
}

function Test-PhoneEnrollmentVisible {
  param(
    [Parameter(Mandatory = $true)][int]$TargetEnrollmentId
  )

  $tmpDir = Join-Path (Get-Location).Path "tmp"
  New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
  $dumpPath = Join-Path $tmpDir "phone-home-dump.xml"

  & adb shell input keyevent KEYCODE_WAKEUP | Out-Null
  & adb shell wm dismiss-keyguard | Out-Null
  & adb shell am start -n org.ezkey.mobile/.MainActivity | Out-Null
  Start-Sleep -Milliseconds 1200
  & adb shell uiautomator dump /sdcard/uidump.xml | Out-Null
  & adb pull /sdcard/uidump.xml $dumpPath | Out-Null

  if (-not (Test-Path $dumpPath)) {
    throw "Could not read phone UI hierarchy dump."
  }

  $dump = Get-Content -Path $dumpPath -Raw
  return ($dump -match "ezkey\.e2e\.home\.enrollment\.$TargetEnrollmentId")
}

if (-not $PSBoundParameters.ContainsKey('AdminToken') -or [string]::IsNullOrWhiteSpace($AdminToken)) {
  if (-not [string]::IsNullOrWhiteSpace($env:EZKEY_ADMIN_TOKEN)) {
    $AdminToken = $env:EZKEY_ADMIN_TOKEN
  }
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
  throw "Admin token required. Pass -AdminToken or set EZKEY_ADMIN_TOKEN."
}

if (-not (Test-PhoneEnrollmentVisible -TargetEnrollmentId $EnrollmentId)) {
  throw @"
No visible enrollment tile found on the real phone for ENROLLMENT_ID=$EnrollmentId.

This run uses the real mobile app UI lane. Demo Device enrollments are separate and do not
populate the phone Home list automatically.

Action: enroll this same enrollment id on the real phone first, then rerun.
"@
}

New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
$summaryPath = Join-Path $ReportDir "summary.csv"
"iteration,authAttemptId,maestroExitCode,authAttemptStatus" | Set-Content -Path $summaryPath -Encoding UTF8

$headers = @{
  Authorization = "Bearer $AdminToken"
  'Content-Type' = 'application/json'
}

$createAttemptUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/auth-attempts"

for ($i = 1; $i -le $Iterations; $i++) {
  Write-Host "[churn] iteration $i/$Iterations - creating auth attempt..."

  $body = @{
    enrollmentId = $EnrollmentId
    challengeRequested = [bool]$ChallengeRequested
    contextTitle = "Mobile churn"
    contextMessage = "Iteration $i"
  } | ConvertTo-Json -Depth 5

  $attempt = Invoke-RestMethod -Method Post -Uri $createAttemptUrl -Headers $headers -Body $body
  $authAttemptId = [int]$attempt.authAttemptId

  Write-Host "[churn] iteration $i/$Iterations - running Maestro pending/respond..."
  $maestroCmd = "cd /c/w/e/ezkey_mobile && ENROLLMENT_ID=$EnrollmentId ./scripts/run-real-device-pilot-maestro.sh"
  & "C:\Program Files\Git\bin\bash.exe" -lc $maestroCmd
  $maestroExit = $LASTEXITCODE

  $statusUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/auth-attempts/$authAttemptId"
  $attemptStatus = "UNKNOWN"

  try {
    $attemptAfter = Invoke-RestMethod -Method Get -Uri $statusUrl -Headers @{ Authorization = "Bearer $AdminToken" }
    if ($null -ne $attemptAfter.authAttemptStatus -and $attemptAfter.authAttemptStatus -ne "") {
      $attemptStatus = [string]$attemptAfter.authAttemptStatus
    }
  }
  catch {
    $attemptStatus = "LOOKUP_FAILED"
  }

  "$i,$authAttemptId,$maestroExit,$attemptStatus" | Add-Content -Path $summaryPath -Encoding UTF8

  if ($maestroExit -ne 0) {
    Write-Host "[churn] iteration $i failed (maestroExitCode=$maestroExit, authAttemptStatus=$attemptStatus)."
    throw "Churn stopped on iteration $i"
  }

  Write-Host "[churn] iteration $i passed (authAttemptId=$authAttemptId, status=$attemptStatus)."
}

Write-Host "[churn] completed. Summary: $summaryPath"
