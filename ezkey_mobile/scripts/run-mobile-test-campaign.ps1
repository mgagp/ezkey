param(
  [ValidateSet('all', 'phase1', 'phase2', 'phase3')]
  [string]$Phase = 'all',

  [string]$Username = 'mobile_tester',
  [string]$AdminApiBaseUrl = 'http://localhost:9080',
  [string]$AuthApiBaseUrl = 'http://localhost:8080',
  [string]$CryptoApiBaseUrl = 'http://localhost:9090',
  [string]$EnrollmentAuthUrl = 'https://goateed-katalina-monsoonal.ngrok-free.dev',

  [int]$Iterations = 5,
  [string]$StatePath = 'maestro/reports/mobile-test-campaign-state.json',
  [string]$SummaryPath = 'maestro/reports/mobile-test-campaign-summary.csv',

  [switch]$BindOnPhone
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dockerDir = (Resolve-Path (Join-Path $repoRoot '..\docker')).Path

if (-not $PSBoundParameters.ContainsKey('BindOnPhone')) {
  $BindOnPhone = $true
}

function Join-ApiUrl {
  param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [Parameter(Mandatory = $true)][string]$Path
  )
  return ($BaseUrl.TrimEnd('/') + '/' + $Path.TrimStart('/'))
}

function Ensure-DirectoryForFile {
  param([Parameter(Mandatory = $true)][string]$FilePath)
  $dir = Split-Path -Path $FilePath -Parent
  if ($dir) {
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
  }
}

function Test-ObjectProperty {
  param(
    [Parameter(Mandatory = $true)]$Object,
    [Parameter(Mandatory = $true)][string]$PropertyName
  )

  if ($null -eq $Object) {
    return $false
  }

  return ($Object.PSObject.Properties.Name -contains $PropertyName)
}

function Set-StateField {
  param(
    [Parameter(Mandatory = $true)]$StateObject,
    [Parameter(Mandatory = $true)][string]$FieldName,
    [Parameter(Mandatory = $false)]$Value
  )

  if ($StateObject -is [hashtable]) {
    $StateObject[$FieldName] = $Value
    return
  }

  if (Test-ObjectProperty -Object $StateObject -PropertyName $FieldName) {
    $StateObject.$FieldName = $Value
    return
  }

  $StateObject | Add-Member -NotePropertyName $FieldName -NotePropertyValue $Value -Force
}

function Load-State {
  param([Parameter(Mandatory = $true)][string]$Path)
  if (-not (Test-Path $Path)) {
    return @{
      version = 1
      campaigns = @()
    }
  }

  $raw = Get-Content -Path $Path -Raw
  if ([string]::IsNullOrWhiteSpace($raw)) {
    return @{
      version = 1
      campaigns = @()
    }
  }

  $obj = $raw | ConvertFrom-Json
  if ($null -eq $obj.campaigns) {
    $obj | Add-Member -NotePropertyName campaigns -NotePropertyValue @() -Force
  }
  return $obj
}

function Save-State {
  param(
    [Parameter(Mandatory = $true)][string]$Path,
    [Parameter(Mandatory = $true)]$State
  )
  Ensure-DirectoryForFile -FilePath $Path
  $State | ConvertTo-Json -Depth 20 | Set-Content -Path $Path -Encoding UTF8
}

function Invoke-Api {
  param(
    [Parameter(Mandatory = $true)][string]$Method,
    [Parameter(Mandatory = $true)][string]$Url,
    [hashtable]$Headers,
    $Body
  )

  if ($null -ne $Body) {
    return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers -Body ($Body | ConvertTo-Json -Depth 10) -ContentType 'application/json'
  }

  return Invoke-RestMethod -Method $Method -Uri $Url -Headers $Headers
}

function Get-AdminEnrollmentId {
  param([Parameter(Mandatory = $true)][string]$TargetUsername)

  Push-Location $dockerDir
  try {
  $safeUsername = $TargetUsername.Replace("'", "''")
  $query = "SELECT enrollment_id FROM ezkey_admin WHERE username='${safeUsername}' LIMIT 1;"
  $output = & docker compose exec -T postgres psql -U postgres -d ezkey_db -At -c $query

  $line = ($output | Where-Object { $_ -match '^[0-9]+$' } | Select-Object -First 1)
  if (-not $line) {
    throw "No enrollment_id found for username=$TargetUsername in ezkey_admin."
  }

  return [int]$line
  }
  finally {
    Pop-Location
  }
}

