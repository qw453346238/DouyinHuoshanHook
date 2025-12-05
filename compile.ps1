# 设置环境变量
$env:GRADLE_USER_HOME = "D:\GradleCache"
$env:ANDROID_SDK_ROOT = "C:\admin"

Write-Host "========================================" -ForegroundColor Green
Write-Host "开始编译 XposedHookModule" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Gradle 缓存目录: $env:GRADLE_USER_HOME"
Write-Host "Android SDK: $env:ANDROID_SDK_ROOT"
Write-Host ""

# 执行编译
& .\gradlew.bat assembleDebug --stacktrace

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "编译成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "APK 位置: app\build\outputs\apk\debug\app-debug.apk"
    
    # 复制到 Downloads
    Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "C:\Users\大哥大\Downloads\DouyinHook.apk" -Force
    Write-Host "已复制到: C:\Users\大哥大\Downloads\DouyinHook.apk" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "编译失败！" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}

Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
