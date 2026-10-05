@echo off
echo ============================================
echo  MediTrack Frontend - Starting on port 5500
echo ============================================
echo.
echo Once it starts, open: http://localhost:5500
echo (Press Ctrl+C to stop)
echo.

python -m http.server 5500

pause
