# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在此代码仓库中工作时提供指引。

## 构建与运行

```bash
# 构建（跳过测试）
mvn clean package -DskipTests

# 运行所有测试
mvn test
# 运行单个测试类
mvn test -Dtest=CodeReviewTest

# 直接运行应用
java -jar target/java-code-review-agent-1.0.0.jar

# 通过 Docker 运行
docker-compose up -d
```

## 必要环境变量

以下变量需在 `application.yml` 或环境变量中配置，缺少任一项应用将无法正常工作：

| 变量名 | 用途 |
|---|---|
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥（默认使用 DeepSeek） |
| `DEEPSEEK_BASE_URL` | DeepSeek API 地址（默认 `https://api.deepseek.com/v1`）|
| `DEEPSEEK_MODEL` | 模型名称（默认 `deepseek-chat`）|
| `GITHUB_TOKEN` | GitHub API 访问令牌 |
| `GITHUB_WEBHOOK_SECRET` | GitHub Webhook HMAC 签名验证密钥 |
| `GITLAB_TOKEN` | GitLab API 访问令牌 |
| `GITLAB_PROJECT_ID` | GitLab 项目 ID |
| `GITLAB_WEBHOOK_TOKEN` | GitLab Webhook Token 验证 |

## 可选扩展配置

以下工具为**可选集成**，默认禁用，不影响核心功能：

| 变量名 | 用途 | 说明 |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API 密钥 | 如需使用 OpenAI 替代 DeepSeek |
| `SONAR_TOKEN` | SonarQube API Token | 需本地安装 SonarQube 服务 |
| `SONAR_PROJECT_KEY` | SonarQube 项目 Key | 需在 SonarQube 中配置项目 |

SpotBugs 通过 `application.yml` 中的 `app.analysis.spotbugs` 配置，无需环境变量。

### AI 模型切换

项目默认使用 **DeepSeek**（国内访问，性价比高）。如需切换 OpenAI：

```bash
# 方式一：环境变量
export DEEPSEEK_BASE_URL=https://api.openai.com/v1
export OPENAI_API_KEY=your_openai_key

# 方式二：application.yml
langchain4j:
  open-ai:
    base-url: https://api.openai.com/v1
    api-key: ${OPENAI_API_KEY}
    model-name: gpt-4o-mini
```

## 架构说明

本项目基于 **Spring Boot 3.2 / Java 17**，监听 Git 平台的 Webhook 事件，通过 LangChain4j（v0.35.0）调用 LLM（兼容 OpenAI 接口），并将代码审查评论回写到 PR 中。

### 请求处理流程

```
Webhook 事件（GitHub/GitLab）
  → WebhookController（POST /webhook/github 或 /webhook/gitlab）
  → CodeReviewService.executeReview(PRInfo)
      1. GitService.getDiff(prInfo)            — 通过 REST API 获取 PR 差异
      2. GitService.getCommitMessage(prInfo)   — 获取提交信息
      3. ReviewAgent.review(diff, context)
           a. PromptService.getSystemPrompt()  — 从模板构建提示词
           b. ChatLanguageModel.chat()         — 调用 LLM
           c. PromptService.parseReviewResult() — 解析 JSON 响应
      4. GitService.postComment(prInfo, markdown) — 将结果回写到 PR
```

### 核心组件

- **`agent/ReviewAgent`** — AI 审查核心。整合 P3C 规范检测、自定义规则检测和 AI 分析，提供完整的代码审查服务。
- **`agent/ReviewTools`** — 基于 JavaParser 进行本地静态分析，补充 P3C 未覆盖的安全漏洞（OWASP）和自定义规则。
- **`service/CodeReviewService`** — 串联完整审查流程；同时暴露 `POST /webhook/trigger` 接口支持手动触发。
- **`service/GitService`** — 封装 GitHub 与 GitLab 的差异：解析 Webhook 载荷、获取差异内容、回写评论。HTTP 请求使用 Spring WebFlux（`WebClient`）。
- **`service/PromptService`** — 从 `src/main/resources/prompts/` 加载并填充提示词模板，再将 LLM 返回的 JSON 解析为 `ReviewReport`。
- **`service/P3CAnalysisService`** — **Alibaba P3C 规范分析服务**，基于 P3C-PMD 规则库进行代码规范检测。
- **`config/`** — 配置 LangChain4j OpenAI `ChatLanguageModel` 等 Spring Bean。
- **`client/SonarQubeClient`** — SonarQube API 客户端（可选，默认禁用）。
- **`client/SpotBugsRunner`** — SpotBugs 执行器（可选，默认禁用）。

