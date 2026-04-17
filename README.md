# Java Code Review Agent

基于 **Spring Boot 3.2 + LangChain4j + DeepSeek** 的智能代码审查 Agent，支持 GitHub/GitLab Webhook 集成，自动对 Pull Request / Merge Request 进行多层次代码审查，并通过 WebSocket 实时推送审查进度。

## 功能特性

| 功能 | 说明 |
|:---|:---|
| **自动 PR 审查** | 接收 Webhook 事件，自动触发代码审查并回写评论 |
| **阿里巴巴 P3C 规范** | 集成 P3C-PMD，覆盖命名、并发、集合、注释等 50+ 条规则 |
| **安全漏洞扫描** | 检测 SQL 注入、XSS、硬编码密钥、弱加密等 OWASP Top 10 |
| **Bug 检测** | 空指针、并发安全、资源泄漏、线程不安全用法 |
| **AI 深度审查** | DeepSeek LLM 汇总分析，生成结构化、建设性的审查报告 |
| **实时进度推送** | WebSocket 实时推送审查阶段（扫描中 → AI 分析 → 报告生成） |
| **多格式报告导出** | 支持 HTML、Markdown、JSON、PDF 格式导出 |
| **审查历史管理** | 项目维度的审查历史、质量趋势、统计看板 |
| **可选外部工具** | 可选集成 SonarQube、SpotBugs，不启用不影响核心功能 |
| **智能降级** | 本地发现问题超过阈值时自动跳过 AI，节省 Token 成本 |

## 效果对比

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

## 快速开始

### 1. 环境要求

- Java 17+
- Maven 3.8+
- DeepSeek / OpenAI API Key
- GitLab 或 GitHub Token

### 2. 克隆项目

```bash
git clone https://github.com/lincyang720/java-code-review-agent.git
cd java-code-review-agent
```

### 3. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，填写你的 API Key
```

`.env` 文件内容示例：

```env
# DeepSeek 配置（推荐，国内访问，性价比高）
DEEPSEEK_API_KEY=your_deepseek_api_key
DEEPSEEK_MODEL=deepseek-chat

# GitLab 配置
GITLAB_URL=http://your-gitlab-host
GITLAB_TOKEN=your_gitlab_token
GITLAB_WEBHOOK_TOKEN=your_webhook_secret

# GitHub 配置
GITHUB_TOKEN=your_github_token
GITHUB_WEBHOOK_SECRET=your_webhook_secret
```

> **提示**：项目默认使用 DeepSeek API（国内访问，性价比高）。如需使用 OpenAI，设置 `DEEPSEEK_BASE_URL=https://api.openai.com/v1` 和 `OPENAI_API_KEY` 即可。

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
docker-compose up -d
```

### 5. 配置 Webhook

**GitLab 配置：**

1. 进入项目 → Settings → Webhooks
2. URL: `http://your-server:8080/webhook/gitlab`
3. Secret Token: 填写 `GITLAB_WEBHOOK_TOKEN`
4. 触发事件：勾选 "Merge request events"

**GitHub 配置：**

1. 进入项目 → Settings → Webhooks
2. Payload URL: `http://your-server:8080/webhook/github`
3. Content type: `application/json`
4. Secret: 填写 `GITHUB_WEBHOOK_SECRET`
5. 触发事件：选择 "Pull requests"

## 项目结构

