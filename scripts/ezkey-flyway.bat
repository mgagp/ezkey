@echo off
setlocal enabledelayedexpansion

echo Ezkey Flyway Migration Tool (Simple)
echo ====================================

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
set CORE_DIR=%SCRIPT_DIR%..\ezkey-core

REM Check if ezkey-core directory exists
if not exist "%CORE_DIR%" (
    echo Error: ezkey-core directory not found at %CORE_DIR%
    exit /b 1
)

REM Change to the core directory
cd /d "%CORE_DIR%"

REM Use Maven exec plugin to run the application
if "%1"=="" (
    echo Running default migration...
    mvn exec:java -Dexec.mainClass="org.ezkey.core.EzkeyCoreApp" -q
) else (
    echo Running Flyway command: %*
    mvn exec:java -Dexec.mainClass="org.ezkey.core.EzkeyCoreApp" -Dexec.args="%*" -q
)

echo Done!
endlocal
