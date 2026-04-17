#!/bin/bash
# ============================================
# Java Code Review Agent 启动脚本 (Linux/Mac)
# ============================================

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║     Java Code Review Agent 启动器                        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# 检查 .env 文件
if [ ! -f ".env" ]; then
    echo "[警告] 未找到 .env 文件，使用 .env.example 创建..."
    if [ -f ".env.example" ]; then
        cp .env.example .env
        echo "[提示] 请编辑 .env 文件填写你的 API Key 后再启动"
        exit 1
    else
        echo "[错误] 未找到 .env.example 文件"
        exit 1
    fi
fi

# 加载 .env 文件
export $(grep -v '^#' .env | xargs)

# 检查 JAR 文件
JAR_FILE="target/java-code-review-agent-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "[信息] 未找到 JAR 文件，开始编译..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "[错误] 编译失败"
        exit 1
    fi
fi

echo "[信息] 启动 Java Code Review Agent..."
echo "[信息] 配置文件: .env"
echo ""

# 启动应用
java -jar "$JAR_FILE"
