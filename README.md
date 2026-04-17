# Java Code Review Agent

基于 **Spring AI + LangChain4j** 的智能代码审查 Agent，支持 GitHub/GitLab Webhook 集成，自动对 Pull Request / Merge Request 进行代码审查。

## 🎯 功能特性

| 功能 | 说明 |
|:---|:---|
| **自动 PR 审查** | 接收 Webhook 事件，自动触发代码审查 |
| **Bug 检测** | 空指针、并发安全、资源泄漏、SQL 注入 |
| **代码质量** | 重复代码、圈复杂度、命名规范、代码风格 |
| **安全扫描** | XSS、SQL 注入、反序列化、敏感信息泄露 |
| **双 AI 框架** | Spring AI + LangChain4j，灵活选择 |
| **本地规则** | 基于 JavaParser 的静态分析，零成本 |
| **智能降级** | 本地发现问题多时跳过 AI，节省成本 |
| **阿里巴巴规范** | 集成 P3C-PMD，真正的阿里巴巴 Java 开发规范 |
| **多渠道检测** | 可选集成 SonarQube、SpotBugs，无需安装默认可用 |

## 📊 效果对比

```
使用前（纯人工审查）：
├── 平均审查时间：1.5 小时/PR
├── 日均审查能力：8-10 个 PR
└── Bug 发现率：约 60%

使用后（AI + 人工复审）：
├── AI 预审时间：3-5 分钟/PR
├── 人工复审时间：15-20 分钟/PR
├── 日均审查能力：30+ 个 PR
└── Bug 发现率：约 85%

效率提升：审查时间减少 75%
```

## 🚀 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.8+
- OpenAI API Key
- GitLab/GitHub Token

### 2. 克隆项目

```bash
git clone https://github.com/your-username/java-code-review-agent.git
cd java-code-review-agent
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，填写你的 API Key
```

`.env` 文件内容：

```env
# DeepSeek 配置（推荐，国内访问，性价比高）
DEEPSEEK_API_KEY=your_deepseek_api_key
DEEPSEEK_MODEL=deepseek-chat

# GitLab 配置（公司内网）
GITLAB_URL=http://192.168.1.151
GITLAB_TOKEN=your_gitlab_token

# GitHub 配置
GITHUB_TOKEN=your_github_token
```

> 💡 **提示**：项目默认使用 DeepSeek API（国内访问，性价比高）。如需使用 OpenAI，请配置 `OPENAI_API_KEY` 并修改 `DEEPSEEK_BASE_URL`。

### 4. 构建运行

**方式一：直接运行**

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/java-code-review-agent-1.0.0.jar
```

**方式二：Docker 运行**

```bash
# 构建镜像
docker-compose build

# 启动服务
docker-compose up -d
```

### 5. 配置 Webhook

**GitLab 配置：**

1. 进入项目 -> Settings -> Webhooks
2. URL: `http://your-server:8080/webhook/gitlab`
3. Secret Token: （可选，用于验证）
4. 触发事件：勾选 "Merge request events"
5. 保存

**GitHub 配置：**

1. 进入项目 -> Settings -> Webhooks
2. Payload URL: `http://your-server:8080/webhook/github`
3. Content type: `application/json`
4. 触发事件：选择 "Pull requests"
5. 保存

## 📁 项目结构

