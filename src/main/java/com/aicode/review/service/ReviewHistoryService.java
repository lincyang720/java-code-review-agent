package com.aicode.review.service;

import com.aicode.review.model.ProjectStats;
import com.aicode.review.model.ReviewHistory;
import com.aicode.review.model.ReviewReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 审查历史服务
 * 
 * 管理审查历史记录的存储、查询和统计。
 * 使用内存存储，适合单机部署。如需分布式部署，可替换为数据库存储。
 * 
 * @author AI Code Review Team
 */
@Slf4j
@Service
public class ReviewHistoryService {

    // 历史记录存储：historyId -> ReviewHistory
    private final Map<String, ReviewHistory> historyStore = new ConcurrentHashMap<>();
    
    // 项目历史索引：projectId -> List<historyId>
    private final Map<String, List<String>> projectHistoryIndex = new ConcurrentHashMap<>();
    
    // 任务到历史的映射：taskId -> historyId
    private final Map<String, String> taskToHistoryMap = new ConcurrentHashMap<>();
    
    // 项目统计缓存：projectId -> ProjectStats
    private final Map<String, ProjectStats> projectStatsCache = new ConcurrentHashMap<>();

    /**
     * 保存审查历史
     */
    public ReviewHistory saveHistory(ReviewHistory history) {
        historyStore.put(history.getHistoryId(), history);
        taskToHistoryMap.put(history.getTaskId(), history.getHistoryId());
        
        // 更新项目索引
        projectHistoryIndex.computeIfAbsent(history.getProjectId(), k -> new ArrayList<>())
                .add(history.getHistoryId());
        
        // 更新项目统计
        updateProjectStats(history);
        
        log.info("保存审查历史: {}, 项目: {}", history.getHistoryId(), history.getProjectId());
        return history;
    }

    /**
     * 从审查报告创建并保存历史记录
     */
    public ReviewHistory saveFromReport(ReviewReport report, String projectId, String branch, 
                                       ReviewHistory.TriggerType triggerType, String triggeredBy) {
        ReviewHistory history = ReviewHistory.fromReport(report, projectId, branch);
        history.setTriggerType(triggerType);
        history.setTriggeredBy(triggeredBy);
        history.setEndTime(java.time.LocalDateTime.now());
        
        // 从报告中提取项目信息
        if (report.getPrInfo() != null) {
            history.setPrTitle(report.getPrInfo().getTitle());
        }
        if (report.getRepository() != null) {
            history.setRepositoryUrl(report.getRepository());
            // 尝试从仓库URL提取项目名称
            String repoName = extractRepoName(report.getRepository());
            history.setProjectName(repoName);
        }
        
        return saveHistory(history);
    }

    /**
     * 根据任务ID获取历史记录
     */
    public Optional<ReviewHistory> getHistoryByTaskId(String taskId) {
        String historyId = taskToHistoryMap.get(taskId);
        if (historyId != null) {
            return Optional.ofNullable(historyStore.get(historyId));
        }
        return Optional.empty();
    }

    /**
     * 根据历史ID获取历史记录
     */
    public Optional<ReviewHistory> getHistoryById(String historyId) {
        return Optional.ofNullable(historyStore.get(historyId));
    }