function Get-DemoDeviceEnrollmentMaterial {
  param([Parameter(Mandatory = $true)][int]$EnrollmentId)

  Push-Location $dockerDir
  try {
  $json = & docker compose exec -T demo-device sh -lc "sed -n '1,220p' /app/data/enrollments/${EnrollmentId}.json"
  $raw = ($json -join "`n").Trim()

  if ([string]::IsNullOrWhiteSpace($raw) -or -not $raw.StartsWith('{')) {
    throw "Could not load Demo Device enrollment file for enrollmentId=$EnrollmentId."
  }

  $obj = $raw | ConvertFrom-Json
  return @{
    enrollmentId = [int]$obj.enrollmentId
    enrollmentProofToken = [string]$obj.enrollmentProofToken
    devicePrivateKey = [string]$obj.devicePrivateKey
    devicePublicKey = [string]$obj.devicePublicKey
  }
  }
  finally {
    Pop-Location
  }
}

function Invoke-CryptoGenerateProofToken {
  $url = Join-ApiUrl -BaseUrl $CryptoApiBaseUrl -Path '/api/v1/crypto/prooftoken'
  $response = Invoke-Api -Method 'GET' -Url $url
  if ($null -eq $response.proofToken -or $response.proofToken -eq '') {
    throw 'Crypto API returned empty proofToken.'
  }
  return [string]$response.proofToken
}

function Invoke-CryptoSign {
  param(
    [Parameter(Mandatory = $true)][string]$Data,
    [Parameter(Mandatory = $true)][string]$PrivateKey
  )

  $url = Join-ApiUrl -BaseUrl $CryptoApiBaseUrl -Path '/api/v1/crypto/sign'
  $body = @{
    data = $Data
    privateKey = $PrivateKey
  }
  $response = Invoke-Api -Method 'POST' -Url $url -Body $body
  if ($null -eq $response.signature -or $response.signature -eq '') {
    throw 'Crypto API returned empty signature.'
  }
  return [string]$response.signature
}

function Get-AdminTokenFromDemoDevice {
  param([Parameter(Mandatory = $true)][string]$TargetUsername)

  $enrollmentId = Get-AdminEnrollmentId -TargetUsername $TargetUsername
  $deviceMaterial = Get-DemoDeviceEnrollmentMaterial -EnrollmentId $enrollmentId

  if ([string]::IsNullOrWhiteSpace([string]$deviceMaterial.enrollmentProofToken) -or [string]::IsNullOrWhiteSpace([string]$deviceMaterial.devicePrivateKey)) {
    throw "Demo Device enrollment JSON for id=$enrollmentId is missing required fields."
  }

  $loginUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path '/api/v1/admin/auth/login'
  $loginBody = @{
    username = $TargetUsername
    challengeRequested = $true
  }
  $loginResponse = Invoke-Api -Method 'POST' -Url $loginUrl -Body $loginBody

  $authAttemptId = [int]$loginResponse.authAttemptId
  $challengeCode = [int]$loginResponse.challengeCode

  if ($authAttemptId -le 0 -or $challengeCode -le 0) {
    throw 'Admin login did not return authAttemptId/challengeCode in two-call mode.'
  }

  $deviceProofToken = Invoke-CryptoGenerateProofToken
  $deviceProofTokenSigned = Invoke-CryptoSign -Data $deviceProofToken -PrivateKey ([string]$deviceMaterial.devicePrivateKey)

  $pendingUrl = Join-ApiUrl -BaseUrl $AuthApiBaseUrl -Path '/api/v1/auth-attempts/pending'
  $pendingBody = @{
    enrollmentId = [int]$deviceMaterial.enrollmentId
    enrollmentProofToken = [string]$deviceMaterial.enrollmentProofToken
    deviceProofToken = $deviceProofToken
    deviceProofTokenSigned = $deviceProofTokenSigned
  }
  $pendingResponse = Invoke-Api -Method 'POST' -Url $pendingUrl -Body $pendingBody

  $authAttemptProofToken = [string]$pendingResponse.authAttemptProofToken
  if ([string]::IsNullOrWhiteSpace($authAttemptProofToken)) {
    throw 'Auth pending response is missing authAttemptProofToken.'
  }

  $respondPayload = $authAttemptProofToken + '|true'
  $authAttemptProofTokenSignedByDevice = Invoke-CryptoSign -Data $respondPayload -PrivateKey ([string]$deviceMaterial.devicePrivateKey)

  $respondUrl = Join-ApiUrl -BaseUrl $AuthApiBaseUrl -Path '/api/v1/auth-attempts/respond'
  $respondBody = @{
    authAttemptId = $authAttemptId
    authAttemptAccepted = $true
    authAttemptProofTokenSignedByDevice = $authAttemptProofTokenSignedByDevice
    authAttemptChallengeResponse = $challengeCode
  }
  [void](Invoke-Api -Method 'POST' -Url $respondUrl -Body $respondBody)

  $waitUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path '/api/v1/admin/auth/passwordless-wait'
  $waitBody = @{
    authAttemptId = $authAttemptId
    challengeCode = $challengeCode
  }
  $waitResponse = Invoke-Api -Method 'POST' -Url $waitUrl -Body $waitBody

  if ($null -eq $waitResponse.token -or $waitResponse.token -eq '') {
    throw 'Passwordless wait response did not return a token.'
  }

  return @{
    token = [string]$waitResponse.token
    enrollmentId = [int]$deviceMaterial.enrollmentId
    challengeCode = $challengeCode
  }
}

