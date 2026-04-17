package com.aicode.review.client;

import com.aicode.review.model.CommitMessage;
import com.aicode.review.model.PRDifferences;
import com.aicode.review.model.ReviewReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * GitLab API 客户端
 *
 * 用于与 GitLab 服务器交互，获取 MR 信息、代码差异、提交评论等。
 *
 * @author AI Code Review Team
 */
@Slf4j
@Component
public class GitLabClient {

    @Value("${app.gitlab.url:http://192.168.1.151}")
    private String gitlabUrl;

    @Value("${app.gitlab.token:${GITLAB_TOKEN:}}")
    private String gitlabToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GitLabClient() {
        this.restTemplate = new RestTemplate();
        // 配置 RestTemplate 不对 URL 进行编码，我们已经手动编码了
        DefaultUriBuilderFactory uriFactory = new DefaultUriBuilderFactory();
        uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
        this.restTemplate.setUriTemplateHandler(uriFactory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取 Merge Request 的详细信息
     *
     * @param projectId 项目ID
     * @param mrIid     MR IID
     * @return MR 详细信息
     */
    public JsonNode getMergeRequest(String projectId, int mrIid) {
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d",
                gitlabUrl, encodeProjectId(projectId), mrIid);

        log.debug("获取 MR 信息: {}", url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);

        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            log.error("解析 MR 信息失败", e);
            return null;
        }
    }

    /**
     * 获取 Merge Request 的代码差异
     *
     * @param projectId 项目ID
     * @param mrIid     MR IID
     * @return 代码差异列表
     */
    public List<PRDifferences.FileDiff> getMergeRequestDiffs(String projectId, int mrIid) {
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d/diffs",
                gitlabUrl, encodeProjectId(projectId), mrIid);

