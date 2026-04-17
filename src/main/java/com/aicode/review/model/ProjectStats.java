package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 项目审查统计
 * 
 * 汇总单个项目的审查统计数据，用于后台展示。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStats {

    /**
     * 项目ID
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
     * 平台类型
     */
    private String platform;

    /**
     * 总审查次数
     */
    private int totalReviews;

    /**
     * 成功次数
     */
    private int successCount;

    /**
     * 失败次数
     */
    private int failedCount;

    /**
     * 累计发现问题数
     */
    private int totalIssues;

    /**
     * 累计严重问题数
     */
    private int totalCritical;

    /**
     * 累计警告数
     */
    private int totalWarning;

    /**
     * 累计建议数
     */
    private int totalInfo;

    /**
     * 平均每次审查问题数
     */
    private double avgIssuesPerReview;

    /**
     * 平均审查耗时（毫秒）
     */
    private long avgDurationMs;

    /**
     * 最后一次审查时间
     */
    private LocalDateTime lastReviewTime;

    /**
     * 最后一次审查状态
     */
    private ReviewReport.Status lastReviewStatus;

    /**
     * 趋势数据（最近7天每天的问题数）
     */
    @Builder.Default
    private Map<String, Integer> dailyIssueTrend = new HashMap<>();

    /**
     * 趋势数据（最近7天每天的审查次数）
     */
    @Builder.Default
    private Map<String, Integer> dailyReviewTrend = new HashMap<>();

    /**
     * 代码质量评分（0-100）
     */
    private int qualityScore;

    /**
     * 计算代码质量评分
     * 基于问题密度和严重程度计算
     */
    public void calculateQualityScore() {
        if (totalReviews == 0) {
            this.qualityScore = 100;
            return;
        }

        // 基础分100，根据问题扣分
        double score = 100.0;
        
        // 严重问题扣10分/个
        score -= totalCritical * 10;
        // 警告扣3分/个
        score -= totalWarning * 3;
        // 建议扣0.5分/个
        score -= totalInfo * 0.5;

        // 确保分数在0-100之间
        this.qualityScore = Math.max(0, Math.min(100, (int) score));
    }

    /**
     * 更新统计数据
     */
    public void updateWithHistory(ReviewHistory history) {
        this.totalReviews++;
        
        if (history.getStatus() == ReviewReport.Status.SUCCESS) {
            this.successCount++;
        } else if (history.getStatus() == ReviewReport.Status.FAILED) {
            this.failedCount++;
        }

        if (history.getIssueStats() != null) {
            this.totalIssues += history.getIssueStats().getTotal();
            this.totalCritical += history.getIssueStats().getCritical();
            this.totalWarning += history.getIssueStats().getWarning();
            this.totalInfo += history.getIssueStats().getInfo();
        }

        if (history.getDurationMs() != null) {
            // 更新平均耗时
            long totalDuration = this.avgDurationMs * (this.totalReviews - 1) + history.getDurationMs();
            this.avgDurationMs = totalDuration / this.totalReviews;
        }

        this.avgIssuesPerReview = (double) this.totalIssues / this.totalReviews;
        this.lastReviewTime = history.getEndTime() != null ? history.getEndTime() : history.getStartTime();
        this.lastReviewStatus = history.getStatus();

        calculateQualityScore();
    }
}
