@echo off
chcp 65001
title MapleStory Server Runner (5GB + Auto GC)

cd /d "C:\Users\ry438\AppData\Local\Temp\opencode\SoloMapling-main"

"C:\Users\ry438\Downloads\SoloMapling-main\SoloMapling-main\jdk-21\bin\java.exe" ^
-Xms2G ^
-Xmx7G ^
-Duser.language=zh ^
-Duser.country=TW ^
-Dwz-path=wz ^
-Dfile.encoding=BIG5 ^
-Dsun.jnu.encoding=UTF-8 ^
-XX:+UseG1GC ^
-XX:MaxGCPauseMillis=50 ^
-XX:G1ReservePercent=15 ^
-XX:InitiatingHeapOccupancyPercent=45 ^
-XX:+ParallelRefProcEnabled ^
-jar "C:\Users\ry438\AppData\Local\Temp\opencode\SoloMapling-main\target\Cosmic.jar"

pause
