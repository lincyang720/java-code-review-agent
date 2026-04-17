package com.aicode.review.service;

import com.aicode.review.client.GitLabClient;
import com.aicode.review.model.CommitMessage;
import com.aicode.review.model.PRDifferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Git 服务
 *
 * 封装 GitHub/GitLab 的通用操作，提供统一的代码获取接口。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Service
public class GitService {

    @Autowired
    private GitLabClient gitLabClient;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 从 Webhook 负载解析 PR/MR 信息并获取差异
     *
     * @param payload     Webhook 请求体
     * @param platform    平台类型 (github/gitlab)
     * @return PR 差异信息
     */
    public PRDifferences getDiffFromWebhook(String payload, String platform) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            if ("gitlab".equals(platform)) {
                return parseGitLabWebhook(json);
            } else {
                return parseGitHubWebhook(json);
            }
        } catch (Exception e) {
            log.error("解析 Webhook 失败", e);
            return null;
        }
    }

    /**
     * 解析 GitLab Webhook
     */
    private PRDifferences parseGitLabWebhook(JsonNode json) {
        try {
            JsonNode attributes = json.path("object_attributes");
            String projectId = json.path("project").path("id").asText();
            int mrIid = attributes.path("iid").asInt();

            PRDifferences diff = PRDifferences.builder()
                    .prNumber(String.valueOf(mrIid))
                    .title(attributes.path("title").asText())
                    .description(attributes.path("description").asText())
                    .sourceBranch(attributes.path("source_branch").asText())
                    .targetBranch(attributes.path("target_branch").asText())
                    .repository(json.path("project").path("path_with_namespace").asText())
                    .author(attributes.path("author_id").asText())
                    .build();

            // 获取代码差异
            List<PRDifferences.FileDiff> fileDiffs = gitLabClient.getMergeRequestDiffs(projectId, mrIid);
            diff.setFiles(fileDiffs);

            return diff;
        } catch (Exception e) {
            log.error("解析 GitLab Webhook 失败", e);
            return null;
        }
    }

    /**
     * 解析 GitHub Webhook
     */
    private PRDifferences parseGitHubWebhook(JsonNode json) {
        try {
            JsonNode pullRequest = json.path("pull_request");

            PRDifferences diff = PRDifferences.builder()
                    .prNumber(String.valueOf(pullRequest.path("number").asInt()))
                    .title(pullRequest.path("title").asText())
                    .description(pullRequest.path("body").asText())
                    .sourceBranch(pullRequest.path("head").path("ref").asText())
                    .targetBranch(pullRequest.path("base").path("ref").asText())
                    .repository(json.path("repository").path("full_name").asText())
                    .author(pullRequest.path("user").path("login").asText())
                    .build();

            // TODO: 获取 GitHub PR 差异

            return diff;
        } catch (Exception e) {
            log.error("解析 GitHub Webhook 失败", e);
            return null;
        }
    }

    /**
     * 获取提交信息
     *
     * @param payload  Webhook 负载
     * @param platform 平台类型
     * @return 提交信息
     */
    public CommitMessage getCommitInfo(String payload, String platform) {
        try {
            JsonNode json = objectMapper.readTree(payload);

            if ("gitlab".equals(platform)) {
                return parseGitLabCommit(json);
            } else {
                return parseGitHubCommit(json);
            }
        } catch (Exception e) {
            log.error("解析提交信息失败", e);
            return null;
        }
    }

    private CommitMessage parseGitLabCommit(JsonNode json) {
        JsonNode lastCommit = json.path("object_attributes").path("last_commit");

        return CommitMessage.builder()
                .sha(lastCommit.path("id").asText())
                .message(lastCommit.path("message").asText())
                .authorName(lastCommit.path("author").path("name").asText())
                .authorEmail(lastCommit.path("author").path("email").asText())
                .build();
    }

    private CommitMessage parseGitHubCommit(JsonNode json) {
        // GitHub Webhook 不直接包含提交信息，需要额外获取
        return null;
    }

    /**
     * 获取 GitLab 项目列表（用于测试连接）
     *
     * @return 连接测试结果
     */
    public String testGitLabConnection() {
        return gitLabClient.testConnection();
    }
}
