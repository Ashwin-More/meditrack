@echo off
echo ============================================
echo  MediTrack Backend - Starting
echo ============================================
echo.

if "%DB_USER%"=="" set DB_USER=root
if "%DB_HOST%"=="" set DB_HOST=localhost

if exist "..\.env" (
    echo Reading settings from ..\.env  ^(DB, JWT, email/OTP^)
) else (
    if "%DB_PASSWORD%"=="" set /p DB_PASSWORD="Enter your MySQL root password: "
)

if not exist "..\.env" if "%JWT_SECRET%"=="" (
    set JWT_SECRET=dev-only-change-this-secret-please-32chars-min
    echo Using a default JWT_SECRET for local dev. Set your own via:
    echo   set JWT_SECRET=your-long-random-string
    echo.
)

echo Email: OTP codes are emailed when ..\.env has MAIL_ENABLED=true + MAIL_USERNAME + MAIL_PASSWORD; otherwise they print in this window.
echo.

echo Connecting to MySQL as %DB_USER%@%DB_HOST% ...
echo.

call mvn spring-boot:run

pause
