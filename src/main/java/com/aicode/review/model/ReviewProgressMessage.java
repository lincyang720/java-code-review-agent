package com.aicode.review.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审查进度消息
 * 
 * 用于 WebSocket 实时推送审查进度到前端
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReviewProgressMessage {

    /**
     * 消息类型
     */
    private MessageType type;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 当前进度（0-100）
     */
    private Integer progress;

    /**
     * 当前处理数量
     */
    private Integer current;

    /**
     * 总数量
     */
    private Integer total;

    /**
     * 当前处理的文件路径
     */
    private String filePath;

    /**
     * 当前文件发现的问题数
     */
    private Integer issueCount;

    /**
     * 时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 附加数据（如完整的审查报告）
     */
    private Object data;

    /**
     * 消息类型枚举
     */
    public enum MessageType {
        CONNECTED,      // 连接成功
        SUBSCRIBED,     // 订阅成功
        STARTED,        // 审查开始
        PROGRESS,       // 进度更新
        FILE_COMPLETE,  // 单个文件完成
        COMPLETED,      // 全部完成
        ERROR,          // 错误
        LOG             // 普通日志
    }

    // ==================== 便捷工厂方法 ====================

    public static ReviewProgressMessage connected(String sessionId) {
        return ReviewProgressMessage.builder()
                .type(MessageType.CONNECTED)
                .message("WebSocket 连接成功")
                .data(sessionId)
                .build();
    }

    public static ReviewProgressMessage subscribed(String taskId) {
        return ReviewProgressMessage.builder()
                .type(MessageType.SUBSCRIBED)
                .taskId(taskId)
                .message("已订阅任务: " + taskId)
                .build();
    }

    public static ReviewProgressMessage started(String taskId, int totalFiles) {
        return ReviewProgressMessage.builder()
                .type(MessageType.STARTED)
                .taskId(taskId)
                .message("开始审查，共 " + totalFiles + " 个文件")
                .total(totalFiles)
                .current(0)
                .progress(0)
                .build();
    }

    public static ReviewProgressMessage progress(String taskId, int current, int total, String filePath) {
        int progress = total > 0 ? (int) ((current * 100.0) / total) : 0;
        return ReviewProgressMessage.builder()
                .type(MessageType.PROGRESS)
                .taskId(taskId)
                .message("正在审查: " + filePath)
                .current(current)
                .total(total)
                .progress(progress)
                .filePath(filePath)
                .build();
    }

    public static ReviewProgressMessage fileComplete(String taskId, int current, int total, 
                                                      String filePath, int issueCount) {
        int progress = total > 0 ? (int) ((current * 100.0) / total) : 0;
        String msg = issueCount > 0 
                ? String.format("[%d/%d] %s 审查完成，发现 %d 个问题", current, total, filePath, issueCount)
                : String.format("[%d/%d] %s 审查完成，未发现问题", current, total, filePath);
        
        return ReviewProgressMessage.builder()
                .type(MessageType.FILE_COMPLETE)
                .taskId(taskId)
                .message(msg)
                .current(current)
                .total(total)
                .progress(progress)
                .filePath(filePath)
                .issueCount(issueCount)
                .build();
    }

    public static ReviewProgressMessage completed(String taskId, ReviewReport report) {
        return ReviewProgressMessage.builder()
                .type(MessageType.COMPLETED)
                .taskId(taskId)
                .message(String.format("审查完成！共发现 %d 个问题（严重: %d, 警告: %d, 建议: %d）",
                        report.getIssueCount(),
                        report.getCriticalCount(),
                        report.getWarningCount(),
                        report.getInfoCount()))
                .progress(100)
                .data(report)
                .build();
    }

    public static ReviewProgressMessage error(String taskId, String error) {
        return ReviewProgressMessage.builder()
                .type(MessageType.ERROR)
                .taskId(taskId)
                .message("审查失败: " + error)
                .build();
    }

    public static ReviewProgressMessage log(String taskId, String message) {
        return ReviewProgressMessage.builder()
                .type(MessageType.LOG)
                .taskId(taskId)
                .message(message)
                .build();
    }
}
