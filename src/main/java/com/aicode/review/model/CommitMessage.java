package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交信息模型
 * 
 * 存储 Git 提交的相关信息，用于分析提交历史、提交规范等。
 * 
 * @author AI Code Review Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessage {

    /**
     * 提交 SHA
     */
    private String sha;

    /**
     * 提交信息
     */
    private String message;

    /**
     * 提交者名称
     */
    private String authorName;

    /**
     * 提交者邮箱
     */
    private String authorEmail;

    /**
     * 提交时间
     */
    private LocalDateTime commitTime;

    /**
     * 父提交 SHA 列表
     */
    private List<String> parentShas;

    /**
     * 变更的文件列表
     */
    private List<String> changedFiles;

    /**
     * 新增行数
     */
    private int additions;

    /**
     * 删除行数
     */
    private int deletions;

    /**
     * 提交信息分析结果
     */
    private MessageAnalysis analysis;

    /**
     * 提交信息分析结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageAnalysis {
        /**
         * 是否符合规范
         */
        private boolean isConventional;

        /**
         * 提交类型 (feat, fix, docs, style, refactor, test, chore)
         */
        private String type;

        /**
         * 提交范围（可选）
         */
        private String scope;

        /**
         * 提交描述
         */
        private String subject;

        /**
         * 提交正文
         */
        private String body;

        /**
         * 是否包含破坏性变更标记 (! 或 BREAKING CHANGE)
         */
        private boolean isBreakingChange;

        /**
         * 问题列表
         */
        private List<String> issues;
    }

    /**
     * 获取提交信息的简短版本（第一行）
     * 
     * @return 提交信息的第一行
     */
    public String getShortMessage() {
        if (message == null) {
            return "";
        }
        int index = message.indexOf('\n');
        return index > 0 ? message.substring(0, index).trim() : message.trim();
    }

    /**
     * 检查提交信息是否符合 Conventional Commits 规范
     * 
     * @return 如果符合规范返回 true
     */
    public boolean isConventionalCommit() {
        if (message == null) {
            return false;
        }
        String shortMsg = getShortMessage();
        // 匹配格式: type(scope): subject 或 type: subject
        return shortMsg.matches("^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)(\\(.+\\))?!?: .+");
    }

    /**
     * 获取提交类型
     * 
     * @return 提交类型，如果不是规范提交返回 null
     */
    public String getCommitType() {
        if (!isConventionalCommit()) {
            return null;
        }
        String shortMsg = getShortMessage();
        int colonIndex = shortMsg.indexOf(':');
        if (colonIndex > 0) {
            String typeAndScope = shortMsg.substring(0, colonIndex);
            int scopeStart = typeAndScope.indexOf('(');
            return scopeStart > 0 ? typeAndScope.substring(0, scopeStart) : typeAndScope;
        }
        return null;
    }

    /**
     * 检查是否为破坏性变更
     * 
     * @return 如果是破坏性变更返回 true
     */
    public boolean isBreakingChange() {
        if (message == null) {
            return false;
        }
        String shortMsg = getShortMessage();
        return shortMsg.contains("!:") || message.contains("BREAKING CHANGE:");
    }

    /**
     * 转换为 Markdown 格式
     * 
     * @return Markdown 格式的提交信息
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(getShortMessage()).append("**\n\n");
        sb.append("- 作者: ").append(authorName).append("\n");
        sb.append("- 时间: ").append(commitTime).append("\n");
        sb.append("- SHA: `").append(sha.substring(0, 7)).append("`\n");
        sb.append("- 变更: +").append(additions).append(" -").append(deletions).append("\n");
        
        if (!isConventionalCommit()) {
            sb.append("- ⚠️ 提交信息不符合 Conventional Commits 规范\n");
        }
        
        return sb.toString();
    }
}
