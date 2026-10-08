@echo off
setlocal
title ATMyTrack Web
set "ATMYTRACK_PYTHON=%LOCALAPPDATA%\Programs\Python\Python310\python.exe"
if not exist "%ATMYTRACK_PYTHON%" set "ATMYTRACK_PYTHON=python"
"%ATMYTRACK_PYTHON%" "%~dp0abrir_web.py" %*
if errorlevel 1 (
    echo Nao foi possivel abrir o ATMyTrack. Verifique o Python e a pasta dist.
    pause
    exit /b 1
)
exit /b 0
