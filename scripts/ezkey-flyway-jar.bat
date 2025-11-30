@echo off
setlocal enabledelayedexpansion

echo Ezkey Flyway Migration Tool (JAR Mode)
echo =======================================

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

REM Build the migration JAR if it doesn't exist
set MIGRATION_JAR=target\ezkey-migration.jar
if not exist "%MIGRATION_JAR%" (
    echo Building migration JAR...
    mvn clean package -Pmigration-jar -q
)

REM Run the migration JAR directly
if "%1"=="" (
    echo Running default migration...
    java -jar "%MIGRATION_JAR%"
) else (
    echo Running Flyway command: %*
    java -jar "%MIGRATION_JAR%" %*
)

echo Done!
endlocal
