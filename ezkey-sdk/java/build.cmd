@echo off
REM Ezkey Java SDK Build Script for Windows
REM This script builds the Java SDK and demo application

echo === Building Ezkey Java SDK ===
echo.

REM Check if Maven is available
where mvn >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Maven is not installed or not in PATH
    echo Please install Maven 3.6+ to build the Java SDK
    exit /b 1
)

REM Check Java version  
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 8+ to build the Java SDK
    exit /b 1
)

echo ✓ Prerequisites check passed
echo.

REM Build the SDK
echo Building SDK...
call mvn clean compile

if %errorlevel% neq 0 (
    echo ✗ SDK compilation failed
    exit /b 1
)
echo ✓ SDK compiled successfully

REM Package the SDK
echo Packaging SDK...
call mvn package

if %errorlevel% neq 0 (
    echo ✗ SDK packaging failed
    exit /b 1
)
echo ✓ SDK packaged successfully
echo ✓ JAR file created: target\ezkey-java-sdk-1.0.0.jar

REM Compile demo application
echo Compiling demo application...
if not exist target\demo-classes mkdir target\demo-classes

REM Get classpath with dependencies (simplified for Windows)
for /f "delims=" %%i in ('mvn dependency:build-classpath -Dmdep.outputFile=-Dsilent') do set CLASSPATH=%%i
set CLASSPATH=target\classes;target\generated-sources\admin-api\target\classes;target\generated-sources\auth-api\target\classes;%CLASSPATH%

javac -cp "%CLASSPATH%" -d target\demo-classes demo\EzkeyDemoApplication.java

if %errorlevel% neq 0 (
    echo ✗ Demo application compilation failed
    exit /b 1
)
echo ✓ Demo application compiled successfully

echo.
echo === Build completed successfully! ===
echo.
echo To run the demo application:
echo   run-demo.cmd
echo.
echo To use the SDK in your project:
echo   Add target\ezkey-java-sdk-1.0.0.jar to your classpath
echo.