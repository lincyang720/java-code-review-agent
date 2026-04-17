package com.aicode.review.client;

import com.aicode.review.model.ReviewReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * GitHub API 客户端
 *
 * 用于与 GitHub 交互，获取 PR 信息、代码差异、发表评论等。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Component
public class GitHubClient {

    @Value("${app.github.token:${GITHUB_TOKEN:}}")
    private String githubToken;

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitHubClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 在 PR 下发表评论
     *
     * @param owner      仓库所有者
     * @param repo       仓库名
     * @param prNumber   PR 编号
     * @param report     审查报告
     * @return 是否成功
     */
    public boolean postReviewComment(String owner, String repo, int prNumber, ReviewReport report) {
        String url = String.format("%s/repos/%s/%s/issues/%d/comments",
                GITHUB_API_BASE, owner, repo, prNumber);

        log.info("发表审查评论到 PR #{}: {}/{}", prNumber, owner, repo);

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String comment = report.toMarkdown();

        // GitHub 评论长度限制
        if (comment.length() > 65535) {
            comment = report.toCompactMarkdown();
        }

        String body = String.format("{\"body\":\"%s\"}",
                comment.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n"));

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("发表评论失败", e);
            return false;
        }
    }

    /**
     * 获取 PR 信息
     *
     * @param owner    仓库所有者
     * @param repo     仓库名
     * @param prNumber PR 编号
     * @return PR 信息
     */
    public JsonNode getPullRequest(String owner, String repo, int prNumber) {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                GITHUB_API_BASE, owner, repo, prNumber);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("获取 PR 信息失败", e);
            return null;
        }
    }

    /**
     * 获取 PR 的代码差异
     *
     * @param owner    仓库所有者
     * @param repo     仓库名
     * @param prNumber PR 编号
     * @return 代码差异文本
     */
    public String getPullRequestDiff(String owner, String repo, int prNumber) {
        String url = String.format("%s/repos/%s/%s/pulls/%d",
                GITHUB_API_BASE, owner, repo, prNumber);

        HttpHeaders headers = createHeaders();
        headers.set("Accept", "application/vnd.github.v3.diff");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取 PR 差异失败", e);
            return "";
        }
    }

    /**
     * 创建 HTTP 请求头
     *
     * @return HttpHeaders
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        return headers;
    }
}