        log.debug("获取 MR 差异: {}", url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode diffs = objectMapper.readTree(response.getBody());
            List<PRDifferences.FileDiff> fileDiffs = new ArrayList<>();

            for (JsonNode diff : diffs) {
                PRDifferences.FileDiff fileDiff = PRDifferences.FileDiff.builder()
                        .filePath(diff.path("new_path").asText())
                        .status(diff.path("deleted_file").asBoolean(false) ? "removed" :
                                diff.path("new_file").asBoolean(false) ? "added" : "modified")
                        .additions(diff.path("additions").asInt(0))
                        .deletions(diff.path("deletions").asInt(0))
                        .patch(diff.path("diff").asText())
                        .build();
                fileDiffs.add(fileDiff);
            }

            return fileDiffs;
        } catch (Exception e) {
            log.error("获取 MR 差异失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取 MR 的提交记录
     *
     * @param projectId 项目ID
     * @param mrIid     MR IID
     * @return 提交信息列表
     */
    public List<CommitMessage> getMergeRequestCommits(String projectId, int mrIid) {
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d/commits",
                gitlabUrl, encodeProjectId(projectId), mrIid);

        log.debug("获取 MR 提交记录: {}", url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode commits = objectMapper.readTree(response.getBody());
            List<CommitMessage> commitMessages = new ArrayList<>();

            for (JsonNode commit : commits) {
                CommitMessage cm = CommitMessage.builder()
                        .sha(commit.path("id").asText())
                        .message(commit.path("message").asText())
                        .authorName(commit.path("author_name").asText())
                        .authorEmail(commit.path("author_email").asText())
                        .commitTime(parseGitLabTime(commit.path("committed_date").asText()))
                        .build();
                commitMessages.add(cm);
            }

            return commitMessages;
        } catch (Exception e) {
            log.error("获取 MR 提交记录失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 在 MR 下发表评论
     *
     * @param projectId    项目ID
     * @param mrIid        MR IID
     * @param report       审查报告
     * @return 是否成功
     */
    public boolean postReviewComment(String projectId, int mrIid, ReviewReport report) {
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d/notes",
                gitlabUrl, encodeProjectId(projectId), mrIid);

        log.info("发表审查评论到 MR #{}: {}", mrIid, url);

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 构建评论内容
        String comment = report.toMarkdown();

        // GitLab 评论长度限制
        if (comment.length() > 1000000) {
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
     * 在指定行发表评论（行内评论）
     *
     * @param projectId 项目ID
     * @param mrIid     MR IID
     * @param filePath  文件路径
     * @param line      行号
     * @param comment   评论内容
     * @return 是否成功
     */
    public boolean postLineComment(String projectId, int mrIid, String filePath,
                                    int line, String comment) {
        String url = String.format("%s/api/v4/projects/%s/merge_requests/%d/discussions",
                gitlabUrl, encodeProjectId(projectId), mrIid);

        log.debug("发表行内评论: {}:{}", filePath, line);

        HttpHeaders headers = createHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"body\":\"%s\",\"position\":{\"base_sha\":\"...\",\"head_sha\":\"...\",\"start_sha\":\"...\",\"position_type\":\"text\",\"new_path\":\"%s\",\"new_line\":%d}}",
                comment.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"),
                filePath,
                line
        );

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("发表行内评论失败", e);
            return false;
        }
    }

    /**
     * 获取分支的最新提交
     *
     * @param projectId 项目ID
     * @param branch    分支名
     * @return 提交信息
     */
    public JsonNode getBranchCommit(String projectId, String branch) {
        String encodedProjectId = encodeProjectId(projectId);
        String url = String.format("%s/api/v4/projects/%s/repository/commits?ref_name=%s&per_page=1",
                gitlabUrl, encodedProjectId, branch);

        log.info("获取分支最新提交: projectId={}, encoded={}, branch={}, url={}", projectId, encodedProjectId, branch, url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            JsonNode commits = objectMapper.readTree(response.getBody());
            if (commits.isArray() && commits.size() > 0) {
                return commits.get(0);
            }
            return null;
        } catch (Exception e) {
            log.error("获取分支提交失败: {}", e.getMessage());
            throw new RuntimeException("获取分支提交失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取仓库文件树
     *
     * @param projectId 项目ID
     * @param branch    分支名
     * @param path      路径（可选）
     * @return 文件列表
     */
    public List<String> getRepositoryTree(String projectId, String branch, String path) {
        List<String> files = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        boolean hasMore = true;

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        while (hasMore && page <= 100) { // 最多100页，防止无限循环
            String url = String.format("%s/api/v4/projects/%s/repository/tree?ref=%s&recursive=true&per_page=%d&page=%d",
                    gitlabUrl, encodeProjectId(projectId), branch, perPage, page);

            log.info("获取仓库文件树第{}页: {}", page, url);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                JsonNode tree = objectMapper.readTree(response.getBody());

                if (!tree.isArray() || tree.size() == 0) {
                    hasMore = false;
                    break;
                }

                log.info("文件树API第{}页返回 {} 个条目", page, tree.size());
                for (JsonNode node : tree) {
                    String type = node.path("type").asText();
                    String filePath = node.path("path").asText();
                    
                    // 记录所有目录，帮助调试
                    if ("tree".equals(type)) {
                        log.debug("发现目录: {}", filePath);
                    }
                    
                    if ("blob".equals(type) && filePath.endsWith(".java")) {
                        files.add(filePath);
                        if (filePath.startsWith("hccloud-iot-task/") || filePath.startsWith("iot-task/")) {
                            log.info("发现 iot-task 目录下的 Java 文件: {}", filePath);
                        }
                    }
                }

                // 如果返回的数量小于perPage，说明没有更多数据了
                if (tree.size() < perPage) {
                    hasMore = false;
                } else {
                    page++;
                }
            } catch (Exception e) {
                log.error("获取仓库文件树第{}页失败", page, e);
                break;
            }
        }

        log.info("总共获取 {} 个Java文件", files.size());
        return files;
    }

    /**
     * 获取文件内容
     *
     * @param projectId 项目ID
     * @param branch    分支名
     * @param filePath  文件路径
     * @return 文件内容
     */
    public String getFileContent(String projectId, String branch, String filePath) {
        // GitLab API 要求文件路径中的 / 编码为 %2F
        // 使用 UTF-8 编码整个路径，然后手动替换 %2F 保持编码状态
        String encodedProjectId = projectId.replace("/", "%2F");
        String encodedFilePath = filePath.replace("/", "%2F");
        
        String url = gitlabUrl + "/api/v4/projects/" + encodedProjectId 
                + "/repository/files/" + encodedFilePath + "/raw?ref=" + branch;

        log.info("获取文件内容: path={}, url={}", filePath, url);

        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            // 使用 String 类型 URL，配合自定义的 RestTemplate 配置
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("获取文件内容失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 测试 GitLab 连接
     *
     * @return 连接信息
     */
    public String testConnection() {
        String url = gitlabUrl + "/api/v4/version";

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            JsonNode version = objectMapper.readTree(response.getBody());
            return String.format("GitLab 版本: %s, 状态: 连接成功",
                    version.path("version").asText("unknown"));
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    /**
     * 创建 HTTP 请求头
     *
     * @return HttpHeaders
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (gitlabToken != null && !gitlabToken.isEmpty()) {
            headers.set("PRIVATE-TOKEN", gitlabToken);
        }
        return headers;
    }

    /**
     * 编码项目ID（处理路径中的特殊字符）
     *
     * @param projectId 项目ID
     * @return 编码后的项目ID
     */
    private String encodeProjectId(String projectId) {
        // 如果是纯数字项目ID，直接返回
        if (projectId.matches("\\d+")) {
            return projectId;
        }
        // 路径格式需要 URL 编码
        return projectId.replace("/", "%2F");
    }

    /**
     * 解析 GitLab 时间格式
     *
     * @param timeStr 时间字符串
     * @return LocalDateTime
     */
    private LocalDateTime parseGitLabTime(String timeStr) {
        try {
            return LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
