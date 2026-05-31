@REM Maven Wrapper script
@REM https://maven.apache.org/wrapper/

@echo off
set WRAPPER_JAR="%~dp0\.mvn\wrapper\maven-wrapper.jar"

if not "%JAVA_HOME%"=="" goto useJavaHome
set JAVACMD=java
goto run

:useJavaHome
set JAVACMD="%JAVA_HOME%\bin\java"

:run
%JAVACMD% %MAVEN_OPTS% -jar %WRAPPER_JAR% %*
