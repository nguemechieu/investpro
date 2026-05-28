@echo off
setlocal

REM Run InvestPro with the local Java 21 JDK and Maven-copied dependencies.
set "APP_HOME=%~dp0"
set "JAVA_HOME=C:\Users\nguem\.jdks\ms-21.0.11"

if not exist "%APP_HOME%target\classes\org\investpro\InvestPro.class" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
)

if not exist "%APP_HOME%target\lib" (
  call "%APP_HOME%mvnw.cmd" -DskipTests package
)

"%JAVA_HOME%\bin\java.exe" ^
  -cp "%APP_HOME%target\classes;%APP_HOME%target\lib\*" ^
  org.investpro.InvestProLauncher

endlocal
