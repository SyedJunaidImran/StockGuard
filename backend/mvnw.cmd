@REM ----------------------------------------------------------------------------
@REM Maven Wrapper Batch Script for Windows
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%"=="" @ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

SET "BASE_DIR=%~dp0"
IF "%BASE_DIR:~-1%"=="\" SET "BASE_DIR=%BASE_DIR:~0,-1%"

SET "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Error: %WRAPPER_JAR% not found.
  EXIT /B 1
)

IF NOT DEFINED JAVA_HOME (
  FOR %%I IN (java.exe) DO SET "JAVACMD=%%~$PATH:I"
) ELSE (
  SET "JAVACMD=%JAVA_HOME%\bin\java.exe"
)

IF NOT EXIST "%JAVACMD%" (
  ECHO Error: JAVA_HOME is not set and no 'java' command could be found in your PATH.
  EXIT /B 1
)

"%JAVACMD%" -cp "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" org.apache.maven.wrapper.MavenWrapperMain %*
EXIT /B %ERRORLEVEL%
