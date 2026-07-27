# Ezkey Tests - Clean Start Script (PowerShell)
# Performs a clean startup of the Docker stack for testing.
# Usage: .\clean-start.ps1 [-Ha] [-MvnBootstrap] [-Jmx] [-ProdSafe]
#
# Optional environment for Docker Compose (auth-api):
#   $env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED = 'true'|'false'  — maps to ezkey.demo.mitm-signature-enabled.
#   Default when unset: true (demo-friendly), except -ProdSafe defaults to false. See docs/DEMO_MITM_SIGNATURE.md.

param(
    [switch]$Ha,
    [switch]$MvnBootstrap,
    [switch]$Jmx,
    [switch]$ProdSafe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$DockerDir = Join-Path $ProjectRoot "docker"
$TestStateDir = Join-Path $ScriptDir ".ezkey-test"

if ($ProdSafe) {
    $springProfiles = "docker"
} else {
    $springProfiles = "docker,docker-dev,docker-test"
}

if ($Jmx) {
    $env:EZKEY_ENABLE_JMX = "true"
}

function Get-DockerComposeCommand {
    try {
        docker compose version | Out-Null
        return @("docker", "compose")
    } catch {
        return @("docker-compose")
    }
}

function Invoke-DockerCompose {
    param([string[]]$Args)
    if ($DockerComposeCommand.Length -gt 1) {
        & $DockerComposeCommand[0] $DockerComposeCommand[1] @Args
    } else {
        & $DockerComposeCommand[0] @Args
    }
}

$DockerComposeCommand = Get-DockerComposeCommand

Write-Host "=========================================="
Write-Host "  Ezkey Tests - Clean Start"
Write-Host "=========================================="
Write-Host ""

# Step 1: Stop Docker Compose stack including volumes
if ($Ha) {
    Write-Host "Step 1/7: Stopping Docker Compose HA stack (including volumes)..."
    $composeFile = Join-Path $DockerDir "docker-compose.ha.yml"
} else {
    Write-Host "Step 1/7: Stopping Docker Compose stack (including volumes)..."
    $composeFile = Join-Path $DockerDir "docker-compose.yml"
}

Set-Location $ProjectRoot

if (Test-Path $composeFile) {
    Write-Host "  Stopping containers..."
    try {
        Invoke-DockerCompose @("-f", $composeFile, "down", "-v") | Out-Null
        Write-Host "  Docker stack stopped and volumes removed"
    } catch {
        Write-Host "  Warning: Some containers may not have been running"
    }
} else {
    Write-Host "  Warning: $composeFile not found"
}

# Also try to stop other compose files for cleanup
if ($Ha) {
    $otherFiles = @(
        (Join-Path $DockerDir "docker-compose.yml")
    )
} else {
    $otherFiles = @(
        (Join-Path $DockerDir "docker-compose.ha.yml")
    )
}

foreach ($otherFile in $otherFiles) {
    if ((Test-Path $otherFile) -and ($otherFile -ne $composeFile)) {
        try {
            Invoke-DockerCompose @("-f", $otherFile, "down", "-v") | Out-Null
        } catch {
            # ignore cleanup errors
        }
    }
}

Write-Host ""

# Step 2: Clean test state files
Write-Host "Step 2/7: Cleaning test state files..."
Set-Location $ScriptDir

if (Test-Path $TestStateDir) {
    Write-Host "  Removing files in $TestStateDir..."
    Remove-Item -Path (Join-Path $TestStateDir "admin-token.json") -Force -ErrorAction SilentlyContinue
    Remove-Item -Path (Join-Path $TestStateDir "bootstrap-credentials.json") -Force -ErrorAction SilentlyContinue
    Remove-Item -Path (Join-Path $TestStateDir "device-credentials.json") -Force -ErrorAction SilentlyContinue
    Remove-Item -Path (Join-Path $TestStateDir "tenant-admin-*-device-credentials.json") -Force -ErrorAction SilentlyContinue
    Remove-Item -Path (Join-Path $TestStateDir "tenant-admin-*-token.json") -Force -ErrorAction SilentlyContinue
    Write-Host "  Test state files cleaned (including tenant admin credentials)"
} else {
    Write-Host "  Creating test state directory..."
    New-Item -ItemType Directory -Path $TestStateDir | Out-Null
    Write-Host "  Test state directory created"
}

Write-Host ""

# Step 3: Generate master encryption key
if ($Ha) {
    Write-Host "Step 3/7: Generating master encryption key (HA mode)..."
} else {
    Write-Host "Step 3/7: Generating master encryption key..."
}

Set-Location $ProjectRoot

$KeyGenScript = Join-Path $DockerDir "generate-encryption-keys.ps1"
if (Test-Path $KeyGenScript) {
    if ($Ha) {
        & $KeyGenScript -Ha
    } else {
        & $KeyGenScript
    }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "  Master key generated"
} else {
    Write-Host "  Error: generate-encryption-keys.ps1 not found at $KeyGenScript" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 4: Start Docker Compose stack with test profiles
if ($Ha) {
    Write-Host "Step 4/7: Starting Docker Compose HA stack with profiles ($springProfiles)..."
    Write-Host "  HA mode: 2 instances of each API behind HAProxy load balancers"
} else {
    Write-Host "Step 4/7: Starting Docker Compose stack with profiles ($springProfiles)..."
}

Set-Location $ProjectRoot
$previousProfiles = $env:SPRING_PROFILES_ACTIVE
$env:SPRING_PROFILES_ACTIVE = $springProfiles

$previousDemoMitm = $env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED
if ($ProdSafe) {
    if ([string]::IsNullOrEmpty($env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED)) {
        $env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED = "false"
    }
} else {
    if ([string]::IsNullOrEmpty($env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED)) {
        $env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED = "true"
    }
}

if ($Ha) {
    $startHaScript = Join-Path $DockerDir "start-ha.ps1"
    if (Test-Path $startHaScript) {
        Write-Host "  Starting HA stack with SPRING_PROFILES_ACTIVE=$springProfiles..."
        & $startHaScript
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "  Docker HA stack started"
    } else {
        Write-Host "  Error: start-ha.ps1 not found at $startHaScript" -ForegroundColor Red
        exit 1
    }
} else {
    $startScript = Join-Path $DockerDir "start.ps1"
    if (Test-Path $startScript) {
        Write-Host "  Starting stack with SPRING_PROFILES_ACTIVE=$springProfiles..."
        & $startScript
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host "  Docker stack started"
    } else {
        Write-Host "  Error: start.ps1 not found at $startScript" -ForegroundColor Red
        exit 1
    }
}

$demoMitmUsedForSummary = $env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED
$env:SPRING_PROFILES_ACTIVE = $previousProfiles
$env:EZKEY_DEMO_MITM_SIGNATURE_ENABLED = $previousDemoMitm

Write-Host ""

# Step 5-7: Maven-based bootstrap (optional)
if ($MvnBootstrap) {
    Write-Host "Step 5/7: Installing project and dependencies..."
    Set-Location $ProjectRoot

    Write-Host "  Installing ezkey-admin-api and ezkey-auth-api (required for original classifier JARs)..."
    & mvn clean install -pl ezkey-admin-api,ezkey-auth-api -am -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Error: Failed to install Admin API and Auth API" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Admin API and Auth API installed successfully"

    Write-Host "  Compiling ezkey-tests module..."
    & mvn clean compile test-compile -pl ezkey-tests -am -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Error: Failed to compile project" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Project compiled successfully"

    Write-Host ""
    Write-Host "Step 6/7: Extracting bootstrap credentials..."
    Set-Location $ProjectRoot

    Write-Host "  Running BootstrapCredentialsExtractionTest..."
    & mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Error: Failed to extract bootstrap credentials" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Bootstrap credentials extracted"

    Write-Host ""
    Write-Host "Step 7/7: Initializing admin token..."
    Set-Location $ProjectRoot

    Write-Host "  Running AdminTokenCreationTest..."
    & mvn test -pl ezkey-tests -Dtest=AdminTokenCreationTest -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  Error: Failed to initialize admin token" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Admin token initialized"

    Write-Host ""
} else {
    Write-Host "Step 5/7: Skipping Maven-based bootstrap (Docker bootstrap-init handles this)"
    Write-Host "  Bootstrap handled by Docker bootstrap-init container"
    Write-Host ""
}

Write-Host ""
Write-Host "=========================================="
Write-Host "  Clean Start Complete!"
Write-Host "=========================================="
Write-Host ""
Write-Host "Stack Status:"
if ($Ha) {
    Write-Host "  - Docker stack: Running HA mode with profiles ($springProfiles)"
    Write-Host "  - Instances: 2x admin-api, 2x auth-api behind HAProxy load balancers"
    Write-Host "  - Admin API: http://localhost:9080 (via HAProxy)"
    Write-Host "  - Auth API: http://localhost:8080 (via HAProxy)"
    Write-Host "  - HAProxy Stats: http://localhost:9081/stats (Admin), http://localhost:8085/stats (Auth)"
} else {
    Write-Host "  - Docker stack: Running with profiles ($springProfiles)"
}
if (-not $ProdSafe) {
    Write-Host "  - Auth API demo MITM: EZKEY_DEMO_MITM_SIGNATURE_ENABLED=$demoMitmUsedForSummary (Pending tamper when attempt is flagged; see docs/DEMO_MITM_SIGNATURE.md)"
} else {
    Write-Host "  - Auth API demo MITM: EZKEY_DEMO_MITM_SIGNATURE_ENABLED=$demoMitmUsedForSummary (-ProdSafe defaults false unless you pre-set the variable)"
}
if ($MvnBootstrap) {
    Write-Host "  - Bootstrap credentials: Extracted to .ezkey-test/bootstrap-credentials.json"
    Write-Host "  - Admin token: Created and saved to .ezkey-test/admin-token.json"
} else {
    Write-Host "  - Bootstrap: Handled automatically by Docker bootstrap-init container"
    Write-Host "  - Demo-device: Pre-seeded and ready for use"
}
Write-Host ""
Write-Host "Next Steps - Running Tests:"
Write-Host ""
Write-Host "  Default (fast tests only - excludes slow, time-dependent):"
Write-Host "    mvn test -pl ezkey-tests"
Write-Host "    Use case: CI on every commit, quick local validation"
Write-Host ""
Write-Host "  All tests (full validation before release):"
Write-Host "    mvn test -pl ezkey-tests -P all-tests"
Write-Host "    Use case: Complete test suite, pre-release validation"
Write-Host ""
Write-Host "  Slow tests only (nightly builds):"
Write-Host "    mvn test -pl ezkey-tests -P slow-tests"
Write-Host "    Use case: Nightly builds, comprehensive validation"
Write-Host ""
Write-Host "  Smoke tests only (quick sanity check):"
Write-Host "    mvn test -pl ezkey-tests -P smoke-tests"
Write-Host "    Use case: Quick validation, post-deployment check"
Write-Host ""
Write-Host "  Specific test class:"
Write-Host "    mvn test -pl ezkey-tests -Dtest=TestClassName"
Write-Host ""
Write-Host "  Ad-hoc filtering (by test groups):"
Write-Host "    mvn test -pl ezkey-tests -Dgroups=encryption"
Write-Host "    mvn test -pl ezkey-tests -DexcludedGroups=time-dependent"
Write-Host ""
Write-Host "Useful Commands:"
if ($Ha) {
    Write-Host "  - View logs: cd ..\docker ; .\manage-ha.ps1 logs"
    Write-Host "  - View instance logs: cd ..\docker ; .\manage-ha.ps1 logs admin-api-1"
    Write-Host "  - Stop stack: cd ..\docker ; .\manage-ha.ps1 stop"
    Write-Host "  - View status: cd ..\docker ; .\manage-ha.ps1 status"
    Write-Host "  - Check HAProxy stats: Start-Process http://localhost:9081/stats"
    Write-Host "  - Start standard stack: .\clean-start.ps1"
} else {
    Write-Host "  - View logs: cd ..\docker ; .\manage.ps1 logs"
    Write-Host "  - Stop stack: cd ..\docker ; .\manage.ps1 stop"
    Write-Host "  - View status: cd ..\docker ; .\manage.ps1 status"
    Write-Host "  - Start with HA stack: .\clean-start.ps1 -Ha"
}
Write-Host ""
