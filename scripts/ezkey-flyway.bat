@echo off
setlocal enabledelayedexpansion

REM Ezkey Flyway Migration Tool
echo Ezkey Flyway Migration Tool
echo ===========================

REM Get the directory where this script is located
set SCRIPT_DIR=%~dp0
set CORE_DIR=%SCRIPT_DIR%ezkey-core

REM Check if ezkey-core directory exists
if not exist "%CORE_DIR%" (
    echo Error: ezkey-core directory not found at %CORE_DIR%
    exit /b 1
)

REM Change to the core directory
cd /d "%CORE_DIR%"

REM Check if target directory exists, if not build the project
if not exist "target\classes" (
    echo Building ezkey-core project...
    call mvn clean compile
    if errorlevel 1 (
        echo Error: Failed to build ezkey-core project
        exit /b 1
    )
)

REM Build classpath with Maven dependencies
echo Building classpath...
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt -q
if errorlevel 1 (
    echo Error: Failed to build classpath
    exit /b 1
)

REM Set Java classpath
set CLASSPATH=target\classes
if exist classpath.txt (
    set /p MAVEN_CLASSPATH=<classpath.txt
    set CLASSPATH=!CLASSPATH!;!MAVEN_CLASSPATH!
    del classpath.txt
)

REM Run the application with the provided arguments
if "%1"=="" (
    echo Running default migration...
    java -cp "%CLASSPATH%" org.ezkey.core.EzkeyCoreApp
) else (
    echo Running Flyway command: %*
    java -cp "%CLASSPATH%" org.ezkey.core.EzkeyCoreApp %*
)

endlocal 