package com.aicode.review.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PR 信息模型 - GitHub/GitLab 通用
 *
 * @author AI Code Review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PRInfo {

    /**
     * 提供商：github / gitlab
     */
    private String provider;

    /**
     * 仓库所有者
     */
    private String owner;

    /**
     * 仓库名
     */
    private String repo;

    /**
     * PR/MR 编号
     */
    private int prNumber;

    /**
     * PR 标题
     */
    private String title;

    /**
     * PR 描述
     */
    private String description;

    /**
     * 作者
     */
    private String author;

    /**
     * 源分支
     */
    private String sourceBranch;

    /**
     * 目标分支
     */
    private String targetBranch;

    /**
     * PR URL
     */
    private String url;

    /**
     * 获取会话 ID
     */
    public String getSessionId() {
        return String.format("%s/%s#%d", owner, repo, prNumber);
    }

    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return String.format("%s/%s PR #%d", owner, repo, prNumber);
    }
}
