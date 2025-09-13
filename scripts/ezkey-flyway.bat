@echo off
setlocal enabledelayedexpansion

echo Ezkey Flyway Migration Tool
echo ===========================

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

REM Check if migration JAR exists, build if needed
set MIGRATION_JAR=target\ezkey-migration.jar
if not exist "%MIGRATION_JAR%" (
    echo Migration JAR not found. Building...
    mvn clean package -Pmigration-jar -q
)

REM Run the migration application
if "%1"=="" (
    echo Running default migration...
    mvn spring-boot:run -q
) else (
    echo Running Flyway command: %*
    mvn spring-boot:run -Dspring-boot.run.arguments="%*" -q
)

echo Done!
endlocal
