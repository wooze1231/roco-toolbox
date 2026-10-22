@echo off
chcp 65001 >nul
cd /d "%~dp0"
python merchant_reminder.py
if errorlevel 1 (
  echo.
  echo 运行失败：请确认已安装 Python 并加入 PATH。
  echo 可前往 https://www.python.org/downloads/ 安装，记得勾选 "Add python.exe to PATH"。
  echo 安装后用「 python merchant_reminder.py 」即可运行本脚本。
  pause
)
