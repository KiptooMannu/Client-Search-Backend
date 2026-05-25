@echo off
echo Starting Kazi Konnect Backend Server...

:: Load .env if present (simple KEY=VALUE parser)
if exist "%~dp0.env" (
	for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env") do (
		if not "%%A"=="" set "%%A=%%B"
	)
)

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
