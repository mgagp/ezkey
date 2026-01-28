# Maven wrapper that injects branch-specific buildQualifier to prevent artifact collisions
# between different git branches when building locally.
#
# Usage: pwsh -File .\scripts\mvn-branch.ps1 [maven-args...]
# Example: pwsh -File .\scripts\mvn-branch.ps1 clean install
#
# Note: Requires PowerShell 7.x (pwsh) for cross-platform compatibility.
# Falls back to powershell.exe on Windows if pwsh not available.

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"

# Try to get current git branch name
$branch = $null
try {
    $branchOutput = git rev-parse --abbrev-ref HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and $branchOutput) {
        $branch = $branchOutput.Trim()
    }
} catch {
    # Git not available or not in a git repo
}

# Determine buildQualifier
$buildQualifier = ""
if (-not $branch -or $branch -eq "HEAD") {
    # Fallback to git SHA if branch name not available (detached HEAD)
    try {
        $shaOutput = git rev-parse --short HEAD 2>$null
        if ($LASTEXITCODE -eq 0 -and $shaOutput) {
            $sha = $shaOutput.Trim()
            $buildQualifier = "-$sha"
        } else {
            $buildQualifier = "-unknown"
        }
    } catch {
        $buildQualifier = "-unknown"
    }
} else {
    # Sanitize branch name: replace invalid characters with hyphens
    # Maven version format allows: [A-Za-z0-9_.-]
    $sanitized = $branch -replace '[^A-Za-z0-9_.-]', '-' -replace '-+', '-' -replace '^-|-$', ''

    # Skip qualifier for main/master branches (standard behavior)
    if ($sanitized -eq "main" -or $sanitized -eq "master") {
        $buildQualifier = ""
    } else {
        $buildQualifier = "-$sanitized"
    }
}

# Execute Maven with buildQualifier injected
$mavenArgsWithQualifier = @("-DbuildQualifier=$buildQualifier") + $MavenArgs
& mvn $mavenArgsWithQualifier