```
java-code-review-agent/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
│
└── src/main/
    ├── java/com/aicode/review/
    │   ├── Application.java
    │   ├── agent/
    │   │   ├── ReviewAgent.java          # AI 审查核心，整合多层检测
    │   │   ├── ReviewTools.java          # JavaParser 静态分析工具
    │   │   └── ReviewMemory.java         # 审查上下文记忆
    │   ├── config/
    │   │   ├── LangChain4jConfig.java    # LLM 模型配置
    │   │   ├── WebSocketConfig.java      # WebSocket 端点配置
    │   │   ├── ReviewProgressWebSocketHandler.java  # 实时进度推送
    │   │   ├── StaticAnalysisConfig.java
    │   │   └── DotEnvConfig.java         # .env 文件加载
    │   ├── controller/
    │   │   ├── WebhookController.java    # Webhook 接收
    │   │   ├── ReviewController.java     # 审查任务与报告接口
    │   │   └── DashboardController.java  # 看板数据接口
    │   ├── service/
    │   │   ├── CodeReviewService.java    # 审查流程编排
    │   │   ├── GitService.java           # GitHub/GitLab API 封装
    │   │   ├── PromptService.java        # 提示词加载与解析
    │   │   ├── P3CAnalysisService.java   # 阿里 P3C 规范检测
    │   │   ├── ReviewHistoryService.java # 审查历史与统计
    │   │   ├── ReviewProgressService.java # 进度消息管理
    │   │   └── ReportExportService.java  # 多格式报告导出
    │   ├── client/
    │   │   ├── GitHubClient.java
    │   │   ├── GitLabClient.java
    │   │   ├── SonarQubeClient.java      # 可选
    │   │   └── SpotBugsRunner.java       # 可选
    │   ├── model/
    │   │   ├── ReviewReport.java
    │   │   ├── Issue.java
    │   │   ├── PRInfo.java
    │   │   ├── PRDifferences.java
    │   │   ├── CommitMessage.java
    │   │   ├── ReviewHistory.java
    │   │   ├── ProjectStats.java
    │   │   └── ReviewProgressMessage.java
    │   └── util/
    │       └── CodeParser.java
    │
    └── resources/
        ├── application.yml
        ├── application-dev.yml
        ├── prompts/
        │   ├── review-prompt.txt         # AI 审查提示词模板
        │   └── commit-prompt.txt         # 提交信息提示词模板
        └── static/
            ├── review-dashboard.html     # 审查任务看板
            └── admin-dashboard.html      # 管理后台
```

## 多层检测架构

```
┌──────────────────────────────────────────────────┐
│                  代码审查流程                      │
├──────────────────────────────────────────────────┤
│  第一层：P3C 阿里巴巴规范（PMD）                   │
│  ├── 命名规范（类名、方法名、变量名）               │
│  ├── 并发处理（线程池、SimpleDateFormat）           │
│  ├── 集合处理（List/Map/Set 规范）                 │
│  ├── 控制语句（大括号、行宽）                      │
│  └── 注释规约（类注释、方法注释）                  │
│                                                  │
│  第二层：自定义规则检测（JavaParser + 正则）        │
│  ├── 空指针检测                                   │
│  ├── 并发安全（线程不安全集合、共享状态）            │
│  ├── 安全漏洞（OWASP Top 10）                     │
│  │   ├── SQL 注入、XSS、路径遍历                  │
│  │   ├── 硬编码密钥、弱加密算法（MD5/SHA1）         │
│  │   ├── 不安全反序列化、命令注入                  │
│  │   └── printStackTrace 日志暴露                │
│  └── 资源泄漏（未关闭的流/连接）                   │
│                                                  │
│  第三层：AI 深度分析（DeepSeek LLM）               │
│  ├── 汇总本地检测结果                             │
│  ├── 业务逻辑 Bug 与设计问题                      │
│  └── 生成结构化、建设性的 Markdown 报告            │
│                                                  │
│  第四层：可选外部工具（需手动启用）                 │
│  ├── SonarQube（企业级质量分析）                  │
│  └── SpotBugs（字节码级 Bug 检测）                │
└──────────────────────────────────────────────────┘
```

## API 接口

### Webhook 接口

| 接口 | 说明 |
|:---|:---|
| `POST /webhook/github` | 接收 GitHub Pull Request 事件 |
| `POST /webhook/gitlab` | 接收 GitLab Merge Request 事件 |
| `POST /webhook/manual-review` | 手动提交代码片段审查 |
| `GET /webhook/health` | 服务健康检查 |
| `GET /webhook/test-gitlab` | 测试 GitLab 连通性 |
| `GET /webhook/review-branch` | 手动触发分支审查 |

### 审查任务接口