function Test-IntegrationExists {
  param(
    [Parameter(Mandatory = $true)][int]$IntegrationId,
    [Parameter(Mandatory = $true)][string]$AdminToken
  )

  try {
    $headers = @{ Authorization = "Bearer $AdminToken" }
    $url = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/integrations/$IntegrationId"
    $resp = Invoke-Api -Method 'GET' -Url $url -Headers $headers
    return ($null -ne $resp)
  }
  catch {
    return $false
  }
}

function Ensure-CampaignIntegration {
  param(
    [Parameter(Mandatory = $true)]$State,
    [Parameter(Mandatory = $true)][string]$AdminToken,
    [Parameter(Mandatory = $true)][string]$TargetUsername
  )

  $headers = @{ Authorization = "Bearer $AdminToken" }

  if ((Test-ObjectProperty -Object $State -PropertyName 'integration') -and $null -ne $State.integration) {
    $existing = $State.integration
    if ($null -ne $existing.integrationId -and (Test-IntegrationExists -IntegrationId ([int]$existing.integrationId) -AdminToken $AdminToken)) {
      return $existing
    }
  }

  $integrationCode = "mobile-campaign-" + ($TargetUsername -replace '[^a-zA-Z0-9\-]', '-').ToLowerInvariant()
  $createUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path '/api/v1/integrations'
  $createBody = @{
    code = $integrationCode + '-' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmss')
    name = "Mobile Campaign Integration ($TargetUsername)"
    description = 'Dedicated integration for autonomous mobile campaign runs'
  }

  $createResponse = Invoke-Api -Method 'POST' -Url $createUrl -Headers $headers -Body $createBody

  $integration = @{
    integrationId = [int]$createResponse.id
    code = [string]$createBody.code
    name = [string]$createBody.name
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
  }

  Set-StateField -StateObject $State -FieldName 'integration' -Value $integration
  return $integration
}

