@echo off
echo ========================================
echo   BayBIS Test Server
echo ========================================
echo.
echo Starting Python server on port 8000...
echo Open http://localhost:8000/index.html in your browser
echo.
echo Press Ctrl+C to stop the server
echo.

cd /d "%~dp0"
python server.py

pause
