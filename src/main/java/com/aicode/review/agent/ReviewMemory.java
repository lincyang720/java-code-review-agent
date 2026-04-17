package com.aicode.review.agent;

import com.aicode.review.model.ReviewReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审查记忆管理器
 * 
 * 管理代码审查的历史记录，支持：
 * 1. 缓存最近的审查结果
 * 2. 避免重复审查相同代码
 * 3. 追踪审查历史趋势
 * 
 * 使用内存存储，生产环境可替换为 Redis
 * 
 * @author AI Code Review Team
 */
@Slf4j
@Component
public class ReviewMemory {

    // 审查结果缓存：PR ID -> 审查报告
    private final Map<String, ReviewReport> reviewCache = new ConcurrentHashMap<>();
    
    // 代码指纹缓存：代码指纹 -> 审查报告（避免重复审查相同代码）
    private final Map<String, ReviewReport> codeFingerprintCache = new ConcurrentHashMap<>();
    
    // 仓库审查统计
    private final Map<String, RepositoryStats> repositoryStats = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24小时
    
    // 最大缓存条目数
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * 保存审查结果
     * 
     * @param prId PR 标识
     * @param codeFingerprint 代码指纹（代码内容的哈希）
     * @param report 审查报告
     */
    public void saveReview(String prId, String codeFingerprint, ReviewReport report) {
        log.debug("保存审查结果: PR={}, 指纹={}", prId, codeFingerprint);
        
        reviewCache.put(prId, report);
        codeFingerprintCache.put(codeFingerprint, report);
        
        // 更新仓库统计
        updateRepositoryStats(report);
        
        // 清理过期缓存
        cleanupCache();
    }

    /**
     * 获取缓存的审查结果
     * 
     * @param prId PR 标识
     * @return 审查报告，如果没有缓存返回 null
     */
    public ReviewReport getCachedReview(String prId) {
        ReviewReport report = reviewCache.get(prId);
        
        if (report != null && isExpired(report)) {
            log.debug("缓存已过期: PR={}", prId);
            reviewCache.remove(prId);
            return null;
        }
        
        return report;
    }

    /**
     * 检查代码是否已审查过（通过代码指纹）
     * 
     * @param codeFingerprint 代码指纹
     * @return 如果已审查过返回审查报告，否则返回 null
     */
    public ReviewReport getReviewByFingerprint(String codeFingerprint) {
        ReviewReport report = codeFingerprintCache.get(codeFingerprint);
        
        if (report != null && isExpired(report)) {
            log.debug("代码指纹缓存已过期");
            codeFingerprintCache.remove(codeFingerprint);
            return null;
        }
        
        if (report != null) {
            log.debug("命中代码指纹缓存，复用审查结果");
        }
        
        return report;
    }

    /**
     * 获取仓库的审查统计
     * 
     * @param repository 仓库名称
     * @return 仓库统计信息
     */
    public RepositoryStats getRepositoryStats(String repository) {
        return repositoryStats.getOrDefault(repository, new RepositoryStats(repository));
    }

    /**
     * 获取所有仓库的统计
     * 
     * @return 所有仓库的统计列表
     */
    public List<RepositoryStats> getAllRepositoryStats() {
        return new ArrayList<>(repositoryStats.values());
    }

    /**
     * 获取最近审查的 PR 列表
     * 
     * @param limit 返回数量限制
     * @return 最近的审查报告列表
     */
    public List<ReviewReport> getRecentReviews(int limit) {
        return reviewCache.values().stream()
                .sorted((r1, r2) -> r2.getReviewTime().compareTo(r1.getReviewTime()))
                .limit(limit)
                .toList();
    }

    /**
     * 清除所有缓存
     */
    public void clearAll() {
        log.info("清除所有审查缓存");
        reviewCache.clear();
        codeFingerprintCache.clear();
        repositoryStats.clear();
    }

    /**
     * 计算代码指纹（简单的哈希）
     * 
     * @param code 代码内容
     * @return 代码指纹
     */
    public String computeFingerprint(String code) {
        // 移除空白字符后计算哈希
        String normalized = code.replaceAll("\\s+", "");
        return String.valueOf(normalized.hashCode());
    }

    /**
     * 检查审查报告是否过期
     * 
     * @param report 审查报告
     * @return 如果过期返回 true
     */
    private boolean isExpired(ReviewReport report) {
        if (report.getReviewTime() == null) {
            return true;
        }
        return System.currentTimeMillis() - 
               report.getReviewTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
               > CACHE_EXPIRY_MS;
    }

    /**
     * 更新仓库统计
     * 
     * @param report 审查报告
     */
    private void updateRepositoryStats(ReviewReport report) {
        String repository = report.getPrInfo() != null ? report.getPrInfo().getRepo() : null;
        if (repository == null) {
            return;
        }

        RepositoryStats stats = repositoryStats.computeIfAbsent(
                repository,
                RepositoryStats::new
        );

        stats.incrementTotalReviews();
        stats.addIssues(report.getIssueCount());
        stats.addCriticalIssues(report.getCriticalCount());
        stats.updateLastReviewTime();
    }

    /**
     * 清理过期缓存
     */
    private void cleanupCache() {
        // 如果缓存超过最大大小，移除最旧的条目
        if (reviewCache.size() > MAX_CACHE_SIZE) {
            log.debug("缓存超过最大限制，开始清理");
            
            reviewCache.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getValue().getReviewTime()))
                    .limit(reviewCache.size() - MAX_CACHE_SIZE)
                    .forEach(e -> reviewCache.remove(e.getKey()));
        }
    }

    // ==================== 仓库统计类 ====================

    /**
     * 仓库审查统计
     */
    public static class RepositoryStats {
        private final String repository;
        private int totalReviews = 0;
        private int totalIssues = 0;
        private int totalCriticalIssues = 0;
        private LocalDateTime lastReviewTime;

        public RepositoryStats(String repository) {
            this.repository = repository;
        }

        public void incrementTotalReviews() {
            this.totalReviews++;
        }

        public void addIssues(int count) {
            this.totalIssues += count;
        }

        public void addCriticalIssues(int count) {
            this.totalCriticalIssues += count;
        }

        public void updateLastReviewTime() {
            this.lastReviewTime = LocalDateTime.now();
        }

        // Getters
        public String getRepository() {
            return repository;
        }

        public int getTotalReviews() {
            return totalReviews;
        }

        public int getTotalIssues() {
            return totalIssues;
        }

        public int getTotalCriticalIssues() {
            return totalCriticalIssues;
        }

        public LocalDateTime getLastReviewTime() {
            return lastReviewTime;
        }

        /**
         * 计算平均每个 PR 的问题数
         * 
         * @return 平均问题数
         */
        public double getAverageIssuesPerReview() {
            return totalReviews > 0 ? (double) totalIssues / totalReviews : 0;
        }

        @Override
        public String toString() {
            return String.format(
                "RepositoryStats{repository='%s', totalReviews=%d, totalIssues=%d, avgIssues=%.2f}",
                repository, totalReviews, totalIssues, getAverageIssuesPerReview()
            );
        }
    }
}
