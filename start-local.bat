@echo off
setlocal enabledelayedexpansion
echo.
echo ============================================================
echo Kazi Konnect Backend Server - LOCAL MODE
echo ============================================================
echo.

:: Load .env.local file first, then override with any environment variables
if exist "%~dp0.env.local" (
	echo Loading local database configuration from .env.local...
	for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env.local") do (
		set "line=%%A"
		if not "!line!"=="" if not "!line:~0,1!"=="#" (
			set "%%A=%%B"
		)
	)
)

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
echo.
echo Database: Local PostgreSQL (Docker)
echo URL: %DB_URL%
echo User: %DB_USERNAME%
echo.
echo === IMPORTANT ===
echo Make sure PostgreSQL is running with Docker:
echo   docker-compose up -d
echo.
echo After the server starts, access the backend at:
echo   http://localhost:8080
echo ================
echo.

:: Convert .env variables to Spring Boot environment variable format (with underscores)
set SPRING_DATASOURCE_URL=%DB_URL%
set SPRING_DATASOURCE_USERNAME=%DB_USERNAME%
set SPRING_DATASOURCE_PASSWORD=%DB_PASSWORD%
set APP_JWT_SECRET=%JWT_SECRET%
set APP_JWT_EXPIRATION=%JWT_EXPIRATION%
set APP_JWT_REFRESHEXPIRATION=%JWT_REFRESH_EXPIRATION%
set CLOUDINARY_CLOUD_NAME=%CLOUDINARY_CLOUD_NAME%
set CLOUDINARY_API_KEY=%CLOUDINARY_API_KEY%
set CLOUDINARY_API_SECRET=%CLOUDINARY_API_SECRET%
set EMAIL_SMTP_USERNAME=%EMAIL_SMTP_USERNAME%
set EMAIL_SMTP_PASSWORD=%EMAIL_SMTP_PASSWORD%
set EMAIL_FROM=%EMAIL_FROM%
set FRONTEND_URL=%FRONTEND_URL%

C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run

if %ERRORLEVEL% NEQ 0 (
	echo.
	echo ERROR: Backend startup failed!
	echo.
	echo Common issues:
	echo 1. PostgreSQL not running - run: docker-compose up -d
	echo 2. Port 5432 already in use
	echo 3. Docker not installed
	echo.
	pause
)
