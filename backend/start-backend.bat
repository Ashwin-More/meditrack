@echo off
echo ============================================
echo  MediTrack Backend - Starting
echo ============================================
echo.

if "%DB_USER%"=="" set DB_USER=root
if "%DB_HOST%"=="" set DB_HOST=localhost

if "%DB_PASSWORD%"=="" (
    set /p DB_PASSWORD="Enter your MySQL root password: "
)

echo Email: links are printed in this window unless you set MAIL_ENABLED=true + MAIL_USERNAME + MAIL_PASSWORD.
echo.

echo Connecting to MySQL as %DB_USER%@%DB_HOST% ...
echo.

call mvn spring-boot:run

pause