function New-CampaignEnrollment {
  param(
    [Parameter(Mandatory = $true)][int]$IntegrationId,
    [Parameter(Mandatory = $true)][string]$AdminToken
  )

  $headers = @{ Authorization = "Bearer $AdminToken" }
  $createUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path '/api/v1/enrollments'
  $createBody = @{
    integrationId = $IntegrationId
    name = 'Mobile Campaign Device ' + [DateTime]::UtcNow.ToString('yyyy-MM-dd HH:mm:ss')
    authAttemptChallengeRequired = $true
  }

  $response = Invoke-Api -Method 'POST' -Url $createUrl -Headers $headers -Body $createBody

  if ($null -eq $response.enrollmentId) {
    throw 'Enrollment creation response is missing enrollmentId.'
  }

  $enrollmentId = [int]$response.enrollmentId
  $challenge = if ($null -ne $response.enrollmentChallenge) { [int]$response.enrollmentChallenge } else { 0 }

  $detailsUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path ("/api/v1/enrollments/{0}" -f $enrollmentId)
  $details = Invoke-Api -Method 'GET' -Url $detailsUrl -Headers $headers

  if ([string]::IsNullOrWhiteSpace([string]$details.enrollmentProofToken)) {
    throw "Enrollment details for id=$enrollmentId did not include enrollmentProofToken."
  }

  if ($challenge -le 0 -and $null -ne $details.enrollmentChallenge) {
    $challenge = [int]$details.enrollmentChallenge
  }

  if ($challenge -le 0) {
    throw "Enrollment details for id=$enrollmentId did not include enrollmentChallenge."
  }

  return @{
    enrollmentId = $enrollmentId
    enrollmentProofToken = [string]$details.enrollmentProofToken
    enrollmentChallenge = $challenge
    authAttemptChallengeRequired = $true
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
  }
}

function Invoke-MaestroEnrollmentBind {
  param(
    [Parameter(Mandatory = $true)]$Campaign
  )

  $cmd = @(
    "cd /c/w/e/ezkey_mobile",
    "maestro test maestro/flows/pilot_enrollment_full_runtime.yaml",
    "-e ENROLLMENT_ID=$($Campaign.enrollmentId)",
    "-e ENROLLMENT_PROOF_TOKEN=$($Campaign.enrollmentProofToken)",
    "-e ENROLLMENT_AUTH_URL=$EnrollmentAuthUrl",
    "-e ENROLLMENT_CHALLENGE=$('{0:D6}' -f [int]$Campaign.enrollmentChallenge)",
    "--format junit",
    "--output maestro/reports/pilot-enrollment-full-runtime.xml"
  ) -join ' '

  & "C:\Program Files\Git\bin\bash.exe" -lc $cmd
  if ($LASTEXITCODE -ne 0) {
    throw "Enrollment Maestro flow failed with exit code $LASTEXITCODE"
  }
}

function Test-AdbDeviceConnected {
  $lines = (& adb devices 2>$null)
  if ($LASTEXITCODE -ne 0 -or $null -eq $lines) {
    return $false
  }

  $devices = $lines | Where-Object { $_ -match '^\S+\s+device$' }
  return (@($devices).Count -gt 0)
}

function Test-PhoneEnrollmentVisible {
  param([Parameter(Mandatory = $true)][int]$TargetEnrollmentId)

  if (-not (Test-AdbDeviceConnected)) {
    throw 'No Android device detected via ADB. Connect or authorize a device before phase3.'
  }

  $tmpDir = Join-Path (Get-Location).Path 'tmp'
  New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
  $dumpPath = Join-Path $tmpDir 'phone-home-dump.xml'

  & adb shell input keyevent KEYCODE_WAKEUP | Out-Null
  & adb shell wm dismiss-keyguard | Out-Null
  & adb shell am start -n org.ezkey.mobile/.MainActivity | Out-Null
  Start-Sleep -Milliseconds 1200
  & adb shell uiautomator dump /sdcard/uidump.xml | Out-Null
  & adb pull /sdcard/uidump.xml $dumpPath | Out-Null

  if (-not (Test-Path $dumpPath)) {
    throw 'Could not read phone UI hierarchy dump.'
  }

  $dump = Get-Content -Path $dumpPath -Raw
  return ($dump -match "ezkey\\.e2e\\.home\\.enrollment\\.$TargetEnrollmentId")
}

function Get-AuthAttemptStatus {
  param(
    [Parameter(Mandatory = $true)][int]$AuthAttemptId,
    [Parameter(Mandatory = $true)][string]$AdminToken
  )

  $url = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/auth-attempts/$AuthAttemptId"
  $headers = @{ Authorization = "Bearer $AdminToken" }
  $resp = Invoke-Api -Method 'GET' -Url $url -Headers $headers
  return [string]$resp.authAttemptStatus
}