| 接口 | 说明 |
|:---|:---|
| `POST /api/review/branch` | 异步启动分支审查，返回 taskId |
| `GET /api/review/{taskId}/status` | 查询任务状态（运行中/完成/未找到） |
| `GET /api/review/{taskId}/report` | 获取审查报告 JSON |
| `GET /api/reports/{taskId}/export?format=` | 导出报告（html/json/markdown/pdf） |
| `GET /api/reports/{taskId}/view` | 浏览器预览 HTML 报告 |
| `GET /api/reports` | 列出所有报告摘要 |
| `DELETE /api/reports/{taskId}` | 清除报告缓存 |

### 看板数据接口

| 接口 | 说明 |
|:---|:---|
| `GET /api/dashboard/stats` | 全局统计（总审查数、问题数、质量分等） |
| `GET /api/dashboard/projects` | 所有项目列表 |
| `GET /api/dashboard/projects/{projectId}` | 项目详情及近期审查 |
| `GET /api/dashboard/history/recent` | 最近审查记录 |
| `GET /api/dashboard/trends` | 近 7 天审查趋势数据 |
| `DELETE /api/dashboard/history/{historyId}` | 删除历史记录 |

### WebSocket 实时进度

连接 `ws://your-server:8080/ws/review-progress`，连接后发送：

```json
{ "taskId": "your-task-id" }
```

服务端会实时推送：

```json
{ "taskId": "xxx", "type": "PROGRESS", "message": "正在进行 P3C 规范检测...", "progress": 30 }
{ "taskId": "xxx", "type": "PROGRESS", "message": "AI 深度分析中...", "progress": 70 }
{ "taskId": "xxx", "type": "COMPLETE", "message": "审查完成", "progress": 100 }
```

> **提示**：若客户端连接晚于任务启动，服务端会缓存最多 100 条进度消息（保留 5 分钟），连接后自动补发。

## 手动触发示例

**触发分支审查：**

```bash
curl -X POST "http://localhost:8080/api/review/branch" \
  -H "Content-Type: application/json" \
  -d '{"projectId": "my-project", "branch": "feature/xxx"}'
```

**手动提交代码片段审查：**

```bash
curl -X POST "http://localhost:8080/webhook/manual-review" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class Test { ... }",
    "context": "用户登录模块",
    "repository": "my-org/my-repo",
    "prId": "42"
  }'
```

**导出报告：**

```bash
# 导出 Markdown
curl "http://localhost:8080/api/reports/{taskId}/export?format=markdown" -o report.md

# 导出 PDF
curl "http://localhost:8080/api/reports/{taskId}/export?format=pdf" -o report.pdf
```

## 配置说明

### application.yml 核心配置

```yaml
langchain4j:
  open-ai:
    base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com/v1}
    api-key: ${DEEPSEEK_API_KEY}
    model-name: ${DEEPSEEK_MODEL:deepseek-chat}
    temperature: 0.1
    timeout: 60

app:
  gitlab:
    url: ${GITLAB_URL:}
    token: ${GITLAB_TOKEN:}
  github:
    token: ${GITHUB_TOKEN:}

  review:
    enable-ai: true                  # 是否开启 AI 分析
    local-issue-threshold: 10        # 本地问题超过此数量跳过 AI
    max-code-length: 50000           # 单次审查代码最大长度
    post-comments: true              # 是否将结果回写到 PR/MR 评论

  analysis:
    local-rules-enabled: true
    ai-analysis-enabled: true
    sonarqube:
      enabled: false                 # 默认禁用
      url: http://localhost:9000
      token: ${SONAR_TOKEN:}
      project-key: ${SONAR_PROJECT_KEY:}
    spotbugs:
      enabled: false                 # 默认禁用
      executable-path: spotbugs
      effort: default                # min / default / max
      threshold: medium              # high / medium / low
```

### 环境变量

