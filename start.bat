@echo off
setlocal enabledelayedexpansion
echo.
echo ============================================================
echo Kazi Konnect Backend Server
echo ============================================================
echo.

:: Check for command-line argument to select database
set DB_MODE=%1
if "!DB_MODE!"=="" set DB_MODE=neon
if "!DB_MODE!"=="h2" (
	echo [H2 Mode] Starting with in-memory H2 database...
	set MAVEN_PROFILE=-Ph2
) else (
	echo [Neon Mode] Starting with cloud PostgreSQL database...
	set MAVEN_PROFILE=
)

:: Load .env if present (skip comments and empty lines)
:: Fixed: assign via intermediate "key"/"val" vars and expand with ! ! (delayed
:: expansion) inside a fully quoted set statement. This makes set treat
:: characters like & | < > ^ in values as literal text instead of letting
:: cmd.exe parse them as command separators/redirection.
if exist "%~dp0.env" (
	echo Loading environment variables from .env file...
	for /f "usebackq tokens=1* delims==" %%A in ("%~dp0.env") do (
		set "key=%%A"
		set "val=%%B"
		if not "!key!"=="" if not "!key:~0,1!"=="#" (
			set "!key!=!val!"
			echo   - Loaded: !key!
		)
	)
)

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot
echo.
if "!DB_MODE!"=="h2" (
	echo Database: H2 In-Memory
	echo URL: jdbc:h2:mem:testdb
	echo Console: http://localhost:8080/h2-console
) else (
	echo Database: Neon PostgreSQL (Cloud)
	echo URL: !DB_URL!
)
echo.

:: Convert .env variables to Spring Boot environment variable format (with underscores)
:: Use delayed expansion (! !) here too, consistent with how the values were loaded above.
set "SPRING_DATASOURCE_URL=!DB_URL!"
set "SPRING_DATASOURCE_USERNAME=!DB_USERNAME!"
set "SPRING_DATASOURCE_PASSWORD=!DB_PASSWORD!"
set "APP_JWT_SECRET=!JWT_SECRET!"
set "APP_JWT_EXPIRATION=!JWT_EXPIRATION!"
set "APP_JWT_REFRESHEXPIRATION=!JWT_REFRESH_EXPIRATION!"
set "CLOUDINARY_CLOUD_NAME=!CLOUDINARY_CLOUD_NAME!"
set "CLOUDINARY_API_KEY=!CLOUDINARY_API_KEY!"
set "CLOUDINARY_API_SECRET=!CLOUDINARY_API_SECRET!"
set "EMAIL_SMTP_USERNAME=!EMAIL_SMTP_USERNAME!"
set "EMAIL_SMTP_PASSWORD=!EMAIL_SMTP_PASSWORD!"
set "EMAIL_FROM=!EMAIL_FROM!"
set "FRONTEND_URL=!FRONTEND_URL!"

if "!DB_MODE!"=="h2" (
	C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=h2"
) else (
	C:\Users\User\maven\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
)

if %ERRORLEVEL% NEQ 0 (
	echo.
	echo ERROR: Backend startup failed!
	echo Try running with H2 in-memory database instead:
	echo   .\start.bat h2
	echo.
	pause
)