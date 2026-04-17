# 部署说明

## 环境变量配置方式

### 方式一：.env 文件（推荐开发环境）

在项目根目录创建 `.env` 文件：

```env
OPENAI_API_KEY=your_openai_api_key_here
```

### 方式二：系统环境变量（推荐生产环境）

```bash
# Windows
set OPENAI_API_KEY=your_openai_api_key_here

# Linux/Mac
export OPENAI_API_KEY=your_openai_api_key_here
```

### 方式三：application.yml（不推荐，会提交到 Git）

```yaml
spring:
  ai:
    openai:
      api-key: your_key_here
```

## JAR 包部署

### 1. 打包

```bash
# 使用本地配置打包（绕过内网仓库）
.\package-with-spring-ai.bat
```

### 2. 部署目录结构

```
deploy/
├── java-code-review-agent-1.0.0.jar    # JAR 包
├── .env                                 # 环境变量配置文件
└── run.bat                              # 启动脚本（可选）
```

### 3. 运行

```bash
# 方式 1: 直接运行（确保 .env 在同一目录）
cd deploy
java -jar java-code-review-agent-1.0.0.jar

# 方式 2: 使用脚本
.\run.bat

# 方式 3: 带环境变量运行
set OPENAI_API_KEY=your_key
java -jar java-code-review-agent-1.0.0.jar
```

## 配置加载优先级

1. 系统环境变量（`System.getenv()`）
2. 系统属性（`System.getProperty()`）
3. application.yml 中的配置
4. .env 文件中的配置（最低优先级）

**注意**: 高优先级的配置会覆盖低优先级的配置。

## 常见问题

### Q: JAR 包运行时提示 API Key 为空

**原因**: .env 文件不在 JAR 所在目录

**解决**: 
1. 将 .env 文件放在 JAR 包同一目录
2. 或使用系统环境变量：`set OPENAI_API_KEY=xxx`

### Q: 内网仓库下载失败

**解决**: 使用 `package-with-spring-ai.bat` 脚本，它会使用 `local-settings.xml` 从公共仓库下载依赖。

### Q: 如何查看当前加载的配置

启动时会输出日志：
```
[INFO] 已加载 .env 文件: C:\deploy\.env
```

如果没有加载 .env，会显示：
```
[WARN] 未找到 .env 文件，将使用系统环境变量或 application.yml 配置
```