| 变量 | 说明 | 必需 |
|:---|:---|:---:|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | ✅ |
| `DEEPSEEK_BASE_URL` | API 地址，默认 `https://api.deepseek.com/v1` | |
| `DEEPSEEK_MODEL` | 模型名称，默认 `deepseek-chat` | |
| `OPENAI_API_KEY` | OpenAI API 密钥（可选替代 DeepSeek） | |
| `GITLAB_URL` | GitLab 服务地址 | |
| `GITLAB_TOKEN` | GitLab API Token | ✅ |
| `GITLAB_WEBHOOK_TOKEN` | GitLab Webhook 验证 Token | |
| `GITHUB_TOKEN` | GitHub API Token | |
| `GITHUB_WEBHOOK_SECRET` | GitHub Webhook HMAC 签名密钥 | |
| `SONAR_TOKEN` | SonarQube Token（可选） | |
| `SONAR_PROJECT_KEY` | SonarQube 项目 Key（可选） | |

### 切换 AI 模型

```bash
# 使用 OpenAI
export DEEPSEEK_BASE_URL=https://api.openai.com/v1
export OPENAI_API_KEY=sk-xxx

# 使用其他兼容 OpenAI 接口的服务（如 Qwen、Yi）
export DEEPSEEK_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
export DEEPSEEK_API_KEY=your_qwen_key
export DEEPSEEK_MODEL=qwen-coder-plus
```

### 启用可选外部工具

**SonarQube：**

```yaml
# application.yml
app:
  analysis:
    sonarqube:
      enabled: true
      url: http://localhost:9000
      token: ${SONAR_TOKEN}
      project-key: ${SONAR_PROJECT_KEY}
```

**SpotBugs：**

```yaml
app:
  analysis:
    spotbugs:
      enabled: true
      executable-path: /usr/local/bin/spotbugs
      effort: max
      threshold: low
```

## 技术栈

| 组件 | 版本 | 用途 |
|:---|:---|:---|
| Spring Boot | 3.2 | 应用框架 |
| LangChain4j | 0.35.0 | LLM 集成与 Agent 框架 |
| DeepSeek | - | AI 代码审查（默认） |
| Alibaba P3C-PMD | - | 阿里巴巴 Java 规范检测 |
| JavaParser | - | 本地静态代码分析 |
| Spring WebFlux | - | 异步 HTTP 客户端（WebClient） |
| WebSocket | - | 实时进度推送 |
| Java | 17 | 运行环境 |
| Maven | 3.8+ | 构建工具 |
| Docker | - | 容器化部署 |

## 测试

```bash
# 运行全部测试
mvn test

# 运行单个测试类
mvn test -Dtest=CodeReviewTest

# 测试 GitLab 连通性
mvn test -Dtest=CodeReviewServiceTest#testGitLabConnection
```

## 常见问题

**Q: Token 费用过高**

本地规则会优先过滤问题，当发现问题数超过 `local-issue-threshold`（默认 10）时自动跳过 AI 调用。也可以切换至更便宜的模型如 `deepseek-chat`。

**Q: 审查结果不准确**

优化 `src/main/resources/prompts/review-prompt.txt` 中的 Few-shot 示例，或在提示词中添加项目特定的规范说明，并调低 `temperature` 参数。

**Q: 无法连接内网 GitLab**

检查 `GITLAB_URL` 配置和网络连通性：`curl http://your-gitlab-host`。GitLab Token 需要 `api` 权限。使用 Docker 时需检查容器网络配置。

**Q: WebSocket 进度消息收不到**

日志中 `当前会话数: 0` 表示客户端尚未连接。确保在任务启动前或启动后 5 分钟内完成 WebSocket 连接并发送 `taskId` 绑定消息。

**Q: SonarQube / SpotBugs 不生效**

这两个工具默认禁用。需在 `application.yml` 中将对应的 `enabled` 改为 `true`，并确保服务已安装/运行。

## 贡献指南

1. Fork 项目
2. 创建分支：`git checkout -b feature/xxx`
3. 提交更改：`git commit -m "feat: add xxx"`
4. 推送分支：`git push origin feature/xxx`
5. 创建 Pull Request

## 许可证

MIT License

## 致谢

- [LangChain4j](https://github.com/langchain4j/langchain4j)
- [Alibaba P3C](https://github.com/alibaba/p3c)
- [JavaParser](https://github.com/javaparser/javaparser)

---

**如果这个项目对你有帮助，欢迎 Star ⭐**