    /**
     * 获取项目的所有历史记录
     */
    public List<ReviewHistory> getProjectHistory(String projectId) {
        List<String> historyIds = projectHistoryIndex.getOrDefault(projectId, Collections.emptyList());
        return historyIds.stream()
                .map(historyStore::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ReviewHistory::getStartTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取所有项目的历史记录（分页）
     */
    public List<ReviewHistory> getAllHistory(int page, int size) {
        return historyStore.values().stream()
                .sorted(Comparator.comparing(ReviewHistory::getStartTime).reversed())
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有项目列表
     */
    public List<ProjectStats> getAllProjects() {
        return new ArrayList<>(projectStatsCache.values());
    }

    /**
     * 获取项目统计
     */
    public Optional<ProjectStats> getProjectStats(String projectId) {
        return Optional.ofNullable(projectStatsCache.get(projectId));
    }

    /**
     * 获取最近的历史记录
     */
    public List<ReviewHistory> getRecentHistory(int limit) {
        return historyStore.values().stream()
                .sorted(Comparator.comparing(ReviewHistory::getStartTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取全局统计
     */
    public GlobalStats getGlobalStats() {
        GlobalStats stats = new GlobalStats();
        stats.setTotalReviews(historyStore.size());
        stats.setTotalProjects(projectStatsCache.size());
        
        int totalIssues = projectStatsCache.values().stream()
                .mapToInt(ProjectStats::getTotalIssues)
                .sum();
        stats.setTotalIssues(totalIssues);
        
        int totalCritical = projectStatsCache.values().stream()
                .mapToInt(ProjectStats::getTotalCritical)
                .sum();
        stats.setTotalCritical(totalCritical);
        
        // 计算平均质量分
        double avgScore = projectStatsCache.values().stream()
                .mapToInt(ProjectStats::getQualityScore)
                .average()
                .orElse(100.0);
        stats.setAverageQualityScore((int) avgScore);
        
        // 今日审查数
        String today = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        long todayReviews = historyStore.values().stream()
                .filter(h -> h.getStartTime() != null && 
                        h.getStartTime().format(DateTimeFormatter.ISO_DATE).equals(today))
                .count();
        stats.setTodayReviews((int) todayReviews);
        
        return stats;
    }

    /**
     * 删除历史记录
     */
    public void deleteHistory(String historyId) {
        ReviewHistory history = historyStore.remove(historyId);
        if (history != null) {
            taskToHistoryMap.remove(history.getTaskId());
            
            List<String> projectHistory = projectHistoryIndex.get(history.getProjectId());
            if (projectHistory != null) {
                projectHistory.remove(historyId);
            }
            
            // 重新计算项目统计
            recalculateProjectStats(history.getProjectId());
        }
    }

    /**
     * 更新项目统计
     */
    private void updateProjectStats(ReviewHistory history) {
        ProjectStats stats = projectStatsCache.computeIfAbsent(history.getProjectId(), 
                pid -> ProjectStats.builder()
                        .projectId(pid)
                        .projectName(history.getProjectName())
                        .repositoryUrl(history.getRepositoryUrl())
                        .platform(history.getPlatform())
                        .build());
        
        stats.updateWithHistory(history);
        
        // 更新趋势数据
        if (history.getStartTime() != null) {
            String date = history.getStartTime().format(DateTimeFormatter.ISO_DATE);
            stats.getDailyReviewTrend().merge(date, 1, Integer::sum);
            if (history.getIssueStats() != null) {
                stats.getDailyIssueTrend().merge(date, history.getIssueStats().getTotal(), Integer::sum);
            }
        }
    }

    /**
     * 重新计算项目统计
     */
    private void recalculateProjectStats(String projectId) {
        List<ReviewHistory> histories = getProjectHistory(projectId);
        
        ProjectStats stats = ProjectStats.builder()
                .projectId(projectId)
                .build();
        
        for (ReviewHistory history : histories) {
            stats.updateWithHistory(history);
        }
        
        projectStatsCache.put(projectId, stats);
    }

    /**
     * 从仓库URL提取项目名称
     */
    private String extractRepoName(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isEmpty()) {
            return "Unknown";
        }
        
        // 处理各种格式的URL
        // https://github.com/user/repo.git
        // https://gitlab.com/user/repo
        // git@github.com:user/repo.git
        
        String url = repositoryUrl.replace(".git", "");
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0) {
            return url.substring(lastSlash + 1);
        }
        
        int lastColon = url.lastIndexOf(':');
        if (lastColon > 0) {
            return url.substring(lastColon + 1);
        }
        
        return url;
    }

    /**
     * 全局统计
     */
    @lombok.Data
    public static class GlobalStats {
        private int totalReviews;
        private int totalProjects;
        private int totalIssues;
        private int totalCritical;
        private int todayReviews;
        private int averageQualityScore;
    }
}
