package com.aicode.review.controller;

import com.aicode.review.service.CodeReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook 接收控制器
 *
 * 接收来自 GitHub/GitLab 的 Webhook 事件，触发代码审查流程。
 *
 * @author AI Code Review Team
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @Autowired
    private CodeReviewService codeReviewService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 接收 GitHub Webhook
     *
     * @param payload Webhook 请求体
     * @param event   GitHub 事件类型
     * @param signature 签名（用于验证）
     * @return 响应
     */
    @PostMapping("/github")
    public ResponseEntity<?> handleGitHubWebhook(
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        log.info("收到 GitHub Webhook 事件: {}", event);

        try {
            // 只处理 PR 相关事件
            if ("pull_request".equals(event)) {
                JsonNode jsonNode = objectMapper.readTree(payload);
                String action = jsonNode.path("action").asText();

                // 只在 PR 打开、同步、重新打开时触发审查
                if ("opened".equals(action) || "synchronize".equals(action) || "reopened".equals(action)) {
                    log.info("触发 PR 代码审查, Action: {}", action);
                    codeReviewService.triggerReview(payload, "github");
                }
            }

            return ResponseEntity.ok().body("{\"status\":\"received\"}");
        } catch (Exception e) {
            log.error("处理 GitHub Webhook 失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 接收 GitLab Webhook
     *
     * @param payload Webhook 请求体
     * @param token   GitLab Secret Token
     * @return 响应
     */
    @PostMapping("/gitlab")
    public ResponseEntity<?> handleGitLabWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String event) {

        log.info("收到 GitLab Webhook 事件: {}", event);

        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            String objectKind = jsonNode.path("object_kind").asText();

            // 处理 Merge Request 事件
            if ("merge_request".equals(objectKind)) {
                String state = jsonNode.path("object_attributes").path("state").asText();
                String action = jsonNode.path("object_attributes").path("action").asText();

                log.info("MR 状态: {}, 动作: {}", state, action);

                // 在 MR 打开、更新时触发审查
                if ("opened".equals(action) || "updated".equals(action) || "reopened".equals(action)) {
                    log.info("触发 MR 代码审查");
                    codeReviewService.triggerReview(payload, "gitlab");
                }
            }

            return ResponseEntity.ok().body("{\"status\":\"received\"}");
        } catch (Exception e) {
            log.error("处理 GitLab Webhook 失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 健康检查接口
     *
     * @return 服务状态
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().body("{\"status\":\"UP\"}");
    }

    /**
     * 测试 GitLab 连接
     *
     * @return 连接状态
     */
    @GetMapping("/test-gitlab")
    public ResponseEntity<?> testGitLabConnection() {
        log.info("测试 GitLab 连接");
        try {
            String result = codeReviewService.testGitLabConnection();
            return ResponseEntity.ok().body("{\"status\":\"success\",\"result\":\"" + result + "\"}");
        } catch (Exception e) {
            log.error("GitLab 连接测试失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 手动审查 GitLab MR
     *
     * @param projectId GitLab 项目 ID 或路径 (如 "hccloudGroup/my-project")
     * @param mrIid     MR IID (Merge Request 编号)
     * @return 审查结果
     */
    @RequestMapping(value = "/review-gitlab-mr", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> reviewGitLabMR(
            @RequestParam String projectId,
            @RequestParam int mrIid) {

        log.info("手动审查 GitLab MR: {}/{}" , projectId, mrIid);

        try {
            String result = codeReviewService.reviewGitLabMR(projectId, mrIid);
            return ResponseEntity.ok().body("{\"status\":\"success\",\"result\":\"" + result + "\"}");
        } catch (Exception e) {
            log.error("审查 GitLab MR 失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 审查分支代码
     *
     * @param projectId GitLab 项目 ID 或路径 (如 "hccloudGroup/my-project")
     * @param branch    分支名 (默认 main)
     * @return 审查结果
     */
    @RequestMapping(value = "/review-branch", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> reviewBranch(
            @RequestParam String projectId,
            @RequestParam(defaultValue = "main") String branch) {

        log.info("审查分支代码: {}/{}" , projectId, branch);

        try {
            String result = codeReviewService.reviewBranch(projectId, branch);
            return ResponseEntity.ok().body("{\"status\":\"success\",\"result\":\"" + result + "\"}");
        } catch (Exception e) {
            log.error("审查分支代码失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 手动触发代码审查（用于测试）
     *
     * @param request 请求参数
     * @return 审查结果
     */
    @PostMapping("/manual-review")
    public ResponseEntity<?> manualReview(@RequestBody ManualReviewRequest request) {
        log.info("收到手动审查请求: {}", request);

        try {
            String result = codeReviewService.manualReview(request);
            return ResponseEntity.ok().body("{\"status\":\"success\",\"result\":\"" + result + "\"}");
        } catch (Exception e) {
            log.error("手动审查失败", e);
            return ResponseEntity.ok().body("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 手动审查请求参数
     */
    public static class ManualReviewRequest {
        private String code;
        private String context;
        private String repository;
        private String prId;

        // Getters and Setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getRepository() { return repository; }
        public void setRepository(String repository) { this.repository = repository; }
        public String getPrId() { return prId; }
        public void setPrId(String prId) { this.prId = prId; }

        @Override
        public String toString() {
            return "ManualReviewRequest{repository='" + repository + "', prId='" + prId + "'}";
        }
    }
}
