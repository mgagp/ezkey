@echo off
setlocal

set "REPO_ROOT=%~dp0.."
set "GIT_BASH=C:\Program Files\Git\bin\bash.exe"
set "JAVA_HOME=C:\Tools\jdk25"
set "MAVEN_HOME=C:\Tools\apache-maven"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

if not exist "%GIT_BASH%" (
  echo Git Bash not found at "%GIT_BASH%".
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JDK 25 not found at "%JAVA_HOME%".
  exit /b 1
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Maven not found at "%MAVEN_HOME%".
  exit /b 1
)

pushd "%REPO_ROOT%"

echo Build environment diagnostics
echo   repo_root=%CD%
echo   git_bash=%GIT_BASH%
echo   java_home=%JAVA_HOME%

where java
where javac
where mvn
java -version
javac -version
mvn -version

"%GIT_BASH%" -lc "cd \"$(cygpath -u \"%CD%\")\" && ./scripts/build.sh %*"
set "EXIT_CODE=%ERRORLEVEL%"

popd
exit /b %EXIT_CODE%
