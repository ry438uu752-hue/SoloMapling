@echo off
setlocal
cd /d "%~dp0.."

if exist "jdk-21\bin\java.exe" (
    set "JAVA_HOME=%CD%\jdk-21"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

call mvnw.cmd -q -DskipTests package
if errorlevel 1 exit /b %errorlevel%

if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java.exe" -cp "target\Cosmic.jar" database.DatabaseMigrationRunner
) else (
    java -cp "target\Cosmic.jar" database.DatabaseMigrationRunner
)
exit /b %errorlevel%