```
java-code-review-agent/
├── pom.xml                          # Maven 配置
├── Dockerfile                       # Docker 镜像
├── docker-compose.yml               # Docker Compose 配置
├── .env.example                     # 环境变量示例
├── README.md                        # 项目说明
│
├── src/
│   ├── main/
│   │   ├── java/com/aicode/review/
│   │   │   ├── Application.java           # 启动类
│   │   │   ├── config/
│   │   │   │   ├── SpringAIConfig.java    # Spring AI 配置
│   │   │   │   └── LangChain4jConfig.java # LangChain4j 配置
│   │   │   ├── controller/
│   │   │   │   └── WebhookController.java # Webhook 接收
│   │   │   ├── service/
│   │   │   │   ├── CodeReviewService.java # 审查服务
│   │   │   │   ├── GitService.java        # Git 服务
│   │   │   │   └── PromptService.java     # 提示词服务
│   │   │   ├── agent/
│   │   │   │   ├── ReviewAgent.java       # AI Agent
│   │   │   │   ├── ReviewTools.java       # 工具定义
│   │   │   │   └── ReviewMemory.java      # 记忆管理
│   │   │   ├── model/
│   │   │   │   ├── Issue.java             # 问题模型
│   │   │   │   ├── ReviewReport.java      # 审查报告
│   │   │   │   ├── PRDifferences.java     # PR 差异
│   │   │   │   └── CommitMessage.java     # 提交信息
│   │   │   ├── client/
│   │   │   │   ├── GitHubClient.java      # GitHub API
│   │   │   │   └── GitLabClient.java      # GitLab API
│   │   │   └── util/
│   │   │       └── CodeParser.java        # 代码解析
│   │   │
│   │   └── resources/
│   │       ├── application.yml            # 应用配置
│   │       └── prompts/
│   │           └── review-prompt.txt      # 审查提示词
│   │
│   └── test/
│       └── java/com/aicode/review/
│           └── CodeReviewServiceTest.java # 单元测试
```

## 🔧 配置说明

### application.yml

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini

langchain4j:
  open-ai:
    api-key: ${OPENAI_API_KEY}
    model-name: gpt-4o-mini

app:
  gitlab:
    url: http://192.168.1.151
    token: ${GITLAB_TOKEN}
  
  # 静态代码分析工具配置（可选）
  analysis:
    # 本地规则检测（阿里巴巴规范、OWASP 安全规则）- 始终启用
    local-rules-enabled: true
    # AI 深度分析
    ai-analysis-enabled: true
    # SonarQube 配置（可选，需要本地服务）
    sonarqube:
      enabled: false  # 默认禁用，如需使用请改为 true
      url: http://localhost:9000
      token: ${SONAR_TOKEN:}
      project-key: ${SONAR_PROJECT_KEY:}
    # SpotBugs 配置（可选，需要本地安装）
    spotbugs:
      enabled: false  # 默认禁用，如需使用请改为 true
      executable-path: spotbugs  # 或指定完整路径如 /usr/local/bin/spotbugs
      effort: default  # min, default, max
      threshold: medium  # high, medium, low, experimental
```

### 环境变量

| 变量 | 说明 | 必需 |
|:---|:---|:---|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥（推荐） | ✅ |
| `DEEPSEEK_MODEL` | 模型名称，默认 deepseek-chat | ❌ |
| `DEEPSEEK_BASE_URL` | API 地址，默认 https://api.deepseek.com/v1 | ❌ |
| `OPENAI_API_KEY` | OpenAI API 密钥（可选，需代理） | ❌ |
| `GITLAB_URL` | GitLab 地址 | ❌ |
| `GITLAB_TOKEN` | GitLab Token | ✅ |
| `GITHUB_TOKEN` | GitHub Token | ❌ |
| `SONAR_TOKEN` | SonarQube Token（可选） | ❌ |
| `SONAR_PROJECT_KEY` | SonarQube 项目 Key（可选） | ❌ |

> 💡 **AI 模型配置说明**：
> - 默认使用 **DeepSeek**（国内访问，性价比高）
> - 如需切换 OpenAI，设置 `DEEPSEEK_BASE_URL=https://api.openai.com/v1` 和 `OPENAI_API_KEY`

## 🧪 测试

```bash
# 运行单元测试
mvn test

# 测试 GitLab 连接
mvn test -Dtest=CodeReviewServiceTest#testGitLabConnection
```

## 📡 API 接口

### Webhook 接收

```
POST /webhook/gitlab    # GitLab Webhook
POST /webhook/github    # GitHub Webhook
```

### 健康检查

```
GET /webhook/health
```

### 手动审查（测试用）

```bash
curl -X POST http://localhost:8080/webhook/manual-review \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Test { ... }",
    "context": "测试代码",
    "repository": "test/repo",
    "prId": "1"
  }'
```

## 🐛 常见问题

### 1. Token 费用过高

**问题：** LLM 调用成本太高

