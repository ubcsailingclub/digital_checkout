@echo off
cd /d D:\digitalcheckout\backend
echo [%date% %time%] Starting member sync >> D:\digitalcheckout\backend\sync.log
.venv\Scripts\python.exe scripts\sync_members.py >> D:\digitalcheckout\backend\sync.log 2>&1
echo [%date% %time%] Sync finished >> D:\digitalcheckout\backend\sync.log