### LLM 集成说明

项目使用 **LangChain4j 的 `OpenAiChatModel`** 进行 AI 代码审查，默认接入 **DeepSeek API**（国内访问，性价比高）。

**默认配置：**
- API 地址：`https://api.deepseek.com/v1`
- 模型：`deepseek-chat`
- 兼容 OpenAI API 格式，可无缝切换其他模型

**提示词模板位置：**
- `src/main/resources/prompts/review-prompt.txt`
- `src/main/resources/prompts/commit-prompt.txt`

**切换 OpenAI：**
修改 `DEEPSEEK_BASE_URL` 和 `OPENAI_API_KEY` 环境变量即可。

### 审查输出格式

LLM 被要求返回如下 JSON，由 `PromptService` 解析为 `ReviewReport`：

```json
{
  "summary": "审查总结",
  "totalIssues": 0,
  "criticalIssues": 0,
  "warningIssues": 0,
  "infoIssues": 0,
  "issues": [
    {
      "severity": "CRITICAL|WARNING|INFO",
      "type": "BUG|SECURITY|QUALITY|PERFORMANCE|BEST_PRACTICE",
      "file": "文件路径", "line": 0,
      "description": "问题描述", "code": "问题代码", "suggestion": "修复建议"
    }
  ],
  "overallScore": 8,
  "recommendation": "APPROVE|REQUEST_CHANGES|COMMENTS"
}
```

### Webhook 接口

| 接口 | 用途 |
|---|---|
| `POST /webhook/github` | 接收 GitHub Webhook 事件 |
| `POST /webhook/gitlab` | 接收 GitLab Webhook 事件 |
| `POST /webhook/trigger` | 手动触发审查（参数：`?owner=&repo=&prNumber=&provider=`）|
| `GET /webhook/health` | 健康检查 |

### 多渠道检测架构

项目支持四层代码检测渠道，形成互补的检测体系：

```
┌─────────────────────────────────────────────────────────────┐
│                    代码审查流程                              │
├─────────────────────────────────────────────────────────────┤
│  第一层：P3C 阿里巴巴规范（PMD）                             │
│  ├── 命名规范（类名、方法名、变量名）                        │
│  ├── 代码格式（大括号、缩进、行宽）                          │
│  ├── OOP 规约（面向对象设计）                                │
│  ├── 集合处理（List/Map/Set 规范）                           │
│  ├── 并发处理（线程池、锁、并发集合）                        │
│  └── 注释规约（类注释、方法注释）                            │
│                                                             │
│  第二层：自定义规则检测（JavaParser）                        │
│  ├── 空指针检测                                             │
│  ├── 并发安全检测                                           │
│  ├── 安全漏洞检测（OWASP Top 10）                            │
│  └── 资源泄露检测                                           │
│                                                             │
│  第三层：AI 深度分析（DeepSeek LLM）                         │
│  ├── 设计问题检测                                           │
│  ├── 业务逻辑 Bug                                           │
│  └── 架构建议                                               │
│                                                             │
│  第四层：可选外部工具（需手动启用）                          │
│  ├── SonarQube（企业级质量分析）                            │
│  └── SpotBugs（字节码级 Bug 检测）                          │
└─────────────────────────────────────────────────────────────┘
```

**启用外部工具：**
- SonarQube：在 `application.yml` 设置 `app.analysis.sonarqube.enabled: true`，并配置 `SONAR_TOKEN`
- SpotBugs：在 `application.yml` 设置 `app.analysis.spotbugs.enabled: true`，并确保本地已安装

### 已知待完善项（TODO）

- **`WebhookController`**：`verifyGitHubSignature` 和 `verifyGitLabToken` 当前始终返回 `true`，生产环境必须实现真实的签名/Token 验证。
- **`application.yml`** 中的 `app.review.ignore-paths` 文件过滤规则需在 `GitService` 中实际执行。
