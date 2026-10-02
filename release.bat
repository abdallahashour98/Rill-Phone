@echo off
chcp 65001 >nul
title Rill-Phone Release ^& Fast Update Tool
echo ================================================================
echo   Rill-Phone - One-Click Fast Automated Release ^& Update
echo ================================================================
python tools\release_update.py %*
pause
