package com.aicode.review.controller;

import com.aicode.review.model.ProjectStats;
import com.aicode.review.model.ReviewHistory;
import com.aicode.review.model.ReviewReport;
import com.aicode.review.service.ReviewHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台管理 Dashboard API
 * 
 * 提供项目列表、审查历史、统计信息等数据接口。
 * 
 * @author AI Code Review Team
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ReviewHistoryService historyService;

    /**
     * 获取全局统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getGlobalStats() {
        ReviewHistoryService.GlobalStats stats = historyService.getGlobalStats();
        
        return ResponseEntity.ok().body(Map.of(
                "totalReviews", stats.getTotalReviews(),
                "totalProjects", stats.getTotalProjects(),
                "totalIssues", stats.getTotalIssues(),
                "totalCritical", stats.getTotalCritical(),
                "todayReviews", stats.getTodayReviews(),
                "averageQualityScore", stats.getAverageQualityScore()
        ));
    }

    /**
     * 获取所有项目列表
     */
    @GetMapping("/projects")
    public ResponseEntity<?> getAllProjects() {
        List<ProjectStats> projects = historyService.getAllProjects();
        
        List<Map<String, Object>> projectList = projects.stream()
                .map(this::convertProjectToMap)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok().body(Map.of(
                "total", projectList.size(),
                "projects", projectList
        ));
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/projects/{projectId}")
    public ResponseEntity<?> getProjectDetail(@PathVariable String projectId) {
        Optional<ProjectStats> statsOpt = historyService.getProjectStats(projectId);
        
        if (statsOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ProjectStats stats = statsOpt.get();
        List<ReviewHistory> histories = historyService.getProjectHistory(projectId);
        
        return ResponseEntity.ok().body(Map.of(
                "project", convertProjectToMap(stats),
                "recentReviews", histories.stream()
                        .limit(10)
                        .map(this::convertHistoryToMap)
                        .collect(Collectors.toList()),
                "totalReviews", histories.size()
        ));
    }

    /**
     * 获取项目的审查历史
     */
    @GetMapping("/projects/{projectId}/history")
    public ResponseEntity<?> getProjectHistory(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<ReviewHistory> histories = historyService.getProjectHistory(projectId);
        
        int total = histories.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<Map<String, Object>> pageData = histories.stream()
                .skip(start)
                .limit(size)
                .map(this::convertHistoryToMap)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok().body(Map.of(
                "total", total,
                "page", page,
                "size", size,
                "data", pageData
        ));
    }

    /**
     * 获取最近审查历史
     */
    @GetMapping("/history/recent")
    public ResponseEntity<?> getRecentHistory(
            @RequestParam(defaultValue = "20") int limit) {
        
        List<ReviewHistory> histories = historyService.getRecentHistory(limit);
        
        List<Map<String, Object>> historyList = histories.stream()
                .map(this::convertHistoryToMap)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok().body(Map.of(
                "total", historyList.size(),
                "history", historyList
        ));
    }

    /**
     * 获取所有审查历史（分页）
     */
    @GetMapping("/history")
    public ResponseEntity<?> getAllHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        List<ReviewHistory> histories = historyService.getAllHistory(page, size);
        int total = historyService.getGlobalStats().getTotalReviews();
        
        List<Map<String, Object>> historyList = histories.stream()
                .map(this::convertHistoryToMap)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok().body(Map.of(
                "total", total,
                "page", page,
                "size", size,
                "data", historyList
        ));
    }

    /**
     * 获取单个历史记录详情
     */
    @GetMapping("/history/{historyId}")
    public ResponseEntity<?> getHistoryDetail(@PathVariable String historyId) {
        Optional<ReviewHistory> historyOpt = historyService.getHistoryById(historyId);
        
        if (historyOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok().body(convertHistoryToMap(historyOpt.get()));
    }

    /**
     * 删除历史记录
     */
    @DeleteMapping("/history/{historyId}")
    public ResponseEntity<?> deleteHistory(@PathVariable String historyId) {
        historyService.deleteHistory(historyId);
        return ResponseEntity.ok().body(Map.of(
                "status", "deleted",
                "historyId", historyId
        ));
    }

    /**
     * 获取趋势数据
     */
    @GetMapping("/trends")
    public ResponseEntity<?> getTrends(
            @RequestParam(defaultValue = "7") int days) {
        
        List<ProjectStats> projects = historyService.getAllProjects();
        
        // 合并所有项目的趋势数据
        Map<String, Integer> mergedReviewTrend = new HashMap<>();
        Map<String, Integer> mergedIssueTrend = new HashMap<>();
        
        for (ProjectStats project : projects) {
            if (project.getDailyReviewTrend() != null) {
                project.getDailyReviewTrend().forEach((date, count) -> 
                        mergedReviewTrend.merge(date, count, Integer::sum));
            }
            if (project.getDailyIssueTrend() != null) {
                project.getDailyIssueTrend().forEach((date, count) -> 
                        mergedIssueTrend.merge(date, count, Integer::sum));
            }
        }
        
        // 按日期排序
        List<String> sortedDates = mergedReviewTrend.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        
        List<Map<String, Object>> trendData = sortedDates.stream()
                .map(date -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", date);
                    map.put("reviews", mergedReviewTrend.getOrDefault(date, 0));
                    map.put("issues", mergedIssueTrend.getOrDefault(date, 0));
                    return map;
                })
                .collect(Collectors.toList());
        
        Map<String, Object> response = new HashMap<>();
        response.put("trends", trendData);
        return ResponseEntity.ok().body(response);
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> convertProjectToMap(ProjectStats stats) {
        Map<String, Object> map = new HashMap<>();
        map.put("projectId", stats.getProjectId());
        map.put("projectName", stats.getProjectName());
        map.put("repositoryUrl", stats.getRepositoryUrl());
        map.put("platform", stats.getPlatform());
        map.put("totalReviews", stats.getTotalReviews());
        map.put("successCount", stats.getSuccessCount());
        map.put("failedCount", stats.getFailedCount());
        map.put("totalIssues", stats.getTotalIssues());
        map.put("totalCritical", stats.getTotalCritical());
        map.put("totalWarning", stats.getTotalWarning());
        map.put("totalInfo", stats.getTotalInfo());
        map.put("avgIssuesPerReview", stats.getAvgIssuesPerReview());
        map.put("avgDurationMs", stats.getAvgDurationMs());
        map.put("lastReviewTime", stats.getLastReviewTime());
        map.put("lastReviewStatus", stats.getLastReviewStatus());
        map.put("qualityScore", stats.getQualityScore());
        map.put("dailyReviewTrend", stats.getDailyReviewTrend());
        map.put("dailyIssueTrend", stats.getDailyIssueTrend());
        return map;
    }

    private Map<String, Object> convertHistoryToMap(ReviewHistory history) {
        Map<String, Object> map = new HashMap<>();
        map.put("historyId", history.getHistoryId());
        map.put("taskId", history.getTaskId());
        map.put("projectId", history.getProjectId());
        map.put("projectName", history.getProjectName());
        map.put("branch", history.getBranch());
        map.put("prId", history.getPrId());
        map.put("prTitle", history.getPrTitle());
        map.put("triggerType", history.getTriggerType());
        map.put("platform", history.getPlatform());
        map.put("status", history.getStatus());
        map.put("issueStats", history.getIssueStats());
        map.put("fileStats", history.getFileStats());
        map.put("startTime", history.getStartTime());
        map.put("endTime", history.getEndTime());
        map.put("durationMs", history.getDurationMs());
        map.put("triggeredBy", history.getTriggeredBy());
        map.put("summary", history.getSummary());
        map.put("reportId", history.getReportId());
        return map;
    }
}
