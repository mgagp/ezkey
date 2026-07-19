param(
    [Parameter(Mandatory = $true)]
    [string]$AdminApiBaseUrl,

    [Parameter(Mandatory = $true)]
    [string]$Username,

    [Parameter(Mandatory = $true)]
    [string]$RecoveryCode,

    [Parameter(Mandatory = $true)]
    [string]$EnrollmentAuthUrl,

    [int]$EnrollmentId,

    [string]$Reason = "Device replacement for automated Maestro enrollment flow",

    [string]$OutputJson = "maestro/reports/fresh-enrollment-seed.json",

    [string]$OutputPs1 = "maestro/reports/fresh-enrollment-seed.ps1",

    [switch]$RunMaestro
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Join-ApiUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return ($BaseUrl.TrimEnd('/') + '/' + $Path.TrimStart('/'))
}

function Ensure-SuccessField {
    param(
        [Parameter(Mandatory = $true)]
        $Response,
        [Parameter(Mandatory = $true)]
        [string]$Operation
    )

    if ($null -eq $Response.success -or -not $Response.success) {
        $message = if ($null -ne $Response.message -and $Response.message -ne "") {
            $Response.message
        } else {
            "$Operation failed without message"
        }
        throw "$Operation failed: $message"
    }
}

if ($Reason.Length -lt 10) {
    throw "Reason must contain at least 10 characters (EnrollmentResetRequestDto constraint)."
}

$recoverUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/admin/auth/recover"
$resetUrl = Join-ApiUrl -BaseUrl $AdminApiBaseUrl -Path "/api/v1/admin/enrollments/reset"

Write-Host "[seed] Recovering admin session with recovery code..."
$recoverBody = @{
    username = $Username
    recoveryCode = $RecoveryCode
} | ConvertTo-Json -Depth 4

$recoverResponse = Invoke-RestMethod -Method Post -Uri $recoverUrl -ContentType "application/json" -Body $recoverBody
Ensure-SuccessField -Response $recoverResponse -Operation "Recovery"

if ($null -eq $recoverResponse.recoveryToken -or $recoverResponse.recoveryToken -eq "") {
    throw "Recovery succeeded but recoveryToken is missing."
}

$targetEnrollmentId = if ($PSBoundParameters.ContainsKey('EnrollmentId')) {
    $EnrollmentId
} else {
    [int]$recoverResponse.enrollmentId
}

if ($null -eq $targetEnrollmentId -or $targetEnrollmentId -le 0) {
    throw "Could not determine enrollmentId. Pass -EnrollmentId explicitly."
}

Write-Host "[seed] Resetting enrollment id $targetEnrollmentId for fresh one-shot credentials..."
$resetBody = @{
    enrollmentId = $targetEnrollmentId
    reason = $Reason
} | ConvertTo-Json -Depth 4

$resetHeaders = @{
    Authorization = "Bearer $($recoverResponse.recoveryToken)"
}

$resetResponse = Invoke-RestMethod -Method Post -Uri $resetUrl -Headers $resetHeaders -ContentType "application/json" -Body $resetBody
Ensure-SuccessField -Response $resetResponse -Operation "Enrollment reset"

if ($null -eq $resetResponse.enrollmentId -or $null -eq $resetResponse.enrollmentProofToken -or $null -eq $resetResponse.enrollmentChallenge) {
    throw "Enrollment reset succeeded but required fields are missing in response."
}

$challengeValue = [int]$resetResponse.enrollmentChallenge
$challengeCode = $challengeValue.ToString("D6")

$seed = [ordered]@{
    ENROLLMENT_ID = [int]$resetResponse.enrollmentId
    ENROLLMENT_PROOF_TOKEN = [string]$resetResponse.enrollmentProofToken
    ENROLLMENT_AUTH_URL = [string]$EnrollmentAuthUrl
    ENROLLMENT_CHALLENGE = $challengeCode
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    source = "admin-recovery-reset"
}

$jsonDirectory = Split-Path -Path $OutputJson -Parent
if ($jsonDirectory) {
    New-Item -ItemType Directory -Path $jsonDirectory -Force | Out-Null
}

$ps1Directory = Split-Path -Path $OutputPs1 -Parent
if ($ps1Directory) {
    New-Item -ItemType Directory -Path $ps1Directory -Force | Out-Null
}

$seed | ConvertTo-Json -Depth 4 | Set-Content -Path $OutputJson -Encoding UTF8

$ps1Lines = @(
    "$env:ENROLLMENT_ID = '$($seed.ENROLLMENT_ID)'",
    "$env:ENROLLMENT_PROOF_TOKEN = '$($seed.ENROLLMENT_PROOF_TOKEN)'",
    "$env:ENROLLMENT_AUTH_URL = '$($seed.ENROLLMENT_AUTH_URL)'",
    "$env:ENROLLMENT_CHALLENGE = '$($seed.ENROLLMENT_CHALLENGE)'"
)
$ps1Lines | Set-Content -Path $OutputPs1 -Encoding UTF8

Write-Host "[seed] Fresh enrollment seed generated."
Write-Host "[seed] JSON: $OutputJson"
Write-Host "[seed] PowerShell env file: $OutputPs1"
Write-Host "[seed] ENROLLMENT_ID=$($seed.ENROLLMENT_ID)"
Write-Host "[seed] ENROLLMENT_CHALLENGE=$($seed.ENROLLMENT_CHALLENGE)"

if ($RunMaestro) {
    Write-Host "[seed] Running Maestro full runtime flow with fresh credentials..."

    $maestroArgs = @(
        "test",
        "maestro/flows/pilot_enrollment_full_runtime.yaml",
        "-e", "ENROLLMENT_ID=$($seed.ENROLLMENT_ID)",
        "-e", "ENROLLMENT_PROOF_TOKEN=$($seed.ENROLLMENT_PROOF_TOKEN)",
        "-e", "ENROLLMENT_AUTH_URL=$($seed.ENROLLMENT_AUTH_URL)",
        "-e", "ENROLLMENT_CHALLENGE=$($seed.ENROLLMENT_CHALLENGE)",
        "--format", "junit",
        "--output", "maestro/reports/pilot-enrollment-full-runtime.xml"
    )

    & maestro @maestroArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Maestro flow failed with exit code $LASTEXITCODE"
    }
}
