# ============================================
# Java Code Review Agent Docker 镜像
# ============================================

# 使用 Eclipse Temurin JDK 17 基础镜像
FROM eclipse-temurin:17-jdk-alpine

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache curl

# 复制 Maven 构建的 JAR 文件
COPY target/*.jar app.jar

# 暴露应用端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/webhook/health || exit 1

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
