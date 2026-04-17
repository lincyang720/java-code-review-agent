package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码审查报告模型
 * 
 * 汇总一次代码审查的所有结果，包括问题列表、统计信息等。
 * 支持生成 Markdown 格式的审查报告，可直接发布到 PR 评论。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReport {

    /**
     * 审查报告ID
     */
    private String reportId;

    /**
     * 审查摘要
     */
    private String summary;

    /**
     * 发现的问题列表
     */
    @Builder.Default
    private List<Issue> issues = new ArrayList<>();

    /**
     * 审查上下文
     */
    private Context context;

    /**
     * 严重问题数量（缓存）
     */
    @Builder.Default
    private int criticalCount = 0;

    /**
     * 警告问题数量（缓存）
     */
    @Builder.Default
    private int warningCount = 0;

    /**
     * 信息问题数量（缓存）
     */
    @Builder.Default
    private int infoCount = 0;

    /**
     * 审查时间
     */
    @Builder.Default
    private LocalDateTime reviewTime = LocalDateTime.now();

    /**
     * 审查耗时（毫秒）
     */
    private Long reviewDurationMs;

    /**
     * 审查状态
     */
    @Builder.Default
    private Status status = Status.SUCCESS;

    /**
     * 如果审查失败，错误信息
     */
    private String errorMessage;

    /**
     * 原始输出（用于调试）
     */
    private String rawOutput;

    /**
     * AI 生成的审查报告文档
     */
    private String aiReport;

    /**
     * PR 信息
     */
    private PRInfo prInfo;

    /**
     * PR ID（冗余字段，方便直接访问）
     */
    private String prId;

    /**
     * 仓库名称（冗余字段，方便直接访问）
     */
    private String repository;

    /**
     * 任务ID（用于 WebSocket 进度推送关联）
     */
    private String taskId;

    /**
     * 审查上下文
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Context {
        private PRInfo prInfo;
        private CommitMessage commitMessage;
        private int filesChanged;
        private int linesAdded;
        private int linesDeleted;

        public String getContext() {
            StringBuilder sb = new StringBuilder();
            if (prInfo != null) {
                sb.append("PR: ").append(prInfo.getDisplayName()).append("\n");
                sb.append("Title: ").append(prInfo.getTitle()).append("\n");
                sb.append("Author: ").append(prInfo.getAuthor()).append("\n");
                sb.append("Branch: ").append(prInfo.getSourceBranch())
                  .append(" -> ").append(prInfo.getTargetBranch()).append("\n");
            }
            if (commitMessage != null) {
                sb.append("Commit: ").append(commitMessage.getMessage()).append("\n");
            }
            sb.append("Files: ").append(filesChanged).append(", ");
            sb.append("Lines: +").append(linesAdded).append(" -").append(linesDeleted);
            return sb.toString();
        }
    }

    /**
     * 审查状态枚举
     */
    public enum Status {
        SUCCESS,      // 审查成功
        PARTIAL,      // 部分成功
        FAILED,       // 审查失败
        IN_PROGRESS   // 审查进行中
    }

    /**
     * 获取严重问题数量
     * 
     * @return CRITICAL 级别问题数量
     */
    public int getCriticalCount() {
        if (criticalCount > 0) return criticalCount;
        return (int) issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.CRITICAL)
                .count();
    }

    /**
     * 获取警告问题数量
     *
     * @return WARNING 级别问题数量
     */
    public int getWarningCount() {
        if (warningCount > 0) return warningCount;
        return (int) issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.WARNING)
                .count();
    }

    /**
     * 获取信息级别问题数量
     *
     * @return INFO 级别问题数量
     */
    public int getInfoCount() {
        if (infoCount > 0) return infoCount;
        return (int) issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.INFO)
                .count();
    }

    /**
     * 获取问题总数
     * 
     * @return 所有问题数量
     */
    public int getIssueCount() {
        return issues != null ? issues.size() : 0;
    }

    /**
     * 添加问题到报告
     * 
     * @param issue 要添加的问题
     */
    public void addIssue(Issue issue) {
        if (this.issues == null) {
            this.issues = new ArrayList<>();
        }
        this.issues.add(issue);
    }

    /**
     * 添加多个问题到报告
     * 
     * @param issues 要添加的问题列表
     */
    public void addIssues(List<Issue> issues) {
        if (this.issues == null) {
            this.issues = new ArrayList<>();
        }
        this.issues.addAll(issues);
    }

    /**
     * 转换为 Markdown 格式的审查报告
     * 适合直接发布到 GitHub/GitLab PR 评论
     * 
     * @return Markdown 格式的审查报告
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        
        // 报告标题
        sb.append("## 🔍 AI 代码审查报告\n\n");
        
        // 审查摘要
        if (summary != null && !summary.isEmpty()) {
            sb.append("> ").append(summary).append("\n\n");
        }
        
        // 统计信息
        sb.append("### 📊 审查统计\n\n");
        sb.append("| 严重 | 警告 | 信息 | 总计 |\n");
        sb.append("|:---:|:---:|:---:|:---:|\n");
        sb.append("| 🚨 ").append(getCriticalCount())
          .append(" | ⚠️ ").append(getWarningCount())
          .append(" | ℹ️ ").append(getInfoCount())
          .append(" | ").append(getIssueCount()).append(" |\n\n");
        
        // 审查状态
        if (status == Status.FAILED) {
            sb.append("❌ **审查失败**: ").append(errorMessage).append("\n\n");
            return sb.toString();
        }
        
        // 按严重级别分组显示问题
        if (issues != null && !issues.isEmpty()) {
            // 严重问题
            List<Issue> criticalIssues = issues.stream()
                    .filter(i -> i.getSeverity() == Issue.Severity.CRITICAL)
                    .toList();
            if (!criticalIssues.isEmpty()) {
                sb.append("### 🚨 严重问题 (").append(criticalIssues.size()).append(")\n\n");
                criticalIssues.forEach(i -> sb.append(i.toMarkdown()));
            }

            // 警告问题
            List<Issue> warningIssues = issues.stream()
                    .filter(i -> i.getSeverity() == Issue.Severity.WARNING)
                    .toList();
            if (!warningIssues.isEmpty()) {
                sb.append("### ⚠️ 警告 (").append(warningIssues.size()).append(")\n\n");
                warningIssues.forEach(i -> sb.append(i.toMarkdown()));
            }

            // 信息级别
            List<Issue> infoIssues = issues.stream()
                    .filter(i -> i.getSeverity() == Issue.Severity.INFO)
                    .toList();
            if (!infoIssues.isEmpty()) {
                sb.append("### ℹ️ 建议 (").append(infoIssues.size()).append(")\n\n");
                infoIssues.forEach(i -> sb.append(i.toMarkdown()));
            }
        } else {
            sb.append("✅ **未发现明显问题，代码质量良好！**\n\n");
        }
        
        // 页脚
        sb.append("---\n");
        sb.append("*由 Java Code Review Agent 生成于 ").append(reviewTime).append("*\n");
        if (reviewDurationMs != null) {
            sb.append("*审查耗时: ").append(reviewDurationMs).append("ms*\n");
        }
        
        return sb.toString();
    }

    /**
     * 转换为简洁的 Markdown 报告（用于评论长度限制场景）
     * 
     * @return 简洁的 Markdown 报告
     */
    public String toCompactMarkdown() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("## 🔍 AI 代码审查\n\n");
        sb.append("🚨 ").append(getCriticalCount())
          .append(" | ⚠️ ").append(getWarningCount())
          .append(" | ℹ️ ").append(getInfoCount())
          .append("\n\n");
        
        // 只显示严重问题
        List<Issue> criticalIssues = issues.stream()
                .filter(i -> i.getSeverity() == Issue.Severity.CRITICAL)
                .limit(5)  // 最多显示5个
                .toList();
        
        if (!criticalIssues.isEmpty()) {
            sb.append("### 严重问题\n\n");
            criticalIssues.forEach(i -> {
                sb.append("- **").append(i.getType()).append("**: ")
                  .append(i.getDescription());
                if (i.getFile() != null) {
                    sb.append(" (`").append(i.getFile()).append("`)");
                }
                sb.append("\n");
            });
        }
        
        if (getCriticalCount() > 5) {
            sb.append("\n*还有 ").append(getCriticalCount() - 5).append(" 个严重问题未显示...*\n");
        }
        
        return sb.toString();
    }

    // ============================================================
    // 静态工厂方法
    // ============================================================

    /**
     * 创建错误报告
     */
    public static ReviewReport error(String message) {
        return ReviewReport.builder()
                .status(Status.FAILED)
                .errorMessage(message)
                .summary("审查失败: " + message)
                .issues(new ArrayList<>())
                .build();
    }

    /**
     * 创建成功报告
     */
    public static ReviewReport success(String summary, List<Issue> issues) {
        return ReviewReport.builder()
                .status(Status.SUCCESS)
                .summary(summary)
                .issues(issues)
                .build();
    }
}
