package com.aicode.review.service;

import com.aicode.review.agent.ReviewAgent;
import com.aicode.review.client.GitHubClient;
import com.aicode.review.client.GitLabClient;
import com.aicode.review.model.CommitMessage;
import com.aicode.review.model.PRDifferences;
import com.aicode.review.model.ReviewReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 代码审查服务
 *
 * 核心业务逻辑：接收 Webhook 事件，执行代码审查，发布审查结果。
 * 支持 WebSocket 实时进度推送。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewService {

    private final ReviewAgent reviewAgent;
    private final GitService gitService;
    private final GitLabClient gitLabClient;
    private final GitHubClient gitHubClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;
    private final ReviewProgressService progressService;

    /**
     * 触发代码审查（异步）
     *
     * @param payload  Webhook 请求体
     * @param platform 平台类型 (github/gitlab)
     */
    @Async
    public void triggerReview(String payload, String platform) {
        log.info("触发代码审查, 平台: {}", platform);

        try {
            // 1. 解析 Webhook 获取 PR/MR 信息
            PRDifferences diff = gitService.getDiffFromWebhook(payload, platform);
            if (diff == null) {
                log.error("无法解析 PR/MR 信息");
                return;
            }

            log.info("获取到 PR #{}: {}, 文件数: {}",
                    diff.getPrNumber(), diff.getTitle(), diff.getChangedFilesCount());

            // 2. 获取提交信息
            CommitMessage commit = gitService.getCommitInfo(payload, platform);

            // 3. 构建代码内容
            StringBuilder codeBuilder = new StringBuilder();
            for (var file : diff.getJavaFileDiffs()) {
                if (file.getPatch() != null) {
                    codeBuilder.append("// File: ").append(file.getFilePath()).append("\n");
                    codeBuilder.append(file.getPatch()).append("\n\n");
                }
            }

            String code = codeBuilder.toString();
            if (code.isEmpty()) {
                log.info("没有 Java 文件变更，跳过审查");
                return;
            }

            // 4. 执行代码审查
            String context = diff.getContext();
            if (commit != null) {
                context += "\n提交信息: " + commit.getShortMessage();
            }

            ReviewReport report = reviewAgent.review(code, context);
            report.setPrId(diff.getPrNumber());
            report.setRepository(diff.getRepository());

            // 5. 发布审查结果
            publishReviewResult(diff, report, platform, payload);

            log.info("代码审查完成: PR #{}, 发现 {} 个问题",
                    diff.getPrNumber(), report.getIssueCount());

        } catch (Exception e) {
            log.error("代码审查失败", e);
        }
    }

    /**
     * 发布审查结果到对应平台
     */
    private void publishReviewResult(PRDifferences diff, ReviewReport report,
                                      String platform, String payload) {
        try {
            if ("gitlab".equals(platform)) {
                JsonNode json = objectMapper.readTree(payload);
                String projectId = json.path("project").path("id").asText();
                int mrIid = Integer.parseInt(diff.getPrNumber());

                boolean success = gitLabClient.postReviewComment(projectId, mrIid, report);
                if (success) {
                    log.info("审查评论已发布到 GitLab MR #{}" , mrIid);
                } else {
                    log.error("发布审查评论失败");
                }

            } else if ("github".equals(platform)) {
                // TODO: 实现 GitHub 评论发布
                log.info("GitHub 评论发布待实现");
            }
        } catch (Exception e) {
            log.error("发布审查结果失败", e);
        }
    }

    /**
     * 手动触发代码审查（用于测试）
     *
     * @param request 手动审查请求
     * @return 审查结果摘要
     */
    public String manualReview(com.aicode.review.controller.WebhookController.ManualReviewRequest request) {
        log.info("手动代码审查: {}", request);

        ReviewReport report = reviewAgent.review(
                request.getCode(),
                request.getContext() != null ? request.getContext() : "手动审查"
        );

        return report.toMarkdown();
    }

    /**
     * 测试 GitLab 连接
     *
     * @return 连接测试结果
     */
    public String testGitLabConnection() {
        return gitLabClient.testConnection();
    }

    /**
     * 手动审查 GitLab MR（同步版本，用于 Controller 调用）
     *
     * @param projectId GitLab 项目 ID 或路径
     * @param mrIid     MR IID
     * @return 审查结果摘要
     */
    public String reviewGitLabMR(String projectId, int mrIid) {
        log.info("开始审查 GitLab MR: {}/{}" , projectId, mrIid);

        try {
            // 1. 获取 MR 信息
            JsonNode mrInfo = gitLabClient.getMergeRequest(projectId, mrIid);
            if (mrInfo == null) {
                return "无法获取 MR 信息，请检查项目ID和MR编号";
            }

            String title = mrInfo.path("title").asText("Unknown");
            String description = mrInfo.path("description").asText("");
            String sourceBranch = mrInfo.path("source_branch").asText();
            String targetBranch = mrInfo.path("target_branch").asText();

            log.info("MR 标题: {}, 分支: {} -> {}", title, sourceBranch, targetBranch);

            // 2. 获取代码差异
            var diffs = gitLabClient.getMergeRequestDiffs(projectId, mrIid);
            if (diffs.isEmpty()) {
                return "MR 中没有文件变更";
            }

            log.info("获取到 {} 个文件的变更", diffs.size());

            // 3. 构建代码内容（只审查 Java 文件）
            StringBuilder codeBuilder = new StringBuilder();
            int javaFileCount = 0;
            for (var diff : diffs) {
                if (diff.getFilePath().endsWith(".java")) {
                    codeBuilder.append("// File: ").append(diff.getFilePath()).append("\n");
                    codeBuilder.append(diff.getPatch()).append("\n\n");
                    javaFileCount++;
                }
            }

            if (javaFileCount == 0) {
                return "MR 中没有 Java 文件变更";
            }

            String code = codeBuilder.toString();
            log.info("开始审查 {} 个 Java 文件", javaFileCount);

            // 4. 构建上下文
            String context = String.format("MR: %s\n描述: %s\n分支: %s -> %s",
                    title, description, sourceBranch, targetBranch);

            // 5. 执行审查
            ReviewReport report = reviewAgent.review(code, context);

            // 6. 发布审查结果
            boolean posted = gitLabClient.postReviewComment(projectId, mrIid, report);

            String result = String.format("审查完成！发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
                    report.getIssueCount(),
                    report.getCriticalCount(),
                    report.getWarningCount(),
                    report.getInfoCount());

            if (posted) {
                result += "，审查报告已发布到 MR";
            } else {
                result += "，但发布评论失败";
            }

            return result;

        } catch (Exception e) {
            log.error("审查 GitLab MR 失败", e);
            return "审查失败: " + e.getMessage();
        }
    }

    /**
     * 审查分支代码（带进度回调）
     *
     * @param projectId 项目 ID 或路径
     * @param branch    分支名
     * @param progressCallback 进度回调函数
     * @return 审查报告
     */
    public ReviewReport reviewBranchWithProgress(String projectId, String branch, 
                                                  Consumer<ReviewProgressService.ProgressEvent> progressCallback) {
        return reviewBranchWithProgress(projectId, branch, null, progressCallback);
    }

    /**
     * 审查分支代码（带进度回调，指定任务ID）
     *
     * @param projectId 项目 ID 或路径
     * @param branch    分支名
     * @param taskId    任务ID（可为null，会自动生成）
     * @param progressCallback 进度回调函数
     * @return 审查报告
     */
    public ReviewReport reviewBranchWithProgress(String projectId, String branch, String taskId,
                                                  Consumer<ReviewProgressService.ProgressEvent> progressCallback) {
        if (taskId == null) {
            taskId = progressService.createTaskId();
        }
        log.info("开始审查分支代码: {}/{}, 任务ID: {}", projectId, branch, taskId);

        try {
            // 1. 获取分支最新提交
            JsonNode commit = gitLabClient.getBranchCommit(projectId, branch);
            if (commit == null) {
                String error = "无法获取分支提交信息，请检查项目ID和分支名";
                sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                    .type(ReviewProgressService.ProgressEvent.EventType.ERROR)
                    .taskId(taskId)
                    .message(error)
                    .build());
                throw new RuntimeException(error);
            }

            String commitId = commit.path("id").asText();
            String commitMessage = commit.path("message").asText();
            String author = commit.path("author_name").asText();

            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                .type(ReviewProgressService.ProgressEvent.EventType.LOG)
                .taskId(taskId)
                .message("最新提交: " + commitId.substring(0, 8) + " by " + author)
                .build());

            // 2. 获取仓库 Java 文件列表
            List<String> files = gitLabClient.getRepositoryTree(projectId, branch, null);
            if (files.isEmpty()) {
                String error = "分支中没有找到 Java 文件";
                sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                    .type(ReviewProgressService.ProgressEvent.EventType.ERROR)
                    .taskId(taskId)
                    .message(error)
                    .build());
                throw new RuntimeException(error);
            }

            int totalFiles = files.size();
            
            // 计算并行线程数：根据CPU核心数和文件数量动态调整
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int batchSize = Math.max(5, Math.min(20, totalFiles / availableProcessors));
            int threadPoolSize = Math.min(availableProcessors * 2, 8);
            
            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                .type(ReviewProgressService.ProgressEvent.EventType.STARTED)
                .taskId(taskId)
                .total(totalFiles)
                .message(String.format("找到 %d 个 Java 文件，启动 %d 线程并行检测（批次大小: %d）...", 
                    totalFiles, threadPoolSize, batchSize))
                .build());

            // 3. 并行批次处理文件检测
            java.util.concurrent.CopyOnWriteArrayList<com.aicode.review.model.Issue> allIssues = 
                new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.CopyOnWriteArrayList<FileResult> fileResults = 
                new java.util.concurrent.CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);

            // 创建线程池
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadPoolSize);
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(files.size());

            for (String filePath : files) {
                String finalTaskId = taskId;
                executor.submit(() -> {
                    int current = processedCount.incrementAndGet();
                    
                    sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                        .type(ReviewProgressService.ProgressEvent.EventType.PROGRESS)
                        .taskId(finalTaskId)
                        .current(current)
                        .total(totalFiles)
                        .filePath(filePath)
                        .message("正在检测: " + filePath)
                        .build());

                    try {
                        String content = gitLabClient.getFileContent(projectId, branch, filePath);
                        if (content == null) {
                            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                                .type(ReviewProgressService.ProgressEvent.EventType.LOG)
                                .taskId(finalTaskId)
                                .message("[进度 " + current + "/" + totalFiles + "] 无法获取文件内容: " + filePath)
                                .build());
                            return;
                        }

                        // 执行静态规则检测（不调用AI）
                        String fileCode = "// File: " + filePath + "\n" + content;
                        List<com.aicode.review.model.Issue> fileIssues = reviewAgent.performLocalChecksOnly(fileCode);
                        
                    // 为每个问题设置文件路径
                    fileIssues.forEach(issue -> {
                        if (issue.getFile() == null || issue.getFile().isEmpty()) {
                            issue.setFile(filePath);
                        }
                    });
                        
                        allIssues.addAll(fileIssues);
                        fileResults.add(new FileResult(filePath, content, fileIssues));
                        successCount.incrementAndGet();

                        int issueCount = fileIssues.size();
                        sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                            .type(ReviewProgressService.ProgressEvent.EventType.FILE_COMPLETE)
                            .taskId(finalTaskId)
                            .current(current)
                            .total(totalFiles)
                            .filePath(filePath)
                            .issueCount(issueCount)
                            .message(issueCount > 0 
                                ? "[" + current + "/" + totalFiles + "] " + filePath + " 检测完成，发现 " + issueCount + " 个问题"
                                : "[" + current + "/" + totalFiles + "] " + filePath + " 检测完成，未发现问题")
                            .build());

                    } catch (Exception e) {
                        sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                            .type(ReviewProgressService.ProgressEvent.EventType.LOG)
                            .taskId(finalTaskId)
                            .message("[进度 " + current + "/" + totalFiles + "] 检测文件失败: " + filePath + " - " + e.getMessage())
                            .build());
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 等待所有任务完成
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("检测被中断", e);
            } finally {
                executor.shutdown();
            }

            int finalSuccessCount = successCount.get();

            if (finalSuccessCount == 0) {
                String error = "无法获取任何文件内容进行审查";
                sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                    .type(ReviewProgressService.ProgressEvent.EventType.ERROR)
                    .taskId(taskId)
                    .message(error)
                    .build());
                throw new RuntimeException(error);
            }

            // 4. 所有文件检测完成后，统一生成AI报告
            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                .type(ReviewProgressService.ProgressEvent.EventType.LOG)
                .taskId(taskId)
                .message("所有文件检测完成，正在生成AI审查报告...")
                .build());

            // 构建整体上下文
            String context = String.format(
                "项目: %s\n分支: %s\n提交: %s\n作者: %s\n文件数: %d/%d",
                projectId, branch, commitId.substring(0, 8), author, finalSuccessCount, totalFiles
            );

            // 统一生成AI报告（只传文件数和问题列表，不传完整代码，避免token超限）
            String aiReport = reviewAgent.generateAIReportOnly(finalSuccessCount, context, 
                new java.util.ArrayList<>(allIssues));

            // 5. 构建最终报告
            ReviewReport finalReport = buildFinalReport(new java.util.ArrayList<>(allIssues), aiReport, finalSuccessCount, totalFiles);
            finalReport.setTaskId(taskId);

            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                .type(ReviewProgressService.ProgressEvent.EventType.COMPLETED)
                .taskId(taskId)
                .report(finalReport)
                .message(String.format("审查完成！共审查 %d/%d 个文件，发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
                    finalSuccessCount, totalFiles, finalReport.getIssueCount(),
                    finalReport.getCriticalCount(), finalReport.getWarningCount(),
                    finalReport.getInfoCount()))
                .build());

            return finalReport;

        } catch (Exception e) {
            log.error("审查分支代码失败", e);
            sendProgress(progressCallback, ReviewProgressService.ProgressEvent.builder()
                .type(ReviewProgressService.ProgressEvent.EventType.ERROR)
                .taskId(taskId)
                .message("审查失败: " + e.getMessage())
                .build());
            throw new RuntimeException("审查失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送进度事件
     */
    private void sendProgress(Consumer<ReviewProgressService.ProgressEvent> callback, 
                              ReviewProgressService.ProgressEvent event) {
        if (callback != null) {
            try {
                callback.accept(event);
            } catch (Exception e) {
                log.warn("发送进度事件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 审查分支代码（同步版本，返回字符串结果）
     *
     * @param projectId 项目 ID 或路径
     * @param branch    分支名
     * @return 审查结果摘要
     */
    public String reviewBranch(String projectId, String branch) {
        try {
            // 使用 WebSocket 进度服务作为回调
            String taskId = progressService.createTaskId();
            Consumer<ReviewProgressService.ProgressEvent> callback = progressService.createProgressCallback(taskId);
            
            ReviewReport report = reviewBranchWithProgress(projectId, branch, callback);
            
            return String.format(
                "审查完成！共发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
                report.getIssueCount(),
                report.getCriticalCount(),
                report.getWarningCount(),
                report.getInfoCount()
            );
        } catch (Exception e) {
            return "审查失败: " + e.getMessage();
        }
    }

    /**
     * 文件检测结果记录
     */
    private record FileResult(String filePath, String content, 
                               List<com.aicode.review.model.Issue> issues) {}

    /**
     * 构建最终审查报告
     */
    private ReviewReport buildFinalReport(List<com.aicode.review.model.Issue> allIssues, 
                                           String aiReport, int successCount, int totalFiles) {
        // 统计问题数量
        int critical = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.CRITICAL)
            .count();
        int warning = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.WARNING)
            .count();
        int info = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.INFO)
            .count();

        // 生成摘要
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append(String.format(
            "共审查 %d/%d 个文件，发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
            successCount, totalFiles, allIssues.size(), critical, warning, info
        ));

        return ReviewReport.builder()
            .reportId(java.util.UUID.randomUUID().toString())
            .reviewTime(java.time.LocalDateTime.now())
            .status(ReviewReport.Status.SUCCESS)
            .issues(allIssues)
            .summary(summaryBuilder.toString())
            .aiReport(aiReport)
            .build();
    }

    /**
     * 合并多个文件的审查报告（兼容旧方法）
     */
    private ReviewReport mergeReports(List<ReviewReport> reports) {
        if (reports.isEmpty()) {
            return ReviewReport.builder()
                .reportId(java.util.UUID.randomUUID().toString())
                .reviewTime(java.time.LocalDateTime.now())
                .status(ReviewReport.Status.SUCCESS)
                .issues(new java.util.ArrayList<>())
                .summary("无审查结果")
                .build();
        }

        // 合并所有问题
        List<com.aicode.review.model.Issue> allIssues = new java.util.ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();
        StringBuilder aiReportBuilder = new StringBuilder();

        for (ReviewReport report : reports) {
            if (report.getIssues() != null) {
                allIssues.addAll(report.getIssues());
            }
            if (report.getAiReport() != null && !report.getAiReport().isEmpty()) {
                aiReportBuilder.append(report.getAiReport()).append("\n\n---\n\n");
            }
        }

        // 生成汇总摘要
        int critical = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.CRITICAL)
            .count();
        int warning = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.WARNING)
            .count();
        int info = (int) allIssues.stream()
            .filter(i -> i.getSeverity() == com.aicode.review.model.Issue.Severity.INFO)
            .count();

        summaryBuilder.append(String.format(
            "共审查 %d 个文件，发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
            reports.size(), allIssues.size(), critical, warning, info
        ));

        return ReviewReport.builder()
            .reportId(java.util.UUID.randomUUID().toString())
            .reviewTime(java.time.LocalDateTime.now())
            .status(ReviewReport.Status.SUCCESS)
            .issues(allIssues)
            .summary(summaryBuilder.toString())
            .aiReport(aiReportBuilder.toString())
            .reviewDurationMs(reports.stream().mapToLong(r -> r.getReviewDurationMs() != null ? r.getReviewDurationMs() : 0).sum())
            .build();
    }
}
