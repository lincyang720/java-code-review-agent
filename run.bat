@echo off
chcp 65001 >nul
echo ==========================================
echo Java Code Review Agent - 启动脚本
echo ==========================================
echo.

set JAR_FILE=target\java-code-review-agent-1.0.0.jar

if not exist %JAR_FILE% (
    echo 错误: 找不到 JAR 文件 %JAR_FILE%
    echo 请先运行 package-with-spring-ai.bat 进行打包
    exit /b 1
)

echo 正在启动 Code Review Agent...
echo.

:: 检查 .env 文件是否存在
if exist .env (
    echo [INFO] 发现 .env 文件，将加载环境变量
) else (
    echo [WARN] 未找到 .env 文件，请确保环境变量已设置
    echo [INFO] 可通过以下方式设置: set OPENAI_API_KEY=your_key
)

:: 启动应用
cd %~dp0
java -jar %JAR_FILE%
