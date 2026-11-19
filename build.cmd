@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "_JAVA_OPTIONS="
set "GRADLE_OPTS=-Dorg.gradle.jvmargs=-Xmx2G -XX:MaxMetaspaceSize=512m"
cd /d "%~dp0"
call gradlew.bat %* --no-daemon --console=plain
