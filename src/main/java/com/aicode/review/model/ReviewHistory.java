package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码审查历史记录
 * 
 * 用于存储和展示审查历史，支持按项目、时间等维度查询。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewHistory {

    /**
     * 历史记录ID
     */
    private String historyId;

    /**
     * 关联的任务ID
     */
    private String taskId;

    /**
     * 项目ID（GitLab/GitHub 项目标识）
     */
    private String projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 仓库地址
     */
    private String repositoryUrl;

    /**
     * 分支名称
     */
    private String branch;

    /**
     * PR/MR ID
     */
    private String prId;

    /**
     * PR/MR 标题
     */
    private String prTitle;

    /**
     * 触发方式：WEBHOOK / MANUAL / SCHEDULED
     */
    private TriggerType triggerType;

    /**
     * 平台类型：GITHUB / GITLAB
     */
    private String platform;

    /**
     * 审查状态
     */
    private ReviewReport.Status status;

    /**
     * 问题统计
     */
    private IssueStats issueStats;

    /**
     * 文件统计
     */
    private FileStats fileStats;

    /**
     * 审查开始时间
     */
    private LocalDateTime startTime;

    /**
     * 审查结束时间
     */
    private LocalDateTime endTime;

    /**
     * 审查耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 触发者（Webhook 提交者或手动触发者）
     */
    private String triggeredBy;

    /**
     * 审查报告摘要
     */
    private String summary;

    /**
     * 关联的报告ID
     */
    private String reportId;

    /**
     * 问题统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueStats {
        private int critical;
        private int warning;
        private int info;
        private int total;

        public static IssueStats fromReport(ReviewReport report) {
            return IssueStats.builder()
                    .critical(report.getCriticalCount())
                    .warning(report.getWarningCount())
                    .info(report.getInfoCount())
                    .total(report.getIssueCount())
                    .build();
        }
    }

    /**
     * 文件统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileStats {
        private int totalFiles;
        private int reviewedFiles;
        private int javaFiles;
        private int changedFiles;

        public static FileStats fromReview(int total, int reviewed) {
            return FileStats.builder()
                    .totalFiles(total)
                    .reviewedFiles(reviewed)
                    .build();
        }
    }

    /**
     * 触发类型枚举
     */
    public enum TriggerType {
        WEBHOOK,    // Webhook 自动触发
        MANUAL,     // 手动触发
        SCHEDULED   // 定时任务触发
    }

    /**
     * 从审查报告创建历史记录
     */
    public static ReviewHistory fromReport(ReviewReport report, String projectId, String branch) {
        return ReviewHistory.builder()
                .historyId("hist-" + System.currentTimeMillis())
                .taskId(report.getTaskId())
                .projectId(projectId)
                .branch(branch)
                .prId(report.getPrId())
                .repositoryUrl(report.getRepository())
                .status(report.getStatus())
                .issueStats(IssueStats.fromReport(report))
                .startTime(report.getReviewTime())
                .durationMs(report.getReviewDurationMs())
                .summary(report.getSummary())
                .reportId(report.getReportId())
                .build();
    }
}