function Wait-ForAuthAttemptFinal {
  param(
    [Parameter(Mandatory = $true)][int]$AuthAttemptId,
    [Parameter(Mandatory = $true)][string]$AdminToken,
    [int]$TimeoutSeconds = 180
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $status = Get-AuthAttemptStatus -AuthAttemptId $AuthAttemptId -AdminToken $AdminToken
    if ($status -in @('ACCEPTED', 'REJECTED', 'INVALID', 'EXPIRED')) {
      return $status
    }
    Start-Sleep -Seconds 5
  }

  return 'TIMEOUT_WAITING_FINAL'
}

function Invoke-ChurnIteration {
  param(
    [Parameter(Mandatory = $true)][int]$Iteration,
    [Parameter(Mandatory = $true)][int]$EnrollmentId,
    [Parameter(Mandatory = $true)][string]$AdminToken,
    [Parameter(Mandatory = $true)][string]$Scenario
  )

  $headers = @{ Authorization = "Bearer $AdminToken" }
  $createUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path '/api/v1/auth-attempts'

  $challengeRequested = ($Scenario -eq 'approve_with_challenge')
  $createBody = @{
    enrollmentId = $EnrollmentId
    challengeRequested = $challengeRequested
    contextTitle = 'Mobile campaign churn'
    contextMessage = "Iteration $Iteration / $Scenario"
  }

  $createResp = Invoke-Api -Method 'POST' -Url $createUrl -Headers $headers -Body $createBody
  $authAttemptId = [int]$createResp.authAttemptId

  $maestroExit = 0
  if ($Scenario -eq 'approve_no_challenge') {
    $cmd = "cd /c/w/e/ezkey_mobile && ENROLLMENT_ID=$EnrollmentId ./scripts/run-real-device-pilot-maestro.sh"
    & "C:\Program Files\Git\bin\bash.exe" -lc $cmd
    $maestroExit = $LASTEXITCODE
  }
  elseif ($Scenario -eq 'approve_with_challenge') {
    $challengeCode = [int]$createResp.authAttemptChallenge
    $cmd = "cd /c/w/e/ezkey_mobile && ENROLLMENT_ID=$EnrollmentId CHALLENGE_CODE=$challengeCode ./scripts/run-real-device-pilot-maestro.sh"
    & "C:\Program Files\Git\bin\bash.exe" -lc $cmd
    $maestroExit = $LASTEXITCODE
  }
  elseif ($Scenario -eq 'timeout_no_action') {
    $maestroExit = 0
  }
  else {
    throw "Unknown scenario: $Scenario"
  }

  $finalStatus = Wait-ForAuthAttemptFinal -AuthAttemptId $authAttemptId -AdminToken $AdminToken

  return @{
    iteration = $Iteration
    scenario = $Scenario
    authAttemptId = $authAttemptId
    maestroExitCode = $maestroExit
    finalStatus = $finalStatus
    createdChallenge = if ($createResp.PSObject.Properties.Name -contains 'authAttemptChallenge') { $createResp.authAttemptChallenge } else { $null }
  }
}

$state = Load-State -Path $StatePath
$campaign = $null
$adminToken = $null

if ($Phase -in @('all', 'phase1')) {
  Write-Host '[phase1] obtaining global admin token via Demo Device material...'
  $phase1 = Get-AdminTokenFromDemoDevice -TargetUsername $Username
  $adminToken = [string]$phase1.token

  Set-StateField -StateObject $state -FieldName 'phase1' -Value @{
    mode = 'demo-device-admin-passwordless'
    username = $Username
    enrollmentId = [int]$phase1.enrollmentId
    obtainedAtUtc = [DateTime]::UtcNow.ToString('o')
    tokenPreview = $adminToken.Substring(0, [Math]::Min(18, $adminToken.Length)) + '...'
  }
  Save-State -Path $StatePath -State $state
  Write-Host "[phase1] token obtained for $Username"
}

