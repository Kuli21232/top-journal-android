@echo off
title TopJournal AI Server
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\server\start.ps1"
if errorlevel 1 (
  echo.
  echo Server could not start. Run server\setup.ps1 and try again.
  pause
)
