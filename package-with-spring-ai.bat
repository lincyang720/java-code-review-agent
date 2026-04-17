@echo off
chcp 65001 >nul
echo ==========================================
echo Java Code Review Agent - 打包脚本
echo ==========================================
echo.
echo 使用本地配置，绕过内网仓库...
echo.

mvn clean package -DskipTests -s local-settings.xml

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo 打包成功！
    echo ==========================================
    echo.
    echo JAR 文件位置: target\java-code-review-agent-1.0.0.jar
    echo.
    echo 运行方式:
    echo   java -jar target\java-code-review-agent-1.0.0.jar
    echo.
    echo 或者直接运行:
    echo   run.bat
) else (
    echo.
    echo ==========================================
    echo 打包失败！
    echo ==========================================
    exit /b 1
)
