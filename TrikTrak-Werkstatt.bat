@echo off
rem TrikTrak Werkstatt: das Laptop-Programm. Doppelklick startet es und oeffnet die Seite im Browser.
rem Dieses Fenster offen lassen, solange du arbeitest. Schliessen beendet das Programm.
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0"
if not exist "werkstatt\build\install\werkstatt\lib" call gradlew.bat --no-daemon -q :werkstatt:installDist
"%JAVA_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "werkstatt\build\install\werkstatt\lib\*" show.jangregersen.werkstatt.MainKt %*
rem Fenster nur bei einem Fehler offen lassen, damit man ihn lesen kann.
if errorlevel 1 pause
