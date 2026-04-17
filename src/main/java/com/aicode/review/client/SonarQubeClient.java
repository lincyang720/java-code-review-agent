package com.aicode.review.client;

import com.aicode.review.config.StaticAnalysisConfig;
import com.aicode.review.model.Issue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * SonarQube API 客户端
 * 
 * 用于获取 SonarQube 代码分析结果
 */
@Slf4j
@Component
public class SonarQubeClient {
    
    private final StaticAnalysisConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public SonarQubeClient(StaticAnalysisConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 检查 SonarQube 是否启用
     */
    public boolean isEnabled() {
        return config.getSonarqube().isEnabled();
    }
    
    /**
     * 获取项目的代码质量问题
     * 
     * @param projectKey 项目 Key
     * @param branch 分支名
     * @return 问题列表
     */
    public List<Issue> getIssues(String projectKey, String branch) {
        List<Issue> issues = new ArrayList<>();
        
        if (!isEnabled()) {
            log.debug("SonarQube 未启用");
            return issues;
        }
        
        try {
            String url = String.format("%s/api/issues/search?projectKeys=%s&branch=%s&ps=500",
                    config.getSonarqube().getUrl(), projectKey, branch);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + config.getSonarqube().getToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);
            
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode issuesNode = root.path("issues");
            
            for (JsonNode issueNode : issuesNode) {
                Issue issue = Issue.builder()
                        .severity(mapSeverity(issueNode.path("severity").asText()))
                        .type(mapType(issueNode.path("type").asText()))
                        .file(issueNode.path("component").asText())
                        .line(issueNode.path("line").asInt())
                        .description(issueNode.path("message").asText())
                        .ruleId("SONAR_" + issueNode.path("rule").asText())
                        .build();
                issues.add(issue);
            }
            
            log.info("从 SonarQube 获取到 {} 个问题", issues.size());
            
        } catch (Exception e) {
            log.error("获取 SonarQube 分析结果失败", e);
        }
        
        return issues;
    }
    
    /**
     * 触发项目分析
     * 
     * @param projectKey 项目 Key
     */
    public void triggerAnalysis(String projectKey) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String url = String.format("%s/api/project_analyses/search?project=%s",
                    config.getSonarqube().getUrl(), projectKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + config.getSonarqube().getToken());
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            log.info("已触发 SonarQube 分析: {}", projectKey);
            
        } catch (Exception e) {
            log.error("触发 SonarQube 分析失败", e);
        }
    }
    
    private Issue.Severity mapSeverity(String sonarSeverity) {
        return switch (sonarSeverity.toUpperCase()) {
            case "BLOCKER", "CRITICAL" -> Issue.Severity.CRITICAL;
            case "MAJOR" -> Issue.Severity.WARNING;
            case "MINOR", "INFO" -> Issue.Severity.INFO;
            default -> Issue.Severity.WARNING;
        };
    }
    
    private Issue.Type mapType(String sonarType) {
        return switch (sonarType.toUpperCase()) {
            case "BUG" -> Issue.Type.BUG;
            case "VULNERABILITY" -> Issue.Type.SECURITY;
            case "CODE_SMELL" -> Issue.Type.QUALITY;
            default -> Issue.Type.QUALITY;
        };
    }
}
