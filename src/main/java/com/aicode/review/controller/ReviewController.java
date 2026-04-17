package com.aicode.review.controller;

import com.aicode.review.model.ReviewReport;
import com.aicode.review.service.CodeReviewService;
import com.aicode.review.service.ReportExportService;
import com.aicode.review.service.ReviewProgressService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码审查 API 控制器
 *
 * 提供审查进度查询、报告导出等功能
 *
 * @author AI Code Review Team
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final CodeReviewService codeReviewService;
    private final ReportExportService reportExportService;
    private final ReviewProgressService progressService;
    
    // 存储正在进行的审查任务
    private final Map<String, CompletableFuture<ReviewReport>> activeTasks = new ConcurrentHashMap<>();
    // 存储已完成的审查报告
    private final Map<String, ReviewReport> completedReports = new ConcurrentHashMap<>();

    /**
     * 启动分支代码审查（异步，带进度推送）
     *
     * @param request 审查请求
     * @return 任务ID
     */
    @PostMapping("/review/branch")
    public ResponseEntity<?> startBranchReview(@RequestBody BranchReviewRequest request) {
        String taskId = request.getTaskId() != null ? request.getTaskId() : progressService.createTaskId();
        
        log.info("启动分支审查任务: {}, projectId: {}, branch: {}", taskId, request.getProjectId(), request.getBranch());
        
        // 创建异步任务
        final String finalTaskId = taskId;
        CompletableFuture<ReviewReport> future = CompletableFuture.supplyAsync(() -> {
            try {
                var callback = progressService.createProgressCallback(finalTaskId);
                ReviewReport report = codeReviewService.reviewBranchWithProgress(
                    request.getProjectId(), 
                    request.getBranch(),
                    finalTaskId,
                    callback
                );
                report.setTaskId(finalTaskId);
                completedReports.put(finalTaskId, report);
                return report;
            } catch (Exception e) {
                log.error("审查任务 {} 失败", finalTaskId, e);
                progressService.sendError(finalTaskId, e.getMessage());
                throw new RuntimeException(e);
            } finally {
                activeTasks.remove(finalTaskId);
            }
        });
        
        activeTasks.put(taskId, future);
        
        return ResponseEntity.ok().body(Map.of(
            "status", "started",
            "taskId", taskId,
            "message", "审查任务已启动，请通过 WebSocket 订阅进度"
        ));
    }

    /**
     * 查询审查任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/review/{taskId}/status")
    public ResponseEntity<?> getReviewStatus(@PathVariable String taskId) {
        if (completedReports.containsKey(taskId)) {
            ReviewReport report = completedReports.get(taskId);
            return ResponseEntity.ok().body(Map.of(
                "status", "completed",
                "taskId", taskId,
                "issueCount", report.getIssueCount(),
                "criticalCount", report.getCriticalCount(),
                "warningCount", report.getWarningCount(),
                "infoCount", report.getInfoCount()
            ));
        }
        
        if (activeTasks.containsKey(taskId)) {
            return ResponseEntity.ok().body(Map.of(
                "status", "running",
                "taskId", taskId,
                "message", "审查进行中"
            ));
        }
        
        return ResponseEntity.ok().body(Map.of(
            "status", "not_found",
            "taskId", taskId,
            "message", "任务不存在或已过期"
        ));
    }

    /**
     * 获取审查报告
     *
     * @param taskId 任务ID
     * @return 审查报告
     */
    @GetMapping("/review/{taskId}/report")
    public ResponseEntity<?> getReviewReport(@PathVariable String taskId) {
        ReviewReport report = completedReports.get(taskId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().body(report);
    }

    /**
     * 导出审查报告
     *
     * @param taskId 任务ID
     * @param format 导出格式 (html, json, pdf, markdown)
     * @return 导出的文件
     */
    @GetMapping("/reports/{taskId}/export")
    public ResponseEntity<?> exportReport(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "html") String format) {
        
        log.info("导出报告: taskId={}, format={}", taskId, format);
        
        ReviewReport report = getReport(taskId);
        if (report == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "报告不存在或任务未完成"));
        }
        
        try {
            byte[] content = reportExportService.exportReport(report, format);
            String mimeType = reportExportService.getMimeType(format);
            String extension = reportExportService.getFileExtension(format);
            String filename = String.format("review-report-%s.%s", taskId, extension);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(mimeType))
                    .body(content);
                    
        } catch (Exception e) {
            log.error("导出报告失败", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "导出失败: " + e.getMessage()));
        }
    }

    /**
     * 直接查看 HTML 报告（浏览器中打开）
     *
     * @param taskId 任务ID
     * @return HTML 报告内容
     */
    @GetMapping("/reports/{taskId}/view")
    public ResponseEntity<?> viewReport(@PathVariable String taskId) {
        log.info("查看报告: taskId={}", taskId);
        
        ReviewReport report = getReport(taskId);
        if (report == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("<html><body><h1>报告不存在或任务未完成</h1></body></html>");
        }
        
        try {
            byte[] content = reportExportService.exportReport(report, "html");
            
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
                    
        } catch (Exception e) {
            log.error("查看报告失败", e);
            return ResponseEntity.internalServerError()
                    .body("<html><body><h1>查看报告失败: " + e.getMessage() + "</h1></body></html>");
        }
    }

    /**
     * 获取报告（从已完成或活跃任务中）
     */
    private ReviewReport getReport(String taskId) {
        ReviewReport report = completedReports.get(taskId);
        if (report == null) {
            // 尝试从活跃任务中获取
            CompletableFuture<ReviewReport> future = activeTasks.get(taskId);
            if (future != null && future.isDone()) {
                try {
                    report = future.get();
                    completedReports.put(taskId, report);
                } catch (Exception e) {
                    log.error("获取任务结果失败", e);
                }
            }
        }
        return report;
    }

    /**
     * 获取所有已完成的审查报告列表
     *
     * @return 报告列表
     */
    @GetMapping("/reports")
    public ResponseEntity<?> listReports() {
        var reports = completedReports.entrySet().stream()
            .map(entry -> {
                ReviewReport report = entry.getValue();
                return Map.of(
                    "taskId", entry.getKey(),
                    "reportId", report.getReportId(),
                    "reviewTime", report.getReviewTime(),
                    "issueCount", report.getIssueCount(),
                    "status", report.getStatus()
                );
            })
            .toList();
        
        return ResponseEntity.ok().body(Map.of(
            "total", reports.size(),
            "reports", reports
        ));
    }

    /**
     * 删除审查报告
     *
     * @param taskId 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/reports/{taskId}")
    public ResponseEntity<?> deleteReport(@PathVariable String taskId) {
        completedReports.remove(taskId);
        activeTasks.remove(taskId);
        return ResponseEntity.ok().body(Map.of(
            "status", "deleted",
            "taskId", taskId
        ));
    }

    /**
     * 分支审查请求
     */
    @Data
    public static class BranchReviewRequest {
        private String projectId;
        private String branch;
        private String taskId;
    }
}