if ($Phase -in @('all', 'phase2')) {
  if ([string]::IsNullOrWhiteSpace($adminToken)) {
    if ((Test-ObjectProperty -Object $state -PropertyName 'phase1') -and $null -ne $state.phase1 -and $state.phase1.username -eq $Username) {
      Write-Host '[phase2] phase1 token not in memory; reacquiring token now...'
      $phase1 = Get-AdminTokenFromDemoDevice -TargetUsername $Username
      $adminToken = [string]$phase1.token
    }
    else {
      throw 'Phase2 requires phase1 token acquisition in the same run or compatible state.'
    }
  }

  Write-Host '[phase2] ensuring dedicated integration...'
  $integration = Ensure-CampaignIntegration -State $state -AdminToken $adminToken -TargetUsername $Username

  Write-Host '[phase2] creating fresh enrollment for this campaign...'
  $campaign = New-CampaignEnrollment -IntegrationId ([int]$integration.integrationId) -AdminToken $adminToken
  $campaign['username'] = $Username
  $campaign['integrationId'] = [int]$integration.integrationId
  $campaign['enrollmentAuthUrl'] = $EnrollmentAuthUrl

  Set-StateField -StateObject $state -FieldName 'currentCampaign' -Value $campaign
  $state.campaigns += $campaign
  Save-State -Path $StatePath -State $state

  Write-Host "[phase2] campaign enrollment created: id=$($campaign.enrollmentId)"

  if ($BindOnPhone) {
    Write-Host '[phase2] binding enrollment on real phone via Maestro full runtime flow...'
    Invoke-MaestroEnrollmentBind -Campaign $campaign
  }
}

if ($Phase -in @('all', 'phase3')) {
  if ([string]::IsNullOrWhiteSpace($adminToken)) {
    Write-Host '[phase3] phase1 token not in memory; reacquiring token now...'
    $phase1 = Get-AdminTokenFromDemoDevice -TargetUsername $Username
    $adminToken = [string]$phase1.token
  }

  if ($null -eq $campaign) {
    if ((Test-ObjectProperty -Object $state -PropertyName 'currentCampaign') -and $null -ne $state.currentCampaign) {
      $campaign = $state.currentCampaign
    }
    else {
      throw 'Phase3 requires an active campaign enrollment from phase2.'
    }
  }

  $enrollmentId = [int]$campaign.enrollmentId
  if (-not (Test-PhoneEnrollmentVisible -TargetEnrollmentId $enrollmentId)) {
    throw "Campaign enrollment $enrollmentId is not visible on phone Home. Bind/verify on phone first."
  }

  Ensure-DirectoryForFile -FilePath $SummaryPath
  'iteration,scenario,authAttemptId,maestroExitCode,finalStatus,createdChallenge' | Set-Content -Path $SummaryPath -Encoding UTF8

  $scenarios = @('approve_no_challenge', 'approve_with_challenge', 'timeout_no_action')

  Write-Host "[phase3] starting churn for enrollmentId=$enrollmentId, iterations=$Iterations"

  for ($i = 1; $i -le $Iterations; $i++) {
    $scenario = $scenarios[($i - 1) % $scenarios.Count]
    $result = Invoke-ChurnIteration -Iteration $i -EnrollmentId $enrollmentId -AdminToken $adminToken -Scenario $scenario

    "$($result.iteration),$($result.scenario),$($result.authAttemptId),$($result.maestroExitCode),$($result.finalStatus),$($result.createdChallenge)" | Add-Content -Path $SummaryPath -Encoding UTF8

    Set-StateField -StateObject $state -FieldName 'lastChurnIteration' -Value $result
    Save-State -Path $StatePath -State $state

    if ($result.maestroExitCode -ne 0) {
      throw "Phase3 failed on iteration $i (scenario=$scenario, maestroExit=$($result.maestroExitCode))"
    }

    if ($scenario -like 'approve_*' -and $result.finalStatus -ne 'ACCEPTED') {
      throw "Phase3 iteration $i expected ACCEPTED but got $($result.finalStatus)"
    }

    if ($scenario -eq 'timeout_no_action' -and $result.finalStatus -ne 'EXPIRED') {
      throw "Phase3 iteration $i expected EXPIRED but got $($result.finalStatus)"
    }

    Write-Host "[phase3] iteration $i/$Iterations OK ($scenario -> $($result.finalStatus))"
  }

  Write-Host "[phase3] completed successfully. Summary: $SummaryPath"
}

Write-Host "[campaign] done. State: $StatePath"