**解决方案：**
- 本地规则先过滤，减少 AI 调用
- 使用 gpt-4o-mini 替代 GPT-4
- 设置 `app.review.local-issue-threshold=10`

### 2. 审查结果不准确

**问题：** AI 给的审查意见太宽泛

**解决方案：**
- 优化 `review-prompt.txt` 中的 Few-shot 示例
- 在提示词中添加项目特定的规范
- 调整 temperature 参数（更低更确定）

### 3. 无法连接公司 GitLab

**问题：** 内网 GitLab 连接失败

**解决方案：**
- 检查网络连通性：`curl http://192.168.1.151`
- 确认 GitLab Token 权限（需要 api 权限）
- 如果使用 Docker，检查容器网络配置

### 4. SonarQube/SpotBugs 如何启用

**问题：** 想使用 SonarQube 或 SpotBugs 进行额外的代码检测

**解决方案：**

**SonarQube（需要本地服务）：**
1. 本地安装 SonarQube 服务（Docker 方式最简单）
2. 在 `application.yml` 中启用：`app.analysis.sonarqube.enabled: true`
3. 配置 SonarQube 地址和 Token
4. 确保项目已在 SonarQube 中配置并分析过

**SpotBugs（需要本地安装）：**
1. 本地安装 SpotBugs（`brew install spotbugs` 或下载安装）
2. 在 `application.yml` 中启用：`app.analysis.spotbugs.enabled: true`
3. 配置可执行文件路径（如 `spotbugs` 或完整路径）
4. 调整 effort 和 threshold 参数控制检测深度

**注意：** 这两个工具默认禁用，不需要安装本地服务即可运行基础代码审查功能。

## 📚 技术栈

- **Java 17**
- **Spring Boot 3.x**
- **LangChain4j** - Agent 框架
- **DeepSeek** - AI 代码审查（默认，国内访问，性价比高）
- **Alibaba P3C** - 阿里巴巴 Java 开发规范（50+ 条规则）
- **JavaParser** - 代码解析
- **Maven** - 构建工具
- **Docker** - 容器化部署

### 阿里巴巴 P3C 规范

项目已集成 **Alibaba P3C-PMD** 规则库，覆盖完整的阿里巴巴 Java 开发手册规范：

| 规范类别 | 规则数量 | 说明 |
|---------|---------|------|
| 命名规范 | 8+ | 类名、方法名、变量名、包名等 |
| 常量定义 | 3+ | 常量命名、魔法数字等 |
| 代码格式 | 5+ | 大括号、行宽、缩进等 |
| OOP 规约 | 10+ | 面向对象设计规范 |
| 集合处理 | 8+ | List、Map、Set 使用规范 |
| 并发处理 | 10+ | 线程池、锁、并发集合等 |
| 控制语句 | 5+ | 条件、循环、异常处理等 |
| 注释规约 | 3+ | 类注释、方法注释等 |
| 其他 | 10+ | 日志、异常、日期等 |

**P3C 规则自动启用**，无需额外配置。

### 可选扩展工具

- **SonarQube** - 企业级代码质量分析（需本地服务）
- **SpotBugs** - 基于字节码的 Bug 检测（需本地安装）

> 💡 **提示**：以上两个工具为可选集成，默认禁用。不安装它们不会影响核心代码审查功能的使用。

### AI 模型支持

项目默认使用 **DeepSeek** 进行代码审查，优势：
- ✅ 国内直接访问，无需代理
- ✅ 代码专项优化，审查质量高
- ✅ 价格仅为 GPT-4 的 1/10

如需切换 OpenAI，修改环境变量 `DEEPSEEK_BASE_URL` 和 `OPENAI_API_KEY` 即可。

## 🤝 贡献指南

1. Fork 项目
2. 创建分支：`git checkout -b feature/xxx`
3. 提交更改：`git commit -m "Add xxx"`
4. 推送分支：`git push origin feature/xxx`
5. 创建 Pull Request

## 📄 许可证

MIT License

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai)
- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [JavaParser](https://github.com/javaparser/javaparser)

---

**如果这个项目对你有帮助，欢迎 Star ⭐**
