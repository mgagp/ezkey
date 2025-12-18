# Measure Native Image Memory Usage (PowerShell)
# 
# This script helps measure actual memory usage (RSS) of native Spring Boot images
# Usage: .\scripts\measure-native-memory.ps1 [container-name] [port]

param(
    [string]$ContainerName = "ezkey-auth-api-native",
    [int]$Port = 8080
)

$BaseUrl = "http://localhost:$Port"

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Native Image Memory Measurement" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Container: $ContainerName"
Write-Host "Port: $Port"
Write-Host ""

# Check if container is running
$containerExists = docker ps --format '{{.Names}}' | Select-String -Pattern "^${ContainerName}$"
if (-not $containerExists) {
    Write-Host "❌ Error: Container '$ContainerName' is not running" -ForegroundColor Red
    Write-Host "   Start it first: docker compose -f docker/docker-compose.native.yml up -d auth-api" -ForegroundColor Yellow
    exit 1
}

Write-Host "📊 Docker Container Stats:" -ForegroundColor Green
Write-Host "---------------------------"
docker stats $ContainerName --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}"
Write-Host ""

# Check if Actuator is available
Write-Host "🔍 Checking Actuator Endpoints:" -ForegroundColor Green
Write-Host "---------------------------"
try {
    $healthResponse = Invoke-WebRequest -Uri "${BaseUrl}/actuator/health" -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ Actuator is available" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "📈 Memory Metrics (via Actuator):" -ForegroundColor Green
    Write-Host "---------------------------"
    
    # Get process uptime
    try {
        $uptimeResponse = Invoke-RestMethod -Uri "${BaseUrl}/actuator/metrics/process.uptime" -ErrorAction Stop
        $uptime = $uptimeResponse.measurements[0].value
        if ($uptime) {
            Write-Host "Process Uptime: $uptime seconds" -ForegroundColor Cyan
        }
    } catch {
        Write-Host "ℹ️  Process uptime not available" -ForegroundColor Yellow
    }
    
    # List available metrics
    try {
        $metricsResponse = Invoke-RestMethod -Uri "${BaseUrl}/actuator/metrics" -ErrorAction Stop
        Write-Host ""
        Write-Host "📋 Available Memory-Related Metrics:" -ForegroundColor Green
        $metricsResponse.names | Where-Object { $_ -like "*memory*" -or $_ -like "*jvm*" } | Select-Object -First 10 | ForEach-Object {
            Write-Host "  - $_" -ForegroundColor Cyan
        }
    } catch {
        Write-Host "⚠️  Could not list metrics" -ForegroundColor Yellow
    }
    
    Write-Host ""
} catch {
    Write-Host "⚠️  Actuator not available or not responding" -ForegroundColor Yellow
    Write-Host "   Make sure actuator dependency is added and endpoints are exposed" -ForegroundColor Yellow
    Write-Host ""
}

# Try to get RSS from /proc if possible
Write-Host "🔬 Process Memory (RSS) - Direct Inspection:" -ForegroundColor Green
Write-Host "---------------------------"
try {
    $rss = docker exec $ContainerName sh -c "cat /proc/self/status 2>/dev/null | grep VmRSS" 2>$null
    if ($rss) {
        Write-Host "✅ Got RSS from /proc:" -ForegroundColor Green
        Write-Host $rss -ForegroundColor Cyan
    } else {
        Write-Host "⚠️  Cannot access /proc (distroless image, no shell)" -ForegroundColor Yellow
        Write-Host "   Use Actuator metrics above or create debug image with shell" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "   To create debug image:" -ForegroundColor Yellow
        Write-Host "   docker build -f ezkey-auth-api/Dockerfile.debug -t ezkey-auth-api-native-debug ." -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠️  Cannot access /proc (distroless image, no shell)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "💡 Tips:" -ForegroundColor Cyan
Write-Host "  - Docker stats shows allocated memory, not actual RSS"
Write-Host "  - Actuator metrics show process memory from application perspective"
Write-Host "  - For true RSS, use debug image with shell access to /proc"
Write-Host "  - Compare with JVM version: docker stats ezkey-auth-api (JVM container)"
Write-Host ""
