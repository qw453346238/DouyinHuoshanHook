@echo off
echo ========================================
echo 使用自定义 Gradle 缓存目录编译
echo ========================================

REM 设置 Gradle 用户目录到 D 盘（无中文路径）
set GRADLE_USER_HOME=D:\GradleCache
set ANDROID_SDK_ROOT=C:\admin

echo Gradle 缓存目录: %GRADLE_USER_HOME%
echo Android SDK: %ANDROID_SDK_ROOT%
echo.

REM 创建目录（如果不存在）
if not exist "%GRADLE_USER_HOME%" mkdir "%GRADLE_USER_HOME%"

echo 开始编译...
echo.

REM 清理并编译
call gradlew.bat clean assembleDebug --no-daemon

echo.
echo ========================================
echo 编译完成！
echo ========================================
echo APK 位置: app\build\outputs\apk\debug\app-debug.apk
echo.
pause
