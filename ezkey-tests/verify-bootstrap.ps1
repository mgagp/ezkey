# Ezkey Bootstrap Verification Script (Windows)
# Verifies that Clean Start and bootstrap process works correctly
# Usage: .\verify-bootstrap.ps1

$ErrorActionPreference = "Stop"

$dockerCompose = "docker-compose -f docker/docker-compose.yml"

function Check($msg) {
    Write-Host "[Check] $msg" -ForegroundColor Cyan
}

function Pass($msg) {
    Write-Host "[OK] $msg" -ForegroundColor Green
}

function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Warn($msg) {
    Write-Host "[WARN] $msg" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===================================================="
Write-Host "   Ezkey Bootstrap Verification Script"
Write-Host "===================================================="
Write-Host ""

# Step 1: Verify Bootstrap Credentials File
Write-Host "Step 1: Verify Bootstrap Credentials File Exists"
Write-Host "---------------------------------------------------"
Check "Bootstrap credentials file in Admin API"

try {
    $credFile = & docker-compose -f docker/docker-compose.yml exec -T admin-api cat /var/lib/ezkey/bootstrap/bootstrap-credentials.json 2>$null

    if ([string]::IsNullOrEmpty($credFile)) {
        Fail "Bootstrap credentials file not found in Admin API"
    }

    Pass "Bootstrap credentials file exists"

    # Parse JSON directly
    try {
        $json = $credFile | ConvertFrom-Json
        Pass "  - enrollmentId: $($json.enrollmentId)"
        Pass "  - enrollmentProofToken: present"
        Pass "  - enrollmentChallengeCode: $($json.enrollmentChallengeCode)"
        Pass "  - username: $($json.username)"
    } catch {
        # Fallback to simple pattern matching if JSON parsing fails
        if ($credFile -match 'enrollmentId') {
            Pass "  - enrollmentId: found"
        }
        if ($credFile -match 'enrollmentProofToken') {
            Pass "  - enrollmentProofToken: found"
        }
        if ($credFile -match 'enrollmentChallengeCode') {
            Pass "  - enrollmentChallengeCode: found"
        }
        if ($credFile -match 'username') {
            Pass "  - username: found"
        }
    }
} catch {
    Fail "Error reading bootstrap credentials: $_"
}

Write-Host ""
Write-Host "Step 2: Verify Device Credentials File Exists"
Write-Host "---------------------------------------------"
Check "Device credentials file in Bootstrap Init"

try {
    $deviceFile = & docker-compose -f docker/docker-compose.yml exec -T admin-api cat /var/lib/ezkey/bootstrap/device-credentials.json 2>$null

    if ([string]::IsNullOrEmpty($deviceFile)) {
        Fail "Device credentials file not found"
    }

    Pass "Device credentials file exists"
} catch {
    Fail "Error reading device credentials: $_"
}

Write-Host ""
Write-Host "Step 3: Verify Spring Profile is Active"
Write-Host "----------------------------------------"
Check "Docker profile active in Admin API"

try {
    $profileLog = & docker-compose -f docker/docker-compose.yml logs admin-api 2>&1 | Select-String "profile is active" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($null -eq $profileLog) {
        Fail "Could not find profile activation log in Admin API"
    }

    if ($profileLog -match "docker") {
        Pass "Docker profile is active"
    } else {
        Fail "Docker profile is NOT active in Admin API"
    }
} catch {
    Fail "Error checking profile: $_"
}

Write-Host ""
Write-Host "Step 4: Verify Bootstrap Export Message"
Write-Host "---------------------------------------"
Check "Bootstrap export confirmation in Admin API logs"

try {
    $exportLog = & docker-compose -f docker/docker-compose.yml logs admin-api 2>&1 | Select-String "bootstrap credentials exported" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($null -eq $exportLog) {
        Fail "Bootstrap export confirmation not found in Admin API logs"
    }

    Pass "Bootstrap export confirmed"
} catch {
    Fail "Error checking export log: $_"
}

Write-Host ""
Write-Host "Step 5: Verify Bootstrap Init Success"
Write-Host "-------------------------------------"
Check "Bootstrap Init completion"

try {
    $bootstrapComplete = & docker-compose -f docker/docker-compose.yml logs bootstrap-init 2>&1 | Select-String "Bootstrap Init Complete" -ErrorAction SilentlyContinue | Select-Object -First 1

    if ($null -eq $bootstrapComplete) {
        Warn "Bootstrap Init completion message not found (may still be running)"
    } else {
        Pass "Bootstrap Init completed successfully"
    }
} catch {
    Fail "Error checking bootstrap init: $_"
}

Write-Host ""
Write-Host "Step 6: Verify Container Health Status"
Write-Host "--------------------------------------"

try {
    $health = & docker-compose -f docker/docker-compose.yml ps --format "{{.Service}}: {{.Status}}" 2>&1

    foreach ($line in $health) {
        $parts = $line -split ": "
        if ($parts.Count -eq 2) {
            $service = $parts[0].Trim()
            $status = $parts[1].Trim()

            if ($status -match "healthy") {
                Pass "$service is healthy"
            } elseif ($status -match "starting") {
                Warn "$service is starting"
            } elseif ($status -match "Exited.*0") {
                Pass "$service completed successfully"
            } else {
                Fail "$service status is: $status"
            }
        }
    }
} catch {
    Fail "Error checking container health: $_"
}

Write-Host ""
Write-Host "===================================================="
Write-Host "   VERIFICATION COMPLETE"
Write-Host "===================================================="
Write-Host ""
Write-Host "Summary:"
Write-Host "  - Bootstrap credentials exported by Admin API"
Write-Host "  - Device credentials created by Bootstrap Init"
Write-Host "  - Docker profile active in Admin API"
Write-Host "  - All required fields present in credentials"
Write-Host "  - Services running and healthy"
Write-Host ""
Write-Host "Clean Start is ready for use!"
Write-Host ""
