package com.aicode.review.service;

import com.aicode.review.config.ReviewProgressWebSocketHandler;
import com.aicode.review.model.ReviewProgressMessage;
import com.aicode.review.model.ReviewReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 审查进度服务
 *
 * 封装进度推送逻辑，支持 WebSocket 实时推送和回调函数
 *
 * @author AI Code Review Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewProgressService {

    private final ReviewProgressWebSocketHandler webSocketHandler;

    /**
     * 创建新的审查任务ID
     */
    public String createTaskId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 发送审查开始消息
     */
    public void sendStarted(String taskId, int totalFiles) {
        webSocketHandler.sendProgress(taskId, ReviewProgressMessage.started(taskId, totalFiles));
    }

    /**
     * 发送进度更新
     */
    public void sendProgress(String taskId, int current, int total, String filePath) {
        webSocketHandler.sendProgress(taskId, ReviewProgressMessage.progress(taskId, current, total, filePath));
    }

    /**
     * 发送单个文件完成消息
     */
    public void sendFileComplete(String taskId, int current, int total, String filePath, int issueCount) {
        webSocketHandler.sendProgress(taskId, 
            ReviewProgressMessage.fileComplete(taskId, current, total, filePath, issueCount));
    }

    /**
     * 发送审查完成消息
     */
    public void sendCompleted(String taskId, ReviewReport report) {
        webSocketHandler.sendProgress(taskId, ReviewProgressMessage.completed(taskId, report));
    }

    /**
     * 发送错误消息
     */
    public void sendError(String taskId, String error) {
        webSocketHandler.sendProgress(taskId, ReviewProgressMessage.error(taskId, error));
    }

    /**
     * 发送日志消息
     */
    public void sendLog(String taskId, String message) {
        webSocketHandler.sendProgress(taskId, ReviewProgressMessage.log(taskId, message));
    }

    /**
     * 创建进度回调函数（用于 CodeReviewService）
     */
    public Consumer<ProgressEvent> createProgressCallback(String taskId) {
        return event -> {
            switch (event.getType()) {
                case STARTED -> sendStarted(taskId, event.getTotal());
                case PROGRESS -> sendProgress(taskId, event.getCurrent(), event.getTotal(), event.getFilePath());
                case FILE_COMPLETE -> sendFileComplete(taskId, event.getCurrent(), event.getTotal(), 
                                                        event.getFilePath(), event.getIssueCount());
                case COMPLETED -> {
                    if (event.getReport() != null) {
                        sendCompleted(taskId, event.getReport());
                    }
                }
                case ERROR -> sendError(taskId, event.getMessage());
                case LOG -> sendLog(taskId, event.getMessage());
            }
        };
    }

    /**
     * 进度事件
     */
    public static class ProgressEvent {
        private final EventType type;
        private final String taskId;
        private final String message;
        private final Integer current;
        private final Integer total;
        private final String filePath;
        private final Integer issueCount;
        private final ReviewReport report;

        private ProgressEvent(Builder builder) {
            this.type = builder.type;
            this.taskId = builder.taskId;
            this.message = builder.message;
            this.current = builder.current;
            this.total = builder.total;
            this.filePath = builder.filePath;
            this.issueCount = builder.issueCount;
            this.report = builder.report;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public EventType getType() { return type; }
        public String getTaskId() { return taskId; }
        public String getMessage() { return message; }
        public Integer getCurrent() { return current; }
        public Integer getTotal() { return total; }
        public String getFilePath() { return filePath; }
        public Integer getIssueCount() { return issueCount; }
        public ReviewReport getReport() { return report; }

        public enum EventType {
            STARTED, PROGRESS, FILE_COMPLETE, COMPLETED, ERROR, LOG
        }

        public static class Builder {
            private EventType type;
            private String taskId;
            private String message;
            private Integer current;
            private Integer total;
            private String filePath;
            private Integer issueCount;
            private ReviewReport report;

            public Builder type(EventType type) {
                this.type = type;
                return this;
            }

            public Builder taskId(String taskId) {
                this.taskId = taskId;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder current(Integer current) {
                this.current = current;
                return this;
            }

            public Builder total(Integer total) {
                this.total = total;
                return this;
            }

            public Builder filePath(String filePath) {
                this.filePath = filePath;
                return this;
            }

            public Builder issueCount(Integer issueCount) {
                this.issueCount = issueCount;
                return this;
            }

            public Builder report(ReviewReport report) {
                this.report = report;
                return this;
            }

            public ProgressEvent build() {
                return new ProgressEvent(this);
            }
        }
    }
}
