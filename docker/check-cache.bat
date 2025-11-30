@echo off
REM Ezkey Docker Cache Validation Script for Windows
REM This script helps validate that BuildKit cache is working correctly
REM Usage: check-cache.bat

echo ========================================
echo BuildKit Cache Validation
echo ========================================
echo.
echo This script helps you verify that BuildKit cache is working.
echo.
echo To validate the cache:
echo 1. Run a first build: docker\start.bat minimal
echo 2. Note the build time (especially dependency download time)
echo 3. Run a second build: docker\start.bat minimal
echo 4. Compare the times - the second build should be MUCH faster
echo.
echo Expected behavior:
echo - First build: Downloads all dependencies (slow)
echo - Second build: Uses cached dependencies (fast)
echo.
echo Checking BuildKit status...
docker buildx version >nul 2>&1
if errorlevel 1 (
    echo [31mWARNING: BuildKit may not be available[0m
    echo Make sure Docker Desktop is running and BuildKit is enabled
) else (
    echo [32mBuildKit is available[0m
)
echo.
echo Checking Docker BuildKit cache...
echo Note: BuildKit cache is managed internally and not visible as a Docker volume
echo However, you can verify it's working by comparing build times.
echo.
echo To clear BuildKit cache (if needed):
echo   docker builder prune -a
echo.
pause

